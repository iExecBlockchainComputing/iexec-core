/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

import static com.iexec.common.replicate.ReplicateStatus.COMPUTED;
import static com.iexec.common.replicate.ReplicateStatus.CONTRIBUTED;
import static com.iexec.common.replicate.ReplicateStatus.FAILED;
import static com.iexec.common.replicate.ReplicateStatus.RESULT_UPLOADED;
import static com.iexec.common.replicate.ReplicateStatus.REVEALED;
import static com.iexec.common.replicate.ReplicateStatus.REVEALING;
import static com.iexec.common.replicate.ReplicateStatus.WORKER_LOST;
import static com.iexec.common.replicate.ReplicateStatus.getChainStatus;
import static com.iexec.common.replicate.ReplicateStatusCause.REVEAL_TIMEOUT;

import java.util.*;
import java.util.stream.Collectors;

import com.iexec.common.chain.ChainContribution;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.common.task.TaskDescription;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.result.ResultService;
import com.iexec.core.stdout.StdoutService;
import com.iexec.core.workflow.ReplicateWorkflow;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ReplicatesService {

    private ReplicatesRepository replicatesRepository;
    private IexecHubService iexecHubService;
    private ApplicationEventPublisher applicationEventPublisher;
    private Web3jService web3jService;
    private ResultService resultService;
    private StdoutService stdoutService;

    public ReplicatesService(ReplicatesRepository replicatesRepository,
                             IexecHubService iexecHubService,
                             ApplicationEventPublisher applicationEventPublisher,
                             Web3jService web3jService,
                             ResultService resultService,
                             StdoutService stdoutService) {
        this.replicatesRepository = replicatesRepository;
        this.iexecHubService = iexecHubService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.web3jService = web3jService;
        this.resultService = resultService;
        this.stdoutService = stdoutService;
    }

    public void addNewReplicate(String chainTaskId, String walletAddress) {
        if (!getReplicate(chainTaskId, walletAddress).isPresent()) {
            Optional<ReplicatesList> optional = getReplicatesList(chainTaskId);
            if (optional.isPresent()) {
                ReplicatesList replicatesList = optional.get();
                Replicate replicate = new Replicate(walletAddress, chainTaskId);
                replicate.setWorkerWeight(iexecHubService.getWorkerWeight(walletAddress));// workerWeight value for pendingWeight estimate
                replicatesList.getReplicates().add(replicate);

                replicatesRepository.save(replicatesList);
                log.info("New replicate saved [chainTaskId:{}, walletAddress:{}]", chainTaskId, walletAddress);
            }
        } else {
            log.error("Replicate already saved [chainTaskId:{}, walletAddress:{}]", chainTaskId, walletAddress);
        }

    }

    public synchronized void createEmptyReplicateList(String chainTaskId) {
        replicatesRepository.save(new ReplicatesList(chainTaskId));
    }

    public Optional<ReplicatesList> getReplicatesList(String chainTaskId) {
        return replicatesRepository.findByChainTaskId(chainTaskId);
    }

    public List<Replicate> getReplicates(String chainTaskId) {
        Optional<ReplicatesList> optionalList = getReplicatesList(chainTaskId);
        if (!optionalList.isPresent()) {
            return Collections.emptyList();
        }
        return optionalList.get().getReplicates();
    }

    public Optional<Replicate> getReplicate(String chainTaskId, String walletAddress) {
        Optional<ReplicatesList> optional = getReplicatesList(chainTaskId);
        if (!optional.isPresent()) {
            return Optional.empty();
        }

        for (Replicate replicate : optional.get().getReplicates()) {
            if (replicate.getWalletAddress().equals(walletAddress)) {
                return Optional.of(replicate);
            }
        }

        return Optional.empty();
    }

    public boolean hasWorkerAlreadyParticipated(String chainTaskId, String walletAddress) {
        return getReplicate(chainTaskId, walletAddress).isPresent();
    }

    public int getNbReplicatesWithCurrentStatus(String chainTaskId, ReplicateStatus... listStatus) {
        int nbReplicates = 0;
        for (Replicate replicate : getReplicates(chainTaskId)) {
            for (ReplicateStatus status : listStatus) {
                if (replicate.getCurrentStatus().equals(status)) {
                    nbReplicates++;
                }
            }
        }
        return nbReplicates;
    }

    public int getNbReplicatesContainingStatus(String chainTaskId, ReplicateStatus... listStatus) {
        Set<String> addressReplicates = new HashSet<>();
        for (Replicate replicate : getReplicates(chainTaskId)) {
            List<ReplicateStatus> listReplicateStatus = replicate.getStatusUpdateList().stream()
                    .map(ReplicateStatusUpdate::getStatus)
                    .collect(Collectors.toList());
            for (ReplicateStatus status : listStatus) {
                if (listReplicateStatus.contains(status)) {
                    addressReplicates.add(replicate.getWalletAddress());
                }
            }
        }
        return addressReplicates.size();
    }

    public int getNbValidContributedWinners(String chainTaskId, String contributionHash) {
        int nbValidWinners = 0;
        for (Replicate replicate : getReplicates(chainTaskId)) {
            Optional<ReplicateStatus> oStatus = replicate.getLastRelevantStatus();
            if (oStatus.isPresent() && oStatus.get().equals(CONTRIBUTED)
                    && contributionHash.equals(replicate.getContributionHash())) {
                nbValidWinners++;
            }
        }
        return nbValidWinners;
    }

    public int getNbOffChainReplicatesWithStatus(String chainTaskId, ReplicateStatus status) {
        return getNbReplicatesWithCurrentStatus(chainTaskId, status) +
                getNbReplicatesWithGivenStatusJustBeforeWorkerLost(chainTaskId, status);
    }

    /*
     * For status = CONTRIBUTED, a replicate will be counted when statuses = { CREATED, ..., CONTRIBUTED, WORKER_LOST }
     * For status = REVEALED, a replicate will be counted when statuses = { CREATED, ..., REVEALED, WORKER_LOST }
     */
    private int getNbReplicatesWithGivenStatusJustBeforeWorkerLost(String chainTaskId, ReplicateStatus status) {
        int nbReplicates = 0;
        for (Replicate replicate : getReplicates(chainTaskId)) {
            if (isStatusBeforeWorkerLostEqualsTo(replicate, status)) {
                nbReplicates++;
            }
        }
        return nbReplicates;
    }

    public Optional<Replicate> getRandomReplicateWithRevealStatus(String chainTaskId) {
        List<Replicate> revealReplicates = getReplicates(chainTaskId);
        Collections.shuffle(revealReplicates);

        for (Replicate replicate : revealReplicates) {
            if (replicate.getCurrentStatus().equals(REVEALED)) {
                return Optional.of(replicate);
            }
        }

        return Optional.empty();
    }

    public Optional<Replicate> getReplicateWithResultUploadedStatus(String chainTaskId) {
        for (Replicate replicate : getReplicates(chainTaskId)) {

            boolean isStatusResultUploaded = replicate.getCurrentStatus().equals(RESULT_UPLOADED);
            boolean isStatusResultUploadedBeforeWorkerLost = isStatusBeforeWorkerLostEqualsTo(replicate, RESULT_UPLOADED);

            if (isStatusResultUploaded || isStatusResultUploadedBeforeWorkerLost) {
                return Optional.of(replicate);
            }
        }

        return Optional.empty();
    }

    private boolean isStatusBeforeWorkerLostEqualsTo(Replicate replicate, ReplicateStatus status) {
        int size = replicate.getStatusUpdateList().size();
        return size >= 2
                && replicate.getStatusUpdateList().get(size - 1).getStatus().equals(WORKER_LOST)
                && replicate.getStatusUpdateList().get(size - 2).getStatus().equals(status);
    }

    /*
     * This implicitly sets the modifier to POOL_MANAGER
     */
    public void updateReplicateStatus(String chainTaskId,
                                      String walletAddress,
                                      ReplicateStatus newStatus) {
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.poolManagerRequest(newStatus);
        updateReplicateStatus(chainTaskId, walletAddress, statusUpdate);
    }

    /*
     * This implicitly sets the modifier to POOL_MANAGER
     */
    public void updateReplicateStatus(String chainTaskId,
                                      String walletAddress,
                                      ReplicateStatus newStatus,
                                      ReplicateStatusDetails details) {
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.poolManagerRequest(newStatus, details);
        updateReplicateStatus(chainTaskId, walletAddress, statusUpdate);
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
     * @return {@link Optional#empty()} if this update is OK, {@code Optional} containing the error reason otherwise.
     */
    public Optional<ReplicateStatusUpdateError> canUpdateReplicateStatus(String chainTaskId,
                                               String walletAddress,
                                               ReplicateStatusUpdate statusUpdate) {
        Optional<ReplicatesList> oReplicateList = getReplicatesList(chainTaskId);
        if (oReplicateList.isEmpty() || oReplicateList.get().getReplicateOfWorker(walletAddress).isEmpty()) {
            log.error("Cannot update replicate, could not get replicate [chainTaskId:{}, UpdateRequest:{}]",
                    chainTaskId, statusUpdate);
            return Optional.of(ReplicateStatusUpdateError.UNKNOWN_REPLICATE);
        }

        ReplicatesList replicatesList = oReplicateList.get();
        Replicate replicate = replicatesList.getReplicateOfWorker(walletAddress).orElseThrow(); // "get" could be used there but triggers a warning
        ReplicateStatus newStatus = statusUpdate.getStatus();

        boolean hasAlreadyTransitionedToStatus = replicate.containsStatus(newStatus);
        if (hasAlreadyTransitionedToStatus) {
            log.warn("Cannot update replicate, status {} already reported.", newStatus);
            return Optional.of(ReplicateStatusUpdateError.ALREADY_REPORTED);
        }

        boolean isValidTransition = ReplicateWorkflow.getInstance()
                .isValidTransition(replicate.getCurrentStatus(), newStatus);
        if (!isValidTransition) {
            log.warn("Cannot update replicate, bad workflow transition {}",
                    getStatusUpdateLogs(chainTaskId, replicate, statusUpdate));
            return Optional.of(ReplicateStatusUpdateError.BAD_WORKFLOW_TRANSITION);
        }

        boolean canUpdate = true;

        switch (newStatus) {
            case CONTRIBUTE_FAILED:
            case REVEAL_FAILED:
                canUpdate = false;
                break;
            case RESULT_UPLOAD_FAILED:
                canUpdate = verifyStatus(chainTaskId, walletAddress, newStatus);
                break;
            case CONTRIBUTED:
            case REVEALED:
                canUpdate = canUpdateToBlockchainSuccess(chainTaskId, replicate, statusUpdate);
                break;
            case RESULT_UPLOADED:
                canUpdate = canUpdateToUploadSuccess(chainTaskId, replicate, statusUpdate);
                break;
            default:
                break;
        }

        if (!canUpdate) {
            log.error("Cannot update replicate {}",
                    getStatusUpdateLogs(chainTaskId, replicate, statusUpdate));
            return Optional.of(ReplicateStatusUpdateError.GENERIC_CANT_UPDATE);
        }

        return Optional.empty();
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
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 100)
    public Optional<TaskNotificationType> updateReplicateStatus(String chainTaskId,
                                                                String walletAddress,
                                                                ReplicateStatusUpdate statusUpdate) {
        log.info("Replicate update request [status:{}, chainTaskId:{}, walletAddress:{}, details:{}]",
                statusUpdate.getStatus(), chainTaskId, walletAddress, statusUpdate.getDetailsWithoutStdout());

        final Optional<ReplicateStatusUpdateError> replicateStatusUpdateError =
                canUpdateReplicateStatus(chainTaskId,walletAddress,statusUpdate);
        if (replicateStatusUpdateError.isPresent()) {
            return Optional.empty();
        }

        ReplicatesList replicatesList = getReplicatesList(chainTaskId).orElseThrow();           // "get" could be used there but triggers a warning
        Replicate replicate = replicatesList.getReplicateOfWorker(walletAddress).orElseThrow(); // "get" could be used there but triggers a warning
        ReplicateStatus newStatus = statusUpdate.getStatus();

        if (statusUpdate.getDetails() != null && statusUpdate.getDetails().getStdout() != null) {
            if (statusUpdate.getStatus().equals(COMPUTED)) {
                String stdout = statusUpdate.getDetails().tailStdout().getStdout();
                stdoutService.addReplicateStdout(chainTaskId, walletAddress, stdout);
            }
            statusUpdate.getDetails().setStdout(null);
        }

        replicate.updateStatus(statusUpdate);
        replicatesRepository.save(replicatesList);
        applicationEventPublisher.publishEvent(new ReplicateUpdatedEvent(chainTaskId, walletAddress, statusUpdate));
        ReplicateStatusCause newStatusCause = statusUpdate.getDetails() != null ?
                statusUpdate.getDetails().getCause() : null;
        TaskNotificationType nextAction = ReplicateWorkflow.getInstance().getNextAction(newStatus, newStatusCause);

        log.info("Replicate updated successfully [newStatus:{}, newStatusCause:{} " +
                        "nextAction:{}, chainTaskId:{}, walletAddress:{}]",
                replicate.getCurrentStatus(), newStatusCause, nextAction, chainTaskId, walletAddress);

        return Optional.ofNullable(nextAction); // should we return a default action when null?
    }

    @Recover
    public void updateReplicateStatus(OptimisticLockingFailureException exception,
                                      String chainTaskId,
                                      String walletAddress,
                                      ReplicateStatusUpdate statusUpdate) {
        log.error("Could not update replicate status, maximum number of retries reached");
        exception.printStackTrace();
    }

    private boolean canUpdateToBlockchainSuccess(String chainTaskId, Replicate replicate,
                                                 ReplicateStatusUpdate statusUpdate) {
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

        if (!verifyStatus(chainTaskId, replicate.getWalletAddress(), newStatus)) {
            log.error("Cannot update replicate, status is not correct",
                    getStatusUpdateLogs(chainTaskId, replicate, statusUpdate));
            return false;
        }

        if (newStatus.equals(CONTRIBUTED) && !updateReplicateWeight(chainTaskId, replicate)) {
            log.error("Cannot update replicate, worker weight not updated {}",
                    getStatusUpdateLogs(chainTaskId, replicate, statusUpdate));
            return false;
        }

        return true;
    }

    private boolean canUpdateToUploadSuccess(String chainTaskId, Replicate replicate,
                                             ReplicateStatusUpdate statusUpdate) {
        ReplicateStatus newStatus = statusUpdate.getStatus();
        ReplicateStatusDetails details = statusUpdate.getDetails();

        if (!verifyStatus(chainTaskId, replicate.getWalletAddress(), newStatus)) {
            log.error("Cannot update replicate, status is not correct",
                    getStatusUpdateLogs(chainTaskId, replicate, statusUpdate));
            return false;
        }

        if (details == null || details.getResultLink() == null || details.getResultLink().isEmpty()) {
            log.error("Cannot update replicate, missing resultLink {}",
                    getStatusUpdateLogs(chainTaskId, replicate, statusUpdate));
            return false;
        }

        replicate.setResultLink(details.getResultLink());
        replicate.setChainCallbackData(details.getChainCallbackData());
        return true;

    }

    private boolean verifyStatus(String chainTaskId, String walletAddress, ReplicateStatus status) {
        switch (status) {
            case CONTRIBUTED:
                return iexecHubService.repeatIsContributedTrue(chainTaskId, walletAddress);
            case REVEALED:
                return iexecHubService.repeatIsRevealedTrue(chainTaskId, walletAddress);
            case RESULT_UPLOADED:
                return isResultUploaded(chainTaskId);
            case RESULT_UPLOAD_FAILED:
                return !isResultUploaded(chainTaskId);
            default:
                return true;
        }
    }

    private boolean updateReplicateWeight(String chainTaskId, Replicate replicate) {
        String walletAddress = replicate.getWalletAddress();
        int workerWeight = iexecHubService.getWorkerWeight(walletAddress);
        if (workerWeight == 0) {
            log.error("Failed to get worker weight [chainTaskId:{}, workerWallet:{}]",
                    chainTaskId, walletAddress);
            return false;
        }

        Optional<ChainContribution> chainContribution =
                iexecHubService.getChainContribution(chainTaskId, walletAddress);

        if (!chainContribution.isPresent()) {
            log.error("Failed to get chain contribution [chainTaskId:{}, workerWallet:{}]",
                    chainTaskId, walletAddress);
            return false;
        }

        replicate.setContributionHash(chainContribution.get().getResultHash());
        replicate.setWorkerWeight(workerWeight);
        return true;
    }

    private String getStatusUpdateLogs(String chainTaskId, Replicate replicate, ReplicateStatusUpdate statusUpdate) {
        return String.format("[currentStatus:%s, newStatus:%s chainTaskId:%s, walletAddress:%s, details:%s]",
                replicate.getCurrentStatus(), statusUpdate.getStatus(), chainTaskId, replicate.getWalletAddress(),
                statusUpdate.getDetailsWithoutStdout());
    }

    public boolean isResultUploaded(String chainTaskId) {
        Optional<TaskDescription> task = iexecHubService.getTaskDescriptionFromChain(chainTaskId);

        if (task.isEmpty()){
            return false;
        }

        // Offchain computing - basic & tee
        if (task.get().containsCallback()){
            return true;
        }

        // Cloud computing - tee
        if (task.get().isTeeTask()) {
            return true; // pushed from enclave
        }

        // Cloud computing - basic
        return resultService.isResultUploaded(chainTaskId);
    }

    public boolean didReplicateContributeOnchain(String chainTaskId, String walletAddress) {
        return iexecHubService.isStatusTrueOnChain(
                chainTaskId, walletAddress, getChainStatus(ReplicateStatus.CONTRIBUTED));
    }

    public boolean didReplicateRevealOnchain(String chainTaskId, String walletAddress) {
        return iexecHubService.isStatusTrueOnChain(
                chainTaskId, walletAddress, getChainStatus(ReplicateStatus.REVEALED));
    }

    public void setRevealTimeoutStatusIfNeeded(String chainTaskId, Replicate replicate) {
        Optional<ReplicateStatus> oStatus = replicate.getLastRelevantStatus();
        if (!oStatus.isPresent()) {
            return;
        }
        ReplicateStatus status = oStatus.get();
        if (status.equals(REVEALING) || status.equals(CONTRIBUTED)) {
            ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.poolManagerRequest(FAILED, REVEAL_TIMEOUT);
            updateReplicateStatus(chainTaskId, replicate.getWalletAddress(), statusUpdate);
        }
    }
}