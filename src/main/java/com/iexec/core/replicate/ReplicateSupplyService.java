/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.lifecycle.purge.ExpiringTaskMapFactory;
import com.iexec.common.lifecycle.purge.Purgeable;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.common.replicate.ReplicateTaskSummary;
import com.iexec.common.replicate.ReplicateTaskSummary.ReplicateTaskSummaryBuilder;
import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.task.TaskAbortCause;
import com.iexec.core.chain.SignatureService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.contribution.ConsensusHelper;
import com.iexec.core.notification.TaskNotification;
import com.iexec.core.notification.TaskNotificationExtra;
import com.iexec.core.notification.TaskNotificationType;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.iexec.common.replicate.ReplicateStatus.*;

@Slf4j
@Service
public class ReplicateSupplyService implements Purgeable {

    private final ReplicatesService replicatesService;
    private final SignatureService signatureService;
    private final TaskService taskService;
    private final TaskUpdateRequestManager taskUpdateRequestManager;
    private final WorkerService workerService;
    private final Web3jService web3jService;
    final Map<String, Lock> taskAccessForNewReplicateLocks = ExpiringTaskMapFactory.getExpiringTaskMap();

    public ReplicateSupplyService(ReplicatesService replicatesService,
                                  SignatureService signatureService,
                                  TaskService taskService,
                                  TaskUpdateRequestManager taskUpdateRequestManager,
                                  WorkerService workerService,
                                  Web3jService web3jService) {
        this.replicatesService = replicatesService;
        this.signatureService = signatureService;
        this.taskService = taskService;
        this.taskUpdateRequestManager = taskUpdateRequestManager;
        this.workerService = workerService;
        this.web3jService = web3jService;
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
    Optional<ReplicateTaskSummary> getAvailableReplicateTaskSummary(long workerLastBlock, String walletAddress) {
        // return empty if the worker is not sync
        //TODO Check if worker node is sync
        final boolean isWorkerLastBlockAvailable = workerLastBlock > 0;
        if (!isWorkerLastBlockAvailable) {
            return Optional.empty();
        }

        if (!web3jService.hasEnoughGas(walletAddress)) {
            return Optional.empty();
        }

        final Optional<Worker> optional = workerService.getWorker(walletAddress);
        if (optional.isEmpty()) {
            return Optional.empty();
        }
        final Worker worker = optional.get();

        // return empty if max computing task is reached or if the worker is not found
        if (!workerService.canAcceptMoreWorks(worker)) {
            return Optional.empty();
        }

        return getReplicateTaskSummaryForAnyAvailableTask(
                walletAddress,
                worker.isTeeEnabled()
        );
    }

    /**
     * Loops through available tasks
     * and finds the first one that needs a new {@link Replicate}.
     *
     * @param walletAddress Wallet address of the worker asking for work.
     * @param isTeeEnabled  Whether this worker supports TEE.
     * @return An {@link Optional} containing a {@link ReplicateTaskSummary}
     * if any {@link Task} is available and can be handled by this worker,
     * {@link Optional#empty()} otherwise.
     */
    private Optional<ReplicateTaskSummary> getReplicateTaskSummaryForAnyAvailableTask(
            String walletAddress,
            boolean isTeeEnabled) {
        final List<String> alreadyScannedTasks = new ArrayList<>();

        Optional<ReplicateTaskSummary> replicateTaskSummary = Optional.empty();
        while (replicateTaskSummary.isEmpty()) {
            final Optional<Task> oTask = taskService.getPrioritizedInitializedOrRunningTask(
                    !isTeeEnabled,
                    alreadyScannedTasks
            );
            if (oTask.isEmpty()) {
                // No more tasks waiting for a new replicate.
                return Optional.empty();
            }

            final Task task = oTask.get();
            alreadyScannedTasks.add(task.getChainTaskId());
            replicateTaskSummary = getReplicateTaskSummary(task, walletAddress);
        }
        return replicateTaskSummary;
    }

    private Optional<ReplicateTaskSummary> getReplicateTaskSummary(Task task, String walletAddress) {
        String chainTaskId = task.getChainTaskId();
        if (!acceptOrRejectTask(task, walletAddress)) {
            return Optional.empty();
        }

        // generate contribution authorization
        final WorkerpoolAuthorization authorization = signatureService.createAuthorization(
                walletAddress,
                chainTaskId,
                task.getEnclaveChallenge());
        ReplicateTaskSummaryBuilder replicateTaskSummary = ReplicateTaskSummary.builder()
                .workerpoolAuthorization(authorization);
        if (task.isTeeTask()) {
            replicateTaskSummary.smsUrl(task.getSmsUrl());
        }
        return Optional.of(replicateTaskSummary.build());
    }

    /**
     * Given a {@link Task} and a {@code walletAddress} of a worker,
     * tries to accept the task - i.e. create a new {@link Replicate}
     * for that task on that worker.
     *
     * @param task          {@link Task} needing at least one new {@link Replicate}.
     * @param walletAddress Wallet address of a worker looking for new {@link Task}.
     * @return {@literal true} if the task has been accepted,
     * {@literal false} otherwise.
     */
    private boolean acceptOrRejectTask(Task task, String walletAddress) {
        if (task.getEnclaveChallenge().isEmpty()) {
            return false;
        }

        final String chainTaskId = task.getChainTaskId();
        final Lock lock = taskAccessForNewReplicateLocks
                .computeIfAbsent(chainTaskId, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            // Can't get lock on task
            // => another replicate is already having a look at this task.
            return false;
        }

        try {
            return replicatesService.getReplicatesList(chainTaskId)
                    .map(replicatesList -> acceptOrRejectTask(task, walletAddress, replicatesList))
                    .orElse(false);
        } finally {
            // We should always unlock the task
            // so that it could be taken by another replicate
            // if there's any issue.
            lock.unlock();
        }
    }

    /**
     * Given a {@link Task}, a {@code walletAddress} of a worker and a {@link ReplicatesList},
     * tries to accept the task - i.e. create a new {@link Replicate}
     * for that task on that worker.
     *
     * @param task           {@link Task} needing at least one new {@link Replicate}.
     * @param walletAddress  Wallet address of a worker looking for new {@link Task}.
     * @param replicatesList Replicates of given {@link Task}.
     * @return {@literal true} if the task has been accepted,
     * {@literal false} otherwise.
     */
    boolean acceptOrRejectTask(Task task, String walletAddress, ReplicatesList replicatesList) {
        final boolean hasWorkerAlreadyParticipated =
                replicatesList.hasWorkerAlreadyParticipated(walletAddress);
        if (hasWorkerAlreadyParticipated) {
            return false;
        }

        final String chainTaskId = replicatesList.getChainTaskId();
        final boolean taskNeedsMoreContributions = ConsensusHelper.doesTaskNeedMoreContributionsForConsensus(
                chainTaskId,
                replicatesList.getReplicates(),
                task.getTrust(),
                task.getMaxExecutionTime());

        if (!taskNeedsMoreContributions
                || taskService.isConsensusReached(replicatesList)) {
            return false;
        }

        return workerService.addChainTaskIdToWorker(chainTaskId, walletAddress)
                .map(worker -> replicatesService.addNewReplicate(replicatesList, walletAddress))
                .orElse(false);
    }

    /**
     * Get notifications missed by the worker during the time it was absent.
     *
     * @param blockNumber   last seen blocknumber by the worker
     * @param walletAddress of the worker
     * @return list of missed notifications. Can be empty if no notification is found
     */
    public List<TaskNotification> getMissedTaskNotifications(long blockNumber, String walletAddress) {
        List<String> chainTaskIdList = workerService.getChainTaskIds(walletAddress);
        List<Task> tasksWithWorkerParticipation = taskService.getTasksByChainTaskIds(chainTaskIdList);
        List<TaskNotification> taskNotifications = new ArrayList<>();
        for (Task task : tasksWithWorkerParticipation) {
            String chainTaskId = task.getChainTaskId();

            final Replicate replicate = replicatesService.getReplicate(chainTaskId, walletAddress).orElse(null);
            if (replicate == null) {
                log.debug("no replicate [chainTaskId:{}, walletAddress:{}]", chainTaskId, walletAddress);
                continue;
            }
            final boolean isRecoverable = replicate.isRecoverable();
            if (!isRecoverable) {
                log.debug("not recoverable [chainTaskId:{}, walletAddress:{}]", chainTaskId, walletAddress);
                continue;
            }
            final String enclaveChallenge = task.getEnclaveChallenge();
            if (task.isTeeTask() && enclaveChallenge.isEmpty()) {
                log.debug("empty enclave challenge [chainTaskId:{}, walletAddress:{}]", chainTaskId, walletAddress);
                continue;
            }
            final TaskNotificationType taskNotificationType = getTaskNotificationType(task, replicate, blockNumber).orElse(null);
            if (taskNotificationType == null) {
                log.debug("no task notification [chainTaskId:{}, walletAddress:{}]", chainTaskId, walletAddress);
                continue;
            }
            log.debug("task notification type {}", taskNotificationType);

            final TaskNotificationExtra taskNotificationExtra =
                    getTaskNotificationExtra(task, taskNotificationType, walletAddress);

            final TaskNotification taskNotification = TaskNotification.builder()
                    .chainTaskId(chainTaskId)
                    .workersAddress(Collections.singletonList(walletAddress))
                    .taskNotificationType(taskNotificationType)
                    .taskNotificationExtra(taskNotificationExtra)
                    .build();

            // change replicate status
            replicatesService.updateReplicateStatus(
                    chainTaskId, walletAddress, ReplicateStatusUpdate.poolManagerRequest(RECOVERING));
            taskNotifications.add(taskNotification);
        }

        return taskNotifications;
    }

    private TaskNotificationExtra getTaskNotificationExtra(Task task, TaskNotificationType taskNotificationType, String walletAddress) {
        final WorkerpoolAuthorization authorization = signatureService.createAuthorization(
                walletAddress, task.getChainTaskId(), task.getEnclaveChallenge());
        final TaskNotificationExtra taskNotificationExtra = TaskNotificationExtra.builder()
                .workerpoolAuthorization(authorization)
                .build();

        switch (taskNotificationType) {
            case PLEASE_CONTRIBUTE:
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

        boolean beforeContributing = replicate.isBeforeStatus(ReplicateStatus.CONTRIBUTING);
        boolean didReplicateStartContributing = replicate.getLastRelevantStatus() == ReplicateStatus.CONTRIBUTING;
        boolean didReplicateContributeOnChain = replicatesService.didReplicateContributeOnchain(chainTaskId, walletAddress);

        if (beforeContributing) {
            return Optional.of(TaskNotificationType.PLEASE_CONTRIBUTE);
        }

        if (didReplicateStartContributing && !didReplicateContributeOnChain) {
            return Optional.of(TaskNotificationType.PLEASE_CONTRIBUTE);
        }

        if (didReplicateStartContributing && didReplicateContributeOnChain) {
            ReplicateStatusDetails details = new ReplicateStatusDetails(blockNumber);
            final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.poolManagerRequest(CONTRIBUTED, details);
            replicatesService.updateReplicateStatus(chainTaskId, walletAddress, statusUpdate);
        }

        // we read the replicate from db to consider the changes added in the previous case
        Optional<Replicate> oReplicateWithLatestChanges = replicatesService.getReplicate(chainTaskId, walletAddress);
        if (oReplicateWithLatestChanges.isEmpty()) {
            return Optional.empty();
        }

        Replicate replicateWithLatestChanges = oReplicateWithLatestChanges.get();
        boolean didReplicateContribute = replicateWithLatestChanges.getLastRelevantStatus()
                == ReplicateStatus.CONTRIBUTED;

        if (didReplicateContribute) {
            final Optional<ReplicatesList> oReplicatesList = replicatesService.getReplicatesList(chainTaskId);
            if (oReplicatesList.isEmpty()) {
                return Optional.empty();
            }
            if (!taskService.isConsensusReached(oReplicatesList.get())) {
                return Optional.of(TaskNotificationType.PLEASE_WAIT);
            }

            taskUpdateRequestManager.publishRequest(chainTaskId);
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

        boolean isInStatusContributed = replicate.getLastRelevantStatus() == ReplicateStatus.CONTRIBUTED;
        boolean didReplicateStartRevealing = replicate.getLastRelevantStatus() == ReplicateStatus.REVEALING;
        boolean didReplicateRevealOnChain = replicatesService.didReplicateRevealOnchain(chainTaskId, walletAddress);

        if (isInStatusContributed) {
            return Optional.of(TaskNotificationType.PLEASE_REVEAL);
        }

        if (didReplicateStartRevealing && !didReplicateRevealOnChain) {
            return Optional.of(TaskNotificationType.PLEASE_REVEAL);
        }

        if (didReplicateStartRevealing && didReplicateRevealOnChain) {
            ReplicateStatusDetails details = new ReplicateStatusDetails(blockNumber);
            final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.poolManagerRequest(REVEALED, details);
            replicatesService.updateReplicateStatus(chainTaskId, walletAddress, statusUpdate);
            taskUpdateRequestManager.publishRequest(chainTaskId);
        }

        // we read the replicate from db to consider the changes added in the previous case
        Optional<Replicate> oReplicateWithLatestChanges = replicatesService.getReplicate(chainTaskId, walletAddress);

        if (oReplicateWithLatestChanges.isEmpty()) {
            return Optional.empty();
        }
        replicate = oReplicateWithLatestChanges.get();

        boolean didReplicateReveal = replicate.getLastRelevantStatus()
                == ReplicateStatus.REVEALED;

        boolean wasReplicateRequestedToUpload = replicate.getLastRelevantStatus()
                == ReplicateStatus.RESULT_UPLOAD_REQUESTED;

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

        boolean wasReplicateRequestedToUpload = replicate.getLastRelevantStatus() == ReplicateStatus.RESULT_UPLOAD_REQUESTED;
        boolean didReplicateStartUploading = replicate.getLastRelevantStatus() == ReplicateStatus.RESULT_UPLOADING;
        boolean didReplicateUploadWithoutNotifying = replicatesService.isResultUploaded(task.getChainTaskId());
        boolean hasReplicateAlreadyUploaded = replicate.getLastRelevantStatus() == ReplicateStatus.RESULT_UPLOADED;

        if (wasReplicateRequestedToUpload) {
            return Optional.of(TaskNotificationType.PLEASE_UPLOAD);
        }

        if (didReplicateStartUploading && !didReplicateUploadWithoutNotifying) {
            return Optional.of(TaskNotificationType.PLEASE_UPLOAD);
        }

        if (didReplicateStartUploading && didReplicateUploadWithoutNotifying) {
            final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.poolManagerRequest(RESULT_UPLOADED);
            replicatesService.updateReplicateStatus(chainTaskId, walletAddress, statusUpdate);

            taskUpdateRequestManager.publishRequest(chainTaskId);
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

    // region purge locks
    @Override
    public boolean purgeTask(String chainTaskId) {
        taskAccessForNewReplicateLocks.remove(chainTaskId);
        return !taskAccessForNewReplicateLocks.containsKey(chainTaskId);
    }

    @Override
    public void purgeAllTasksData() {
        taskAccessForNewReplicateLocks.clear();
    }
    // endregion
}
