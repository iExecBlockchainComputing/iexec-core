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
import com.iexec.common.utils.ContextualLockRunner;
import com.iexec.core.chain.SignatureService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.contribution.ConsensusHelper;
import com.iexec.core.detector.task.ContributionTimeoutTaskDetector;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.TaskUpdateManager;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import net.jodah.expiringmap.ExpiringMap;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.core.task.Task.LONGEST_TASK_TIMEOUT;


@Service
public class ReplicateSupplyService {

    private final ReplicatesService replicatesService;
    private final SignatureService signatureService;
    private final TaskService taskService;
    private final TaskUpdateManager taskUpdateManager;
    private final WorkerService workerService;
    private final Web3jService web3jService;
    private final ContributionTimeoutTaskDetector contributionTimeoutTaskDetector;

    private final ContextualLockRunner<String> newReplicateLockRunner =
            new ContextualLockRunner<>(1, TimeUnit.MINUTES);
    private final Map<String, Boolean> taskAccessForNewReplicateLock =
            ExpiringMap.builder()
                    .expiration(LONGEST_TASK_TIMEOUT.getSeconds(), TimeUnit.SECONDS)
                    .build();

    public ReplicateSupplyService(ReplicatesService replicatesService,
                                  SignatureService signatureService,
                                  TaskService taskService,
                                  TaskUpdateManager taskUpdateManager,
                                  WorkerService workerService,
                                  Web3jService web3jService,
                                  ContributionTimeoutTaskDetector contributionTimeoutTaskDetector) {
        this.replicatesService = replicatesService;
        this.signatureService = signatureService;
        this.taskService = taskService;
        this.taskUpdateManager = taskUpdateManager;
        this.workerService = workerService;
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

        // TODO : Remove this, the optional can never be empty
        // This is covered in workerService.canAcceptMoreWorks
        Optional<Worker> optional = workerService.getWorker(walletAddress);
        if (optional.isEmpty()) {
            return Optional.empty();
        }
        Worker worker = optional.get();

        // return empty if there is no task to contribute
        List<Task> runningTasks = taskService.getInitializedOrRunningTasks(!worker.isTeeEnabled());
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

        return validTasks
                .stream()
                .map(task -> getAuthorizationForTask(task, walletAddress))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private Optional<WorkerpoolAuthorization> getAuthorizationForTask(Task task, String walletAddress) {
        String chainTaskId = task.getChainTaskId();
        if (!canWorkerAcceptTask(task, walletAddress)) {
            return Optional.empty();
        }

        replicatesService.addNewReplicate(chainTaskId, walletAddress);
        unlockTaskAccessForNewReplicate(chainTaskId);
        workerService.addChainTaskIdToWorker(chainTaskId, walletAddress);

        // generate contribution authorization
        final WorkerpoolAuthorization authorization = signatureService.createAuthorization(
                walletAddress,
                chainTaskId,
                task.getEnclaveChallenge());
        return Optional.of(authorization);
    }

    private boolean canWorkerAcceptTask(Task task, String walletAddress) {
        if (task.getEnclaveChallenge().isEmpty()) {
            return false;
        }

        final String chainTaskId = task.getChainTaskId();
        final Optional<ReplicatesList> oReplicatesList = replicatesService.getReplicatesList(chainTaskId);
        // Check is only here to prevent
        // "`Optional.get()` without `isPresent()` warning".
        // This case should not happen.
        if (oReplicatesList.isEmpty()) {
            return false;
        }

        final ReplicatesList replicatesList = oReplicatesList.get();

        // no need to go further if the consensus is already reached on-chain
        // the task should be updated since the consensus is reached but it is still in RUNNING status
        if (taskUpdateManager.isConsensusReached(replicatesList)) {
            taskUpdateManager.publishUpdateTaskRequest(chainTaskId);
            return false;
        }

        if (!lockTaskAccessForNewReplicateIfPossible(chainTaskId)) {
            return false;
        }

        // Check if task is still in contribution phase with INITIALIZED or RUNNING status
        Optional<Task> upToDateTask = taskService.getTaskByChainTaskId(chainTaskId);
        final boolean taskNeedsMoreContributions = ConsensusHelper.doesTaskNeedMoreContributionsForConsensus(
                chainTaskId,
                replicatesList.getReplicates(),
                task.getTrust(),
                task.getMaxExecutionTime());

        if (upToDateTask.isEmpty()
                || !TaskStatus.isInContributionPhase(upToDateTask.get().getCurrentStatus())
                || !taskNeedsMoreContributions
                || replicatesService.hasWorkerAlreadyParticipated(replicatesList, walletAddress)) {
            unlockTaskAccessForNewReplicate(chainTaskId);
            return false;
        }

        return true;
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
            if (oReplicate.isEmpty()) {
                continue;
            }
            Replicate replicate = oReplicate.get();
            boolean isRecoverable = replicate.isRecoverable();
            if (!isRecoverable) {
                continue;
            }
            String enclaveChallenge = task.getEnclaveChallenge();
            if (task.isTeeTask() && enclaveChallenge.isEmpty()) {
                continue;
            }
            Optional<TaskNotificationType> taskNotificationType = getTaskNotificationType(task, replicate, blockNumber);
            if (taskNotificationType.isEmpty()) {
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

        if (replicate.getLastRelevantStatus().isEmpty()) {
            return Optional.empty();
        }

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
        if (oReplicateWithLatestChanges.isEmpty()) {
            return Optional.empty();
        }

        Replicate replicateWithLatestChanges = oReplicateWithLatestChanges.get();
        if (replicateWithLatestChanges.getLastRelevantStatus().isEmpty()) {
            return Optional.empty();
        }

        boolean didReplicateContribute = replicateWithLatestChanges.getLastRelevantStatus().get()
                .equals(ReplicateStatus.CONTRIBUTED);

        if (didReplicateContribute) {
            final Optional<ReplicatesList> oReplicatesList = replicatesService.getReplicatesList(chainTaskId);
            if (oReplicatesList.isEmpty()) {
                return Optional.empty();
            }
            if (!taskUpdateManager.isConsensusReached(oReplicatesList.get())) {
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

        if (replicate.getLastRelevantStatus().isEmpty()) {
            return Optional.empty();
        }

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

        if (oReplicateWithLatestChanges.isEmpty()) {
            return Optional.empty();
        }
        replicate = oReplicateWithLatestChanges.get();
        if (replicate.getLastRelevantStatus().isEmpty()) {
            return Optional.empty();
        }

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

        if (replicate.getLastRelevantStatus().isEmpty()) {
            return Optional.empty();
        }

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
        if (oTask.isEmpty()) {
            return Optional.empty();
        }

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

    // region Locking access to task for new replicate
    /**
     * Locks a task access for a new replicate if no other worker
     * is already trying to create a replicate on that task.
     * This method is synchronized on {@code chainTaskId}.
     *
     * @param chainTaskId ID of the task to lock access on.
     * @return {@literal true} if the lock has been acquired,
     * {@literal false} otherwise.
     */
    boolean lockTaskAccessForNewReplicateIfPossible(String chainTaskId) {
        // There could have been some race condition there without synchronization.
        return newReplicateLockRunner.getWithLock(chainTaskId, () -> {
            if (Boolean.TRUE.equals(taskAccessForNewReplicateLock.getOrDefault(chainTaskId, false))) {
                return false;
            }
            setTaskAccessForNewReplicateLock(chainTaskId, true);
            return true;
        });
    }

    void unlockTaskAccessForNewReplicate(String chainTaskId) {
        setTaskAccessForNewReplicateLock(chainTaskId, false);
    }

    private void setTaskAccessForNewReplicateLock(String chainTaskId, boolean isTaskBeingAccessedForNewReplicate) {
        taskAccessForNewReplicateLock.put(chainTaskId, isTaskBeingAccessedForNewReplicate);
    }
    // endregion
}
