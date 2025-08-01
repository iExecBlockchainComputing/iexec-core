/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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
import com.iexec.commons.poco.chain.ChainTask;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.logs.TaskLogsService;
import com.iexec.core.notification.TaskNotificationType;
import com.iexec.core.result.ResultService;
import com.iexec.core.workflow.ReplicateWorkflow;
import io.vavr.control.Either;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.web3j.utils.Numeric;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusCause.REVEAL_TIMEOUT;
import static com.iexec.commons.poco.chain.DealParams.IPFS_RESULT_STORAGE_PROVIDER;

@Slf4j
@Service
public class ReplicatesService {

    private final MongoTemplate mongoTemplate;
    private final ReplicatesRepository replicatesRepository;
    private final IexecHubService iexecHubService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Web3jService web3jService;
    private final ResultService resultService;
    private final TaskLogsService taskLogsService;

    private final ContextualLockRunner<String> replicatesUpdateLockRunner =
            new ContextualLockRunner<>(10, TimeUnit.MINUTES);

    public ReplicatesService(MongoTemplate mongoTemplate,
                             ReplicatesRepository replicatesRepository,
                             IexecHubService iexecHubService,
                             ApplicationEventPublisher applicationEventPublisher,
                             Web3jService web3jService,
                             ResultService resultService,
                             TaskLogsService taskLogsService) {
        this.mongoTemplate = mongoTemplate;
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
        return getReplicatesList(chainTaskId)
                .map(ReplicatesList::getReplicates)
                .orElse(Collections.emptyList());
    }

    public Optional<Replicate> getReplicate(String chainTaskId, String walletAddress) {
        return getReplicates(chainTaskId)
                .stream()
                .filter(replicate -> replicate.getWalletAddress().equals(walletAddress))
                .findFirst();
    }

    public int getNbReplicatesWithCurrentStatus(String chainTaskId, ReplicateStatus... listStatus) {
        return getReplicatesList(chainTaskId)
                .map(replicatesList -> replicatesList.getNbReplicatesWithCurrentStatus(listStatus))
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
    public ReplicateStatusUpdateError canUpdateReplicateStatus(final Replicate replicate,
                                                               final ReplicateStatusUpdate statusUpdate,
                                                               final UpdateReplicateStatusArgs updateReplicateStatusArgs) {
        final String chainTaskId = replicate.getChainTaskId();
        final String walletAddress = replicate.getWalletAddress();
        final ReplicateStatus newStatus = statusUpdate.getStatus();

        final boolean hasAlreadyTransitionedToStatus = replicate.containsStatus(newStatus);
        if (hasAlreadyTransitionedToStatus) {
            log.warn("Cannot update replicate, status already reported {}",
                    getStatusUpdateLogs(chainTaskId, replicate, statusUpdate));
            return ReplicateStatusUpdateError.ALREADY_REPORTED;
        }

        final boolean isValidTransition = ReplicateWorkflow.getInstance()
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

        final boolean canUpdate = switch (newStatus) {
            case CONTRIBUTE_FAILED, REVEAL_FAILED -> false;
            case CONTRIBUTE_AND_FINALIZE_DONE, RESULT_UPLOAD_FAILED ->
                    verifyStatus(chainTaskId, walletAddress, newStatus, updateReplicateStatusArgs);
            case CONTRIBUTED, REVEALED ->
                    canUpdateToBlockchainSuccess(chainTaskId, replicate, statusUpdate, updateReplicateStatusArgs);
            case RESULT_UPLOADED ->
                    canUpdateToUploadSuccess(chainTaskId, replicate, statusUpdate, updateReplicateStatusArgs);
            default -> true;
        };

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

        if (statusUpdate.getStatus() == CONTRIBUTE_AND_FINALIZE_DONE) {
            // TODO read chainCallbackData if CONTRIBUTE_AND_FINALIZE becomes applicable some day in the future and if latest ABI is used
            resultLink = iexecHubService.getChainTask(chainTaskId)
                    .map(ChainTask::getResults)
                    .map(Numeric::hexStringToByteArray)
                    .map(String::new)
                    .orElse(null);
        }

        return UpdateReplicateStatusArgs.builder()
                .workerWeight(workerWeight)
                .chainContribution(chainContribution)
                .resultLink(resultLink)
                .chainCallbackData(chainCallbackData)
                .taskDescription(taskDescription)
                .build();
    }

    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 100)
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
        final String details = String.format("[chainTaskId:%s, walletAddress:%s, statusUpdate:%s]",
                chainTaskId, walletAddress, statusUpdate);
        log.error("Could not update replicate status, maximum number of retries reached {}", details, exception);
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
    Either<ReplicateStatusUpdateError, TaskNotificationType> updateReplicateStatus(
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

    /**
     * This method updates a replicate but does not care about thread safety.
     * A single replicate can then be updated twice at the same time
     * and completely break a task.
     * This method has to be used with a synchronization mechanism, e.g.
     * {@link ReplicatesService#updateReplicateStatus(String, String, ReplicateStatusUpdate, UpdateReplicateStatusArgs)}
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

        final Replicate replicate = getReplicatesList(chainTaskId)
                .flatMap(replicatesList -> replicatesList.getReplicateOfWorker(walletAddress))
                .orElse(null);
        if (replicate == null) {
            log.error("Cannot update replicate, could not get replicate [chainTaskId:{}, UpdateRequest:{}]",
                    chainTaskId, statusUpdate);
            return Either.left(ReplicateStatusUpdateError.UNKNOWN_REPLICATE);
        }
        final ReplicateStatus newStatus = statusUpdate.getStatus();

        final ReplicateStatusUpdateError error = canUpdateReplicateStatus(replicate, statusUpdate, updateReplicateStatusArgs);
        if (ReplicateStatusUpdateError.NO_ERROR != error) {
            return Either.left(error);
        }

        Update update = new Update();
        if (newStatus == CONTRIBUTED || newStatus == CONTRIBUTE_AND_FINALIZE_DONE) {
            replicate.setContributionHash(updateReplicateStatusArgs.getChainContribution().getResultHash());
            replicate.setWorkerWeight(updateReplicateStatusArgs.getWorkerWeight());
            update.set("replicates.$.contributionHash", updateReplicateStatusArgs.getChainContribution().getResultHash());
            update.set("replicates.$.workerWeight", updateReplicateStatusArgs.getWorkerWeight());
        }

        if (newStatus == RESULT_UPLOADED || newStatus == CONTRIBUTE_AND_FINALIZE_DONE) {
            replicate.setResultLink(updateReplicateStatusArgs.getResultLink());
            replicate.setChainCallbackData(updateReplicateStatusArgs.getChainCallbackData());
            update.set("replicates.$.resultLink", updateReplicateStatusArgs.getResultLink());
            update.set("replicates.$.chainCallbackData", updateReplicateStatusArgs.getChainCallbackData());
        }

        if (statusUpdate.getDetails() != null &&
                (newStatus == COMPUTED || (newStatus == COMPUTE_FAILED
                        && ReplicateStatusCause.APP_COMPUTE_FAILED == statusUpdate.getDetails().getCause()))) {
            final ComputeLogs computeLogs = statusUpdate.getDetails().tailLogs().getComputeLogs();
            taskLogsService.addComputeLogs(chainTaskId, computeLogs);
            statusUpdate.getDetails().setComputeLogs(null);//using null here to keep light replicate
            replicate.setAppComputeLogsPresent(true);
            update.set("replicates.$.appComputeLogsPresent", true);
        }

        update.push("replicates.$.statusUpdateList", statusUpdate);
        Query query = Query.query(Criteria.where("chainTaskId").is(chainTaskId).and("replicates.walletAddress").is(walletAddress));
        mongoTemplate.updateFirst(query, update, ReplicatesList.class);
        replicate.updateStatus(statusUpdate);
        applicationEventPublisher.publishEvent(new ReplicateUpdatedEvent(chainTaskId, walletAddress, statusUpdate));
        ReplicateStatusCause newStatusCause = statusUpdate.getDetails() != null ?
                statusUpdate.getDetails().getCause() : null;
        TaskNotificationType nextAction = ReplicateWorkflow.getInstance().getNextAction(newStatus, newStatusCause, updateReplicateStatusArgs.getTaskDescription());

        log.info("Replicate updated successfully [newStatus:{}, newStatusCause:{} " +
                        "nextAction:{}, chainTaskId:{}, walletAddress:{}]",
                replicate.getCurrentStatus(), newStatusCause, nextAction, chainTaskId, walletAddress);

        return Either.right(nextAction);
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
        return switch (status) {
            case CONTRIBUTED -> iexecHubService.repeatIsContributedTrue(chainTaskId, walletAddress);
            case REVEALED -> iexecHubService.repeatIsRevealedTrue(chainTaskId, walletAddress);
            case RESULT_UPLOADED -> isResultUploaded(updateReplicateStatusArgs.getTaskDescription());
            case RESULT_UPLOAD_FAILED -> !isResultUploaded(updateReplicateStatusArgs.getTaskDescription());
            case CONTRIBUTE_AND_FINALIZE_DONE -> iexecHubService.repeatIsRevealedTrue(chainTaskId, walletAddress)
                    && isResultUploaded(chainTaskId)
                    && iexecHubService.isTaskInCompletedStatusOnChain(chainTaskId);
            default -> true;
        };
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

    public boolean isResultUploaded(final TaskDescription task) {
        final boolean hasIpfsStorageProvider = IPFS_RESULT_STORAGE_PROVIDER.equals(task.getDealParams().getIexecResultStorageProvider());

        // Offchain computing or TEE task with private storage
        if (task.containsCallback() || (task.isTeeTask() && !hasIpfsStorageProvider)) {
            return true;
        }

        // Cloud computing, upload to IPFS - basic & TEE
        return resultService.isResultUploaded(task);
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
