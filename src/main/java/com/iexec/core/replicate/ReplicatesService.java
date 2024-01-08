/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.core.replicate;

import com.iexec.common.replicate.*;
import com.iexec.common.utils.ContextualLockRunner;
import com.iexec.commons.poco.chain.ChainContribution;
import com.iexec.commons.poco.notification.TaskNotificationType;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.logs.TaskLogsService;
import com.iexec.core.result.ResultService;
import com.iexec.core.workflow.ReplicateWorkflow;
import io.vavr.control.Either;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusCause.REVEAL_TIMEOUT;

@Slf4j
@Service
public class ReplicatesService {

    private final ReplicatesRepository replicatesRepository;
    private final IexecHubService iexecHubService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Web3jService web3jService;
    private final ResultService resultService;
    private final TaskLogsService taskLogsService;

    private final ContextualLockRunner<String> replicatesUpdateLockRunner =
            new ContextualLockRunner<>(10, TimeUnit.MINUTES);

    public ReplicatesService(ReplicatesRepository replicatesRepository,
                             IexecHubService iexecHubService,
                             ApplicationEventPublisher applicationEventPublisher,
                             Web3jService web3jService,
                             ResultService resultService,
                             TaskLogsService taskLogsService) {
        this.replicatesRepository = replicatesRepository;
        this.iexecHubService = iexecHubService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.web3jService = web3jService;
        this.resultService = resultService;
        this.taskLogsService = taskLogsService;
    }

    public boolean addNewReplicate(ReplicatesList replicatesList, String walletAddress) {
        final String chainTaskId = replicatesList.getChainTaskId();
        if (replicatesList.getReplicateOfWorker(walletAddress).isEmpty()) {
            Replicate replicate = new Replicate(walletAddress, chainTaskId);
            replicate.setWorkerWeight(iexecHubService.getWorkerWeight(walletAddress));// workerWeight value for pendingWeight estimate
            replicatesList.getReplicates().add(replicate);

            replicatesRepository.save(replicatesList);
            log.info("New replicate saved [chainTaskId:{}, walletAddress:{}]", chainTaskId, walletAddress);
        } else {
            log.error("Replicate already saved [chainTaskId:{}, walletAddress:{}]", chainTaskId, walletAddress);
            return false;
        }

        return true;
    }

    public synchronized void createEmptyReplicateList(String chainTaskId) {
        replicatesRepository.save(new ReplicatesList(chainTaskId));
    }

    public boolean hasReplicatesList(String chainTaskId) {
        return replicatesRepository.countByChainTaskId(chainTaskId) > 0;
    }

    public Optional<ReplicatesList> getReplicatesList(String chainTaskId) {
        return replicatesRepository.findByChainTaskId(chainTaskId);
    }

    public List<Replicate> getReplicates(String chainTaskId) {
        Optional<ReplicatesList> optionalList = getReplicatesList(chainTaskId);
        if (optionalList.isEmpty()) {
            return Collections.emptyList();
        }
        return optionalList.get().getReplicates();
    }

    public Optional<Replicate> getReplicate(String chainTaskId, String walletAddress) {
        Optional<ReplicatesList> optional = getReplicatesList(chainTaskId);
        if (optional.isEmpty()) {
            return Optional.empty();
        }

        for (Replicate replicate : optional.get().getReplicates()) {
            if (replicate.getWalletAddress().equals(walletAddress)) {
                return Optional.of(replicate);
            }
        }

        return Optional.empty();
    }

    public int getNbReplicatesWithCurrentStatus(String chainTaskId, ReplicateStatus... listStatus) {
        return getReplicatesList(chainTaskId)
                .map(replicatesList -> replicatesList.getNbReplicatesWithCurrentStatus(listStatus))
                .orElse(0);
    }

    public int getNbReplicatesWithLastRelevantStatus(String chainTaskId, ReplicateStatus... listStatus) {
        return getReplicatesList(chainTaskId)
                .map(replicatesList -> replicatesList.getNbReplicatesWithLastRelevantStatus(listStatus))
                .orElse(0);
    }

    public int getNbReplicatesContainingStatus(String chainTaskId, ReplicateStatus... listStatus) {
        return getReplicatesList(chainTaskId)
                .map(replicatesList -> replicatesList.getNbReplicatesContainingStatus(listStatus))
                .orElse(0);
    }

    public Optional<Replicate> getRandomReplicateWithRevealStatus(String chainTaskId) {
        return getReplicatesList(chainTaskId)
                .flatMap(ReplicatesList::getRandomReplicateWithRevealStatus);
    }

    public Optional<Replicate> getReplicateWithResultUploadedStatus(String chainTaskId) {
        return getReplicatesList(chainTaskId)
                .flatMap(ReplicatesList::getReplicateWithResultUploadedStatus);
    }

    /**
     * Checks whether a replicate can be updated with given status.
     * Reason why a replicate can't be updated could be one of the following:
     * <ul>
     *     <li>The replicate is unknown. It could happen if there has been a confusion is the chains.</li>
     *     <li>The status has already been reported.
     *          It could happen if an update has already been detected before the worker notify the scheduler.</li>
     *     <li>The new status could not be reached from the old status.</li>
     *     <li>Some step failed.</li>
     * </ul>
     *
     * @return {@link ReplicateStatusUpdateError#NO_ERROR} if this update is OK,
     * another {@link ReplicateStatusUpdateError} containing the error reason otherwise.
     */
    public ReplicateStatusUpdateError canUpdateReplicateStatus(Replicate replicate,
                                                               ReplicateStatusUpdate statusUpdate,
                                                               UpdateReplicateStatusArgs updateReplicateStatusArgs) {
        final String chainTaskId = replicate.getChainTaskId();
        final String walletAddress = replicate.getWalletAddress();
        final ReplicateStatus newStatus = statusUpdate.getStatus();

        boolean hasAlreadyTransitionedToStatus = replicate.containsStatus(newStatus);
        if (hasAlreadyTransitionedToStatus) {
            log.warn("Cannot update replicate, status already reported {}",
                    getStatusUpdateLogs(chainTaskId, replicate, statusUpdate));
            return ReplicateStatusUpdateError.ALREADY_REPORTED;
        }

        boolean isValidTransition = ReplicateWorkflow.getInstance()
                .isValidTransition(replicate.getCurrentStatus(), newStatus);
        if (!isValidTransition) {
            log.warn("Cannot update replicate, bad workflow transition {}",
                    getStatusUpdateLogs(chainTaskId, replicate, statusUpdate));
            return ReplicateStatusUpdateError.BAD_WORKFLOW_TRANSITION;
        }

        if (newStatus == COMPUTED && updateReplicateStatusArgs.getTaskDescription() == null) {
            log.warn("TaskDescription is null with a COMPUTED status, this case shouldn't happen {}",
                    getStatusUpdateLogs(chainTaskId, replicate, statusUpdate));
            return ReplicateStatusUpdateError.UNKNOWN_TASK;
        }

        boolean canUpdate = true;

        switch (newStatus) {
            case CONTRIBUTE_FAILED:
            case REVEAL_FAILED:
                canUpdate = false;
                break;
            case CONTRIBUTE_AND_FINALIZE_DONE:
            case RESULT_UPLOAD_FAILED:
                canUpdate = verifyStatus(chainTaskId, walletAddress, newStatus, updateReplicateStatusArgs);
                break;
            case CONTRIBUTED:
            case REVEALED:
                canUpdate = canUpdateToBlockchainSuccess(chainTaskId, replicate, statusUpdate, updateReplicateStatusArgs);
                break;
            case RESULT_UPLOADED:
                canUpdate = canUpdateToUploadSuccess(chainTaskId, replicate, statusUpdate, updateReplicateStatusArgs);
                break;
            default:
                break;
        }

        if (!canUpdate) {
            log.warn("Cannot update replicate {}",
                    getStatusUpdateLogs(chainTaskId, replicate, statusUpdate));
            return ReplicateStatusUpdateError.GENERIC_CANT_UPDATE;
        }

        return ReplicateStatusUpdateError.NO_ERROR;
    }

    /**
     * Computes arguments used by {@link ReplicatesService#updateReplicateStatus(String, String, ReplicateStatusUpdate, UpdateReplicateStatusArgs)}.
     * These arguments can be null or empty, no check is done there.
     */
    public UpdateReplicateStatusArgs computeUpdateReplicateStatusArgs(String chainTaskId,
                                                                      String walletAddress,
                                                                      ReplicateStatusUpdate statusUpdate) {
        int workerWeight = 0;
        ChainContribution chainContribution = null;
        String resultLink = null;
        String chainCallbackData = null;
        final TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);

        if (statusUpdate.getStatus() == CONTRIBUTED || statusUpdate.getStatus() == CONTRIBUTE_AND_FINALIZE_DONE) {
            workerWeight = iexecHubService.getWorkerWeight(walletAddress);
            chainContribution = iexecHubService.getChainContribution(chainTaskId, walletAddress).orElse(null);
        }

        if (statusUpdate.getStatus() == RESULT_UPLOADED || statusUpdate.getStatus() == CONTRIBUTE_AND_FINALIZE_DONE) {
            final ReplicateStatusDetails details = statusUpdate.getDetails();
            if (details != null) {
                resultLink = details.getResultLink();
                chainCallbackData = details.getChainCallbackData();
            }
        }

        return UpdateReplicateStatusArgs.builder()
                .workerWeight(workerWeight)
                .chainContribution(chainContribution)
                .resultLink(resultLink)
                .chainCallbackData(chainCallbackData)
                .taskDescription(taskDescription)
                .build();
    }

    /*
     * This implicitly sets the modifier to POOL_MANAGER
     *
     * @Retryable is needed as it isn't triggered by a call from within the class itself.
     */
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 100)
    public void updateReplicateStatus(String chainTaskId,
                                      String walletAddress,
                                      ReplicateStatus newStatus) {
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.poolManagerRequest(newStatus);
        updateReplicateStatus(chainTaskId, walletAddress, statusUpdate);
    }

    @Recover
    public void updateReplicateStatus(OptimisticLockingFailureException exception,
                                      String chainTaskId,
                                      String walletAddress,
                                      ReplicateStatus newStatus) {
        logUpdateReplicateStatusRecover(exception);
    }

    /*
     * This implicitly sets the modifier to POOL_MANAGER
     *
     * @Retryable is needed as it isn't triggered by a call from within the class itself.
     */
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 100)
    public void updateReplicateStatus(String chainTaskId,
                                      String walletAddress,
                                      ReplicateStatus newStatus,
                                      ReplicateStatusDetails details) {
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.poolManagerRequest(newStatus, details);
        updateReplicateStatus(chainTaskId, walletAddress, statusUpdate);
    }

    @Recover
    public void updateReplicateStatus(OptimisticLockingFailureException exception,
                                      String chainTaskId,
                                      String walletAddress,
                                      ReplicateStatus newStatus,
                                      ReplicateStatusDetails details) {
        logUpdateReplicateStatusRecover(exception);
    }

    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 100)
    public Either<ReplicateStatusUpdateError, TaskNotificationType> updateReplicateStatus(
            String chainTaskId,
            String walletAddress,
            ReplicateStatusUpdate statusUpdate) {
        final UpdateReplicateStatusArgs updateReplicateStatusArgs = computeUpdateReplicateStatusArgs(
                chainTaskId,
                walletAddress,
                statusUpdate);

        return updateReplicateStatus(
                chainTaskId,
                walletAddress,
                statusUpdate,
                updateReplicateStatusArgs);
    }

    @Recover
    public Either<ReplicateStatusUpdateError, TaskNotificationType> updateReplicateStatus(
            OptimisticLockingFailureException exception,
            String chainTaskId,
            String walletAddress,
            ReplicateStatusUpdate statusUpdate) {
        logUpdateReplicateStatusRecover(exception);
        return null;
    }

    /*
     * We retry up to 100 times in case the task has been modified between
     * reading and writing it.
     *
     * Before updating we check:
     *   1) if valid transition.
     *   2) if worker did fail when CONTRIBUTE/REVEAL/UPLOAD_FAILED.
     *   3) if worker did succeed onChain when CONTRIBUTED/REVEALED.
     *   4) if worker did upload when RESULT_UPLOADING.
     */

    /**
     * This method updates a replicate while caring about thread safety.
     * A single replicate can then NOT be updated twice at the same time.
     * This method should be preferred to
     * {@link ReplicatesService#updateReplicateStatusWithoutThreadSafety(String, String, ReplicateStatusUpdate, UpdateReplicateStatusArgs)}!
     *
     * @param chainTaskId               Chain task id of the task whose replicate should be updated.
     * @param walletAddress             Wallet address of the worker whose replicate should be updated.
     * @param statusUpdate              Info about the status update - new status, date of update, ...
     * @param updateReplicateStatusArgs Optional args used to update the status.
     * @return Either a {@link ReplicateStatusUpdateError} if the status can't be updated,
     * or a next action for the worker.
     */
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 100)
    public Either<ReplicateStatusUpdateError, TaskNotificationType> updateReplicateStatus(
            String chainTaskId,
            String walletAddress,
            ReplicateStatusUpdate statusUpdate,
            UpdateReplicateStatusArgs updateReplicateStatusArgs) {
        // Synchronization is mandatory there to avoid race conditions.
        // Lock key should be unique, e.g. `chainTaskId + walletAddress`.
        final String lockKey = chainTaskId + walletAddress;
        return replicatesUpdateLockRunner.getWithLock(
                lockKey,
                () -> updateReplicateStatusWithoutThreadSafety(chainTaskId, walletAddress, statusUpdate, updateReplicateStatusArgs)
        );
    }

    @Recover
    public Either<ReplicateStatusUpdateError, TaskNotificationType> updateReplicateStatus(
            OptimisticLockingFailureException exception,
            String chainTaskId,
            String walletAddress,
            ReplicateStatusUpdate statusUpdate,
            UpdateReplicateStatusArgs updateReplicateStatusArgs) {
        logUpdateReplicateStatusRecover(exception);
        return null;
    }

    /**
     * This method updates a replicate but does not care about thread safety.
     * A single replicate can then be updated twice at the same time
     * and completely break a task.
     * This method has to be used with a synchronization mechanism, e.g.
     * {@link ReplicatesService#updateReplicateStatus(String, String, ReplicateStatus, ReplicateStatusDetails)}
     *
     * @param chainTaskId               Chain task id of the task whose replicate should be updated.
     * @param walletAddress             Wallet address of the worker whose replicate should be updated.
     * @param statusUpdate              Info about the status update - new status, date of update, ...
     * @param updateReplicateStatusArgs Optional args used to update the status.
     * @return Either a {@link ReplicateStatusUpdateError} if the status can't be updated,
     * or a next action for the worker.
     */
    Either<ReplicateStatusUpdateError, TaskNotificationType> updateReplicateStatusWithoutThreadSafety(
            String chainTaskId,
            String walletAddress,
            ReplicateStatusUpdate statusUpdate,
            UpdateReplicateStatusArgs updateReplicateStatusArgs) {
        log.info("Replicate update request [status:{}, chainTaskId:{}, walletAddress:{}, details:{}]",
                statusUpdate.getStatus(), chainTaskId, walletAddress, statusUpdate.getDetailsWithoutLogs());

        final Optional<ReplicatesList> oReplicatesList = getReplicatesList(chainTaskId);
        final Optional<Replicate> oReplicate = oReplicatesList
                .flatMap(replicatesList -> replicatesList.getReplicateOfWorker(walletAddress));
        if (oReplicatesList.isEmpty() || oReplicate.isEmpty()) {
            log.error("Cannot update replicate, could not get replicate [chainTaskId:{}, UpdateRequest:{}]",
                    chainTaskId, statusUpdate);
            return Either.left(ReplicateStatusUpdateError.UNKNOWN_REPLICATE);
        }
        final ReplicatesList replicatesList = oReplicatesList.get();
        final Replicate replicate = oReplicate.get();
        final ReplicateStatus newStatus = statusUpdate.getStatus();

        final ReplicateStatusUpdateError error = canUpdateReplicateStatus(replicate, statusUpdate, updateReplicateStatusArgs);
        if (ReplicateStatusUpdateError.NO_ERROR != error) {
            return Either.left(error);
        }

        if (newStatus == CONTRIBUTED || newStatus == CONTRIBUTE_AND_FINALIZE_DONE) {
            replicate.setContributionHash(updateReplicateStatusArgs.getChainContribution().getResultHash());
            replicate.setWorkerWeight(updateReplicateStatusArgs.getWorkerWeight());
        }

        if (newStatus == RESULT_UPLOADED || newStatus == CONTRIBUTE_AND_FINALIZE_DONE) {
            replicate.setResultLink(updateReplicateStatusArgs.getResultLink());
            replicate.setChainCallbackData(updateReplicateStatusArgs.getChainCallbackData());
        }

        if (statusUpdate.getDetails() != null &&
                (newStatus == COMPUTED || (newStatus == COMPUTE_FAILED
                        && ReplicateStatusCause.APP_COMPUTE_FAILED == statusUpdate.getDetails().getCause()))) {
            final ComputeLogs computeLogs = statusUpdate.getDetails().tailLogs().getComputeLogs();
            taskLogsService.addComputeLogs(chainTaskId, computeLogs);
            statusUpdate.getDetails().setComputeLogs(null);//using null here to keep light replicate
            replicate.setAppComputeLogsPresent(true);
        }

        replicate.updateStatus(statusUpdate);
        replicatesRepository.save(replicatesList);
        applicationEventPublisher.publishEvent(new ReplicateUpdatedEvent(chainTaskId, walletAddress, statusUpdate));
        ReplicateStatusCause newStatusCause = statusUpdate.getDetails() != null ?
                statusUpdate.getDetails().getCause() : null;
        TaskNotificationType nextAction = ReplicateWorkflow.getInstance().getNextAction(newStatus, newStatusCause, updateReplicateStatusArgs.getTaskDescription());

        log.info("Replicate updated successfully [newStatus:{}, newStatusCause:{} " +
                        "nextAction:{}, chainTaskId:{}, walletAddress:{}]",
                replicate.getCurrentStatus(), newStatusCause, nextAction, chainTaskId, walletAddress);

        return Either.right(nextAction);
    }

    private void logUpdateReplicateStatusRecover(OptimisticLockingFailureException exception) {
        log.error("Could not update replicate status, maximum number of retries reached", exception);
    }

    private boolean canUpdateToBlockchainSuccess(String chainTaskId,
                                                 Replicate replicate,
                                                 ReplicateStatusUpdate statusUpdate,
                                                 UpdateReplicateStatusArgs updateReplicateStatusArgs) {
        ReplicateStatus newStatus = statusUpdate.getStatus();
        ReplicateStatusDetails details = statusUpdate.getDetails();
        long receiptBlockNumber = details != null && details.getChainReceipt() != null
                ? details.getChainReceipt().getBlockNumber() : 0;

        boolean isBlockAvailable = web3jService.isBlockAvailable(receiptBlockNumber);
        if (!isBlockAvailable) {
            log.error("Cannot update replicate, block not available {}",
                    getStatusUpdateLogs(chainTaskId, replicate, statusUpdate));
            return false;
        }

        if (!verifyStatus(chainTaskId, replicate.getWalletAddress(), newStatus, updateReplicateStatusArgs)) {
            log.error("Cannot update replicate, status is not correct {}",
                    getStatusUpdateLogs(chainTaskId, replicate, statusUpdate));
            return false;
        }

        if (newStatus.equals(CONTRIBUTED)
                && (!validateWorkerWeight(chainTaskId, replicate, updateReplicateStatusArgs.getWorkerWeight())
                || !validateChainContribution(chainTaskId, replicate, updateReplicateStatusArgs.getChainContribution()))) {
            log.error("Cannot update replicate, worker weight not updated {}",
                    getStatusUpdateLogs(chainTaskId, replicate, statusUpdate));
            return false;
        }

        return true;
    }

    private boolean canUpdateToUploadSuccess(String chainTaskId,
                                             Replicate replicate,
                                             ReplicateStatusUpdate statusUpdate,
                                             UpdateReplicateStatusArgs updateReplicateStatusArgs) {
        ReplicateStatus newStatus = statusUpdate.getStatus();

        if (!verifyStatus(chainTaskId, replicate.getWalletAddress(), newStatus, updateReplicateStatusArgs)) {
            log.error("Cannot update replicate, status is not correct {}",
                    getStatusUpdateLogs(chainTaskId, replicate, statusUpdate));
            return false;
        }

        if (updateReplicateStatusArgs.getTaskDescription() == null) {
            log.error("Cannot update replicate, missing task description {}",
                    getStatusUpdateLogs(chainTaskId, replicate, statusUpdate));
            return false;
        }

        if (updateReplicateStatusArgs.getTaskDescription().containsCallback()) {
            if (!StringUtils.hasLength(updateReplicateStatusArgs.getChainCallbackData())) {
                log.error("Cannot update replicate, missing chainCallbackData {}",
                        getStatusUpdateLogs(chainTaskId, replicate, statusUpdate));
                return false;
            }
        } else {
            if (!StringUtils.hasLength(updateReplicateStatusArgs.getResultLink())) {
                log.error("Cannot update replicate, missing resultLink {}",
                        getStatusUpdateLogs(chainTaskId, replicate, statusUpdate));
                return false;
            }
        }

        return true;
    }

    private boolean verifyStatus(String chainTaskId,
                                 String walletAddress,
                                 ReplicateStatus status,
                                 UpdateReplicateStatusArgs updateReplicateStatusArgs) {
        switch (status) {
            case CONTRIBUTED:
                return iexecHubService.repeatIsContributedTrue(chainTaskId, walletAddress);
            case REVEALED:
                return iexecHubService.repeatIsRevealedTrue(chainTaskId, walletAddress);
            case RESULT_UPLOADED:
                return isResultUploaded(updateReplicateStatusArgs.getTaskDescription());
            case RESULT_UPLOAD_FAILED:
                return !isResultUploaded(updateReplicateStatusArgs.getTaskDescription());
            case CONTRIBUTE_AND_FINALIZE_DONE:
                return iexecHubService.repeatIsRevealedTrue(chainTaskId, walletAddress)
                        && isResultUploaded(chainTaskId)
                        && iexecHubService.isTaskInCompletedStatusOnChain(chainTaskId);
            default:
                return true;
        }
    }

    private boolean validateWorkerWeight(String chainTaskId,
                                         Replicate replicate,
                                         int workerWeight) {
        if (workerWeight == 0) {
            String walletAddress = replicate.getWalletAddress();
            log.error("Failed to get worker weight [chainTaskId:{}, workerWallet:{}]",
                    chainTaskId, walletAddress);
            return false;
        }
        return true;
    }

    private boolean validateChainContribution(String chainTaskId,
                                              Replicate replicate,
                                              ChainContribution chainContribution) {
        if (chainContribution == null) {
            String walletAddress = replicate.getWalletAddress();
            log.error("Failed to get chain contribution [chainTaskId:{}, workerWallet:{}]",
                    chainTaskId, walletAddress);
            return false;
        }

        if (!StringUtils.hasLength(chainContribution.getResultHash())) {
            String walletAddress = replicate.getWalletAddress();
            log.error("Failed to get chain contribution result hash [chainTaskId:{}, workerWallet:{}]",
                    chainTaskId, walletAddress);
            return false;
        }

        return true;
    }

    private String getStatusUpdateLogs(String chainTaskId, Replicate replicate, ReplicateStatusUpdate statusUpdate) {
        return String.format("[currentStatus:%s, newStatus:%s chainTaskId:%s, walletAddress:%s, details:%s]",
                replicate.getCurrentStatus(), statusUpdate.getStatus(), chainTaskId, replicate.getWalletAddress(),
                statusUpdate.getDetailsWithoutLogs());
    }

    public boolean isResultUploaded(String chainTaskId) {
        TaskDescription task = iexecHubService.getTaskDescription(chainTaskId);

        if (task == null) {
            return false;
        }

        return isResultUploaded(task);
    }

    public boolean isResultUploaded(TaskDescription task) {
        // Offchain computing - basic & tee
        if (task.containsCallback()) {
            return true;
        }

        // Cloud computing - tee
        if (task.isTeeTask()) {
            return true; // pushed from enclave
        }

        // Cloud computing - basic
        return resultService.isResultUploaded(task.getChainTaskId());
    }

    public boolean didReplicateContributeOnchain(String chainTaskId, String walletAddress) {
        return iexecHubService.isContributed(chainTaskId, walletAddress);
    }

    public boolean didReplicateRevealOnchain(String chainTaskId, String walletAddress) {
        return iexecHubService.isRevealed(chainTaskId, walletAddress);
    }

    public void setRevealTimeoutStatusIfNeeded(String chainTaskId, Replicate replicate) {
        ReplicateStatus status = replicate.getLastRelevantStatus();
        if (status == REVEALING || status == CONTRIBUTED) {
            ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.poolManagerRequest(FAILED, REVEAL_TIMEOUT);
            updateReplicateStatus(chainTaskId, replicate.getWalletAddress(), statusUpdate);
        }
    }
}
