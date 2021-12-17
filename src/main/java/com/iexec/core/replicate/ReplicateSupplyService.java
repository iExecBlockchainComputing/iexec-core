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

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.common.task.TaskAbortCause;
import com.iexec.core.chain.SignatureService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.detector.task.ContributionTimeoutTaskDetector;
import com.iexec.core.contribution.ConsensusHelper;
import com.iexec.core.sms.SmsService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.TaskUpdateManager;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.iexec.common.replicate.ReplicateStatus.*;


@Service
public class ReplicateSupplyService {

    private final ReplicatesService replicatesService;
    private final SignatureService signatureService;
    private final TaskService taskService;
    private final TaskUpdateManager taskUpdateManager;
    private final WorkerService workerService;
    private final SmsService smsService;
    private final Web3jService web3jService;
    private final ContributionTimeoutTaskDetector contributionTimeoutTaskDetector;

    public ReplicateSupplyService(ReplicatesService replicatesService,
                                  SignatureService signatureService,
                                  TaskService taskService,
                                  TaskUpdateManager taskUpdateManager,
                                  WorkerService workerService,
                                  SmsService smsService,
                                  Web3jService web3jService,
                                  ContributionTimeoutTaskDetector contributionTimeoutTaskDetector) {
        this.replicatesService = replicatesService;
        this.signatureService = signatureService;
        this.taskService = taskService;
        this.taskUpdateManager = taskUpdateManager;
        this.workerService = workerService;
        this.smsService = smsService;
        this.web3jService = web3jService;
        this.contributionTimeoutTaskDetector = contributionTimeoutTaskDetector;
    }

    /*
     * #1 Retryable - In case the task has been modified between reading and writing it, it is retried up to 5 times
     *
     * #2 TaskAccessForNewReplicateLock - To avoid the case where only 1 replicate is required but 2 replicates are
     * created since 2 workers are calling getAvailableReplicate() and reading the database at the same time, we need a
     *  'TaskAccessForNewReplicateLock' which should be:
     *  - locked before `replicatesService.moreReplicatesNeeded(..)`
     *  - released after `replicatesService.addNewReplicate(..)` in the best scenario
     *  - released before any `continue` or  `return`
     *
     */
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 5)
    Optional<WorkerpoolAuthorization> getAuthOfAvailableReplicate(long workerLastBlock, String walletAddress) {
        // return empty if max computing task is reached or if the worker is not found
        if (!workerService.canAcceptMoreWorks(walletAddress)) {
            return Optional.empty();
        }

        // return empty if the worker is not sync
        //TODO Check if worker node is sync
        boolean isWorkerLastBlockAvailable = workerLastBlock > 0;
        if (!isWorkerLastBlockAvailable) {
            return Optional.empty();
        }

        if (!web3jService.hasEnoughGas(walletAddress)) {
            return Optional.empty();
        }

        // return empty if there is no task to contribute
        List<Task> runningTasks = taskService.getInitializedOrRunningTasks();
        if (runningTasks.isEmpty()) {
            return Optional.empty();
        }

        // filter the Tasks that have reached the contribution deadline
        List<Task> validTasks = runningTasks.stream()
                .filter(task -> ! task.isContributionDeadlineReached())
                .collect(Collectors.toCollection(ArrayList::new));
        if (validTasks.size() != runningTasks.size()) {
            contributionTimeoutTaskDetector.detect();
        }

        Optional<Worker> optional = workerService.getWorker(walletAddress);
        if (optional.isEmpty()) {
            return Optional.empty();
        }
        Worker worker = optional.get();

        for (Task task : validTasks) {
            String chainTaskId = task.getChainTaskId();

            // no need to ge further if the consensus is already reached on-chain
            // the task should be updated since the consensus is reached but it is still in RUNNING status
            if (taskUpdateManager.isConsensusReached(task)) {
                taskUpdateManager.publishUpdateTaskRequest(chainTaskId);
                continue;
            }

            // skip the task if it needs TEE and the worker doesn't support it
            boolean isTeeTask = task.isTeeTask();
            if (isTeeTask && !worker.isTeeEnabled()) {
                continue;
            }

            taskService.initializeTaskAccessForNewReplicateLock(chainTaskId);
            if (taskService.isTaskBeingAccessedForNewReplicate(chainTaskId)) {
                continue;//skip task if being accessed
            }
            taskService.lockTaskAccessForNewReplicate(chainTaskId);

            boolean hasWorkerAlreadyParticipated = replicatesService.hasWorkerAlreadyParticipated(
                    chainTaskId, walletAddress);

            final List<Replicate> replicates = replicatesService.getReplicates(chainTaskId);
            final boolean taskNeedsMoreContributions = ConsensusHelper.doesTaskNeedMoreContributionsForConsensus(
                    chainTaskId,
                    replicates,
                    task.getTrust(),
                    task.getMaxExecutionTime());
            if (!hasWorkerAlreadyParticipated && taskNeedsMoreContributions) {
                String enclaveChallenge = smsService.getEnclaveChallenge(chainTaskId, isTeeTask);
                if (enclaveChallenge.isEmpty()) {
                    taskService.unlockTaskAccessForNewReplicate(chainTaskId);//avoid dead lock
                    continue;
                }

                replicatesService.addNewReplicate(chainTaskId, walletAddress);
                taskService.unlockTaskAccessForNewReplicate(chainTaskId);
                workerService.addChainTaskIdToWorker(chainTaskId, walletAddress);

                // generate contribution authorization
                return Optional.of(signatureService.createAuthorization(
                        walletAddress, chainTaskId, enclaveChallenge));
            }
            taskService.unlockTaskAccessForNewReplicate(chainTaskId);
        }

        return Optional.empty();
    }

    /**
     * Get notifications missed by the worker during the time it was absent.
     * 
     * @param blockNumber last seen blocknumber by the worker
     * @param walletAddress of the worker
     * @return list of missed notifications. Can be empty if no notification is found
     */
    public List<TaskNotification> getMissedTaskNotifications(long blockNumber, String walletAddress) {
        List<String> chainTaskIdList = workerService.getChainTaskIds(walletAddress);
        List<Task> tasksWithWorkerParticipation = taskService.getTasksByChainTaskIds(chainTaskIdList);
        List<TaskNotification> taskNotifications = new ArrayList<>();
        for (Task task : tasksWithWorkerParticipation) {
            String chainTaskId = task.getChainTaskId();

            Optional<Replicate> oReplicate = replicatesService.getReplicate(chainTaskId, walletAddress);
            if (!oReplicate.isPresent()) {
                continue;
            }
            Replicate replicate = oReplicate.get();
            boolean isRecoverable = replicate.isRecoverable();
            if (!isRecoverable) {
                continue;
            }
            String enclaveChallenge = smsService.getEnclaveChallenge(chainTaskId, task.isTeeTask());
            if (task.isTeeTask() && enclaveChallenge.isEmpty()) {
                continue;
            }
            Optional<TaskNotificationType> taskNotificationType = getTaskNotificationType(task, replicate, blockNumber);
            if (!taskNotificationType.isPresent()) {
                continue;
            }
            TaskNotificationExtra taskNotificationExtra =
                    getTaskNotificationExtra(task, taskNotificationType.get(),  walletAddress, enclaveChallenge);

            TaskNotification taskNotification = TaskNotification.builder()
                    .chainTaskId(chainTaskId)
                    .workersAddress(Collections.singletonList(walletAddress))
                    .taskNotificationType(taskNotificationType.get())
                    .taskNotificationExtra(taskNotificationExtra)
                    .build();

            // change replicate status
            ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.poolManagerRequest(RECOVERING);
            replicatesService.updateReplicateStatus(chainTaskId, walletAddress, statusUpdate);

            taskNotifications.add(taskNotification);
        }

        return taskNotifications;
    }

    private TaskNotificationExtra getTaskNotificationExtra(Task task, TaskNotificationType taskNotificationType, String walletAddress, String enclaveChallenge) {
        TaskNotificationExtra taskNotificationExtra = TaskNotificationExtra.builder().build();

        switch (taskNotificationType){
            case PLEASE_CONTRIBUTE:
                WorkerpoolAuthorization authorization = signatureService.createAuthorization(
                        walletAddress, task.getChainTaskId(), enclaveChallenge);
                taskNotificationExtra.setWorkerpoolAuthorization(authorization);
                break;
            case PLEASE_REVEAL:
                taskNotificationExtra.setBlockNumber(task.getConsensusReachedBlockNumber());
                break;
            case PLEASE_ABORT:
                taskNotificationExtra.setTaskAbortCause(getTaskAbortCause(task));
                break;
            default:
                break;
        }
        return taskNotificationExtra;
    }

    public Optional<TaskNotificationType> getTaskNotificationType(Task task, Replicate replicate, long blockNumber) {

        if (task.inContributionPhase()) {
            return recoverReplicateInContributionPhase(task, replicate, blockNumber);
        }
        // CONTRIBUTION_TIMEOUT or CONSENSUS_REACHED without contribution
        if (task.getCurrentStatus().equals(TaskStatus.CONTRIBUTION_TIMEOUT)
                || (task.getCurrentStatus().equals(TaskStatus.CONSENSUS_REACHED)
                        && !replicate.containsContributedStatus())) {
            return Optional.of(TaskNotificationType.PLEASE_ABORT);
        }

        Optional<TaskNotificationType> oRecoveryAction = Optional.empty();

        if (task.inRevealPhase()) {
            oRecoveryAction = recoverReplicateInRevealPhase(task, replicate, blockNumber);
        }

        if (task.inResultUploadPhase()) {
            oRecoveryAction = recoverReplicateInResultUploadPhase(task, replicate);
        }

        if (task.inCompletionPhase()) {
            return recoverReplicateIfRevealed(replicate);
        }

        return oRecoveryAction;
    }

    /**
     * CREATED, ..., CAN_CONTRIBUTE         => TaskNotificationType.PLEASE_CONTRIBUTE
     * CONTRIBUTING + !onChain              => TaskNotificationType.PLEASE_CONTRIBUTE
     * CONTRIBUTING + done onChain          => updateStatus to CONTRIBUTED & go to next case
     * CONTRIBUTED + !CONSENSUS_REACHED     => TaskNotificationType.PLEASE_WAIT
     * CONTRIBUTED + CONSENSUS_REACHED      => TaskNotificationType.PLEASE_REVEAL
     */

    private Optional<TaskNotificationType> recoverReplicateInContributionPhase(Task task, Replicate replicate, long blockNumber) {
        String chainTaskId = task.getChainTaskId();
        String walletAddress = replicate.getWalletAddress();

        if (!replicate.getLastRelevantStatus().isPresent()) return Optional.empty();

        boolean beforeContributing = replicate.isBeforeStatus(ReplicateStatus.CONTRIBUTING);
        boolean didReplicateStartContributing = replicate.getLastRelevantStatus().get().equals(ReplicateStatus.CONTRIBUTING);
        boolean didReplicateContributeOnChain = replicatesService.didReplicateContributeOnchain(chainTaskId, walletAddress);

        if (beforeContributing) {
            return Optional.of(TaskNotificationType.PLEASE_CONTRIBUTE);
        }

        if (didReplicateStartContributing && !didReplicateContributeOnChain) {
            return Optional.of(TaskNotificationType.PLEASE_CONTRIBUTE);
        }

        if (didReplicateStartContributing && didReplicateContributeOnChain) {
            ReplicateStatusDetails details = new ReplicateStatusDetails(blockNumber);
            replicatesService.updateReplicateStatus(chainTaskId, walletAddress, CONTRIBUTED, details);
        }

        // we read the replicate from db to consider the changes added in the previous case
        Optional<Replicate> oReplicateWithLatestChanges = replicatesService.getReplicate(chainTaskId, walletAddress);
        if (!oReplicateWithLatestChanges.isPresent()) return Optional.empty();

        Replicate replicateWithLatestChanges = oReplicateWithLatestChanges.get();
        if (!replicateWithLatestChanges.getLastRelevantStatus().isPresent()) return Optional.empty();

        boolean didReplicateContribute = replicateWithLatestChanges.getLastRelevantStatus().get()
                .equals(ReplicateStatus.CONTRIBUTED);

        if (didReplicateContribute) {

            if (!taskUpdateManager.isConsensusReached(task)) {
                return Optional.of(TaskNotificationType.PLEASE_WAIT);
            }

            taskUpdateManager.publishUpdateTaskRequest(chainTaskId);
            return Optional.of(TaskNotificationType.PLEASE_REVEAL);
        }

        return Optional.empty();
    }

    /**
     * CONTRIBUTED                      => TaskNotificationType.PLEASE_REVEAL
     * REVEALING + !onChain             => TaskNotificationType.PLEASE_REVEAL
     * REVEALING + done onChain         => update replicateStatus to REVEALED, update task & go to next case
     * REVEALED (no upload req)         => TaskNotificationType.PLEASE_WAIT
     * RESULT_UPLOAD_REQUESTED          => TaskNotificationType.PLEASE_UPLOAD_RESULT
     */

    private Optional<TaskNotificationType> recoverReplicateInRevealPhase(Task task, Replicate replicate, long blockNumber) {
        String chainTaskId = task.getChainTaskId();
        String walletAddress = replicate.getWalletAddress();

        if (!replicate.getLastRelevantStatus().isPresent()) return Optional.empty();

        boolean isInStatusContributed = replicate.getLastRelevantStatus().get().equals(ReplicateStatus.CONTRIBUTED);
        boolean didReplicateStartRevealing = replicate.getLastRelevantStatus().get().equals(ReplicateStatus.REVEALING);
        boolean didReplicateRevealOnChain = replicatesService.didReplicateRevealOnchain(chainTaskId, walletAddress);

        if (isInStatusContributed) {
            return Optional.of(TaskNotificationType.PLEASE_REVEAL);
        }

        if (didReplicateStartRevealing && !didReplicateRevealOnChain) {
            return Optional.of(TaskNotificationType.PLEASE_REVEAL);
        }

        if (didReplicateStartRevealing && didReplicateRevealOnChain) {
            ReplicateStatusDetails details = new ReplicateStatusDetails(blockNumber);
            replicatesService.updateReplicateStatus(chainTaskId, walletAddress, REVEALED, details);
            taskUpdateManager.publishUpdateTaskRequest(chainTaskId).join();
        }

        // we read the replicate from db to consider the changes added in the previous case
        Optional<Replicate> oReplicateWithLatestChanges = replicatesService.getReplicate(chainTaskId, walletAddress);

        replicate = oReplicateWithLatestChanges.get();
        if (!replicate.getLastRelevantStatus().isPresent()) return Optional.empty();

        boolean didReplicateReveal = replicate.getLastRelevantStatus().get()
                .equals(ReplicateStatus.REVEALED);

        boolean wasReplicateRequestedToUpload = replicate.getLastRelevantStatus().get()
                .equals(ReplicateStatus.RESULT_UPLOAD_REQUESTED);

        if (didReplicateReveal) {
            return Optional.of(TaskNotificationType.PLEASE_WAIT);
        }

        if (wasReplicateRequestedToUpload) {
            return Optional.of(TaskNotificationType.PLEASE_UPLOAD);
        }

        return Optional.empty();
    }

    /**
     * RESULT_UPLOAD_REQUESTED          => TaskNotificationType.PLEASE_UPLOAD_RESULT
     * RESULT_UPLOADING + !done yet     => TaskNotificationType.PLEASE_UPLOAD_RESULT
     * RESULT_UPLOADING + done          => TaskNotificationType.PLEASE_WAIT
     * update to ReplicateStatus.RESULT_UPLOADED
     * RESULT_UPLOADED                  => TaskNotificationType.PLEASE_WAIT
     */

    private Optional<TaskNotificationType> recoverReplicateInResultUploadPhase(Task task, Replicate replicate) {
        String chainTaskId = task.getChainTaskId();
        String walletAddress = replicate.getWalletAddress();

        if (!replicate.getLastRelevantStatus().isPresent()) return Optional.empty();

        boolean wasReplicateRequestedToUpload = replicate.getLastRelevantStatus().get().equals(ReplicateStatus.RESULT_UPLOAD_REQUESTED);
        boolean didReplicateStartUploading = replicate.getLastRelevantStatus().get().equals(ReplicateStatus.RESULT_UPLOADING);
        boolean didReplicateUploadWithoutNotifying = replicatesService.isResultUploaded(task.getChainTaskId());
        boolean hasReplicateAlreadyUploaded = replicate.getLastRelevantStatus().get().equals(ReplicateStatus.RESULT_UPLOADED);

        if (wasReplicateRequestedToUpload) {
            return Optional.of(TaskNotificationType.PLEASE_UPLOAD);
        }

        if (didReplicateStartUploading && !didReplicateUploadWithoutNotifying) {
            return Optional.of(TaskNotificationType.PLEASE_UPLOAD);
        }

        if (didReplicateStartUploading && didReplicateUploadWithoutNotifying) {
            replicatesService.updateReplicateStatus(chainTaskId, walletAddress, RESULT_UPLOADED);

            taskUpdateManager.publishUpdateTaskRequest(chainTaskId);
            return Optional.of(TaskNotificationType.PLEASE_WAIT);
        }

        if (hasReplicateAlreadyUploaded) {
            return Optional.of(TaskNotificationType.PLEASE_WAIT);
        }

        return Optional.empty();
    }

    /**
     * REVEALED + task in COMPLETED status          => TaskNotificationType.PLEASE_COMPLETE
     * REVEALED + task not in COMPLETED status      => TaskNotificationType.PLEASE_WAIT
     * !REVEALED                                    => null
     */

    private Optional<TaskNotificationType> recoverReplicateIfRevealed(Replicate replicate) {
        // refresh task
        Optional<Task> oTask = taskService.getTaskByChainTaskId(replicate.getChainTaskId());
        if (!oTask.isPresent()) return Optional.empty();

        if (replicate.containsRevealedStatus()) {
            if (oTask.get().getCurrentStatus().equals(TaskStatus.COMPLETED)) {
                return Optional.of(TaskNotificationType.PLEASE_COMPLETE);
            }

            return Optional.of(TaskNotificationType.PLEASE_WAIT);
        }

        return Optional.empty();
    }

    private TaskAbortCause getTaskAbortCause(Task task) {
        switch (task.getCurrentStatus()) {
            case CONSENSUS_REACHED:
                return TaskAbortCause.CONSENSUS_REACHED;
            case CONTRIBUTION_TIMEOUT:
                return TaskAbortCause.CONTRIBUTION_TIMEOUT;
            default:
                return TaskAbortCause.UNKNOWN;
        }
    }
}