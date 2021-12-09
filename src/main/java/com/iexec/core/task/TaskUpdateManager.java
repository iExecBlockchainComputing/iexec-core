/*
 * Copyright 2021 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.task;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ChainTaskStatus;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.adapter.BlockchainAdapterService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesList;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.event.*;
import com.iexec.core.task.update.TaskUpdateRequestConsumer;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.iexec.core.task.TaskStatus.*;

@Service
@Slf4j
public class TaskUpdateManager implements TaskUpdateRequestConsumer  {
    private final TaskService taskService;
    private final TaskRepository taskRepository;
    private final TaskUpdateRequestManager taskUpdateRequestManager;
    private final IexecHubService iexecHubService;
    private final ReplicatesService replicatesService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final WorkerService workerService;
    private final BlockchainAdapterService blockchainAdapterService;

    public TaskUpdateManager(TaskService taskService,
                             TaskRepository taskRepository,
                             TaskUpdateRequestManager taskUpdateRequestManager,
                             IexecHubService iexecHubService,
                             ReplicatesService replicatesService,
                             ApplicationEventPublisher applicationEventPublisher,
                             WorkerService workerService,
                             BlockchainAdapterService blockchainAdapterService) {
        this.taskService = taskService;
        this.taskRepository = taskRepository;
        this.taskUpdateRequestManager = taskUpdateRequestManager;
        this.iexecHubService = iexecHubService;
        this.replicatesService = replicatesService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.workerService = workerService;
        this.blockchainAdapterService = blockchainAdapterService;
        this.taskUpdateRequestManager.setRequestConsumer(this);
    }

    @Override
    public void onTaskUpdateRequest(String chainTaskId) {
        log.info("Received task update request [chainTaskId:{}]", chainTaskId);
        this.updateTask(chainTaskId);
    }

    public CompletableFuture<Boolean> publishUpdateTaskRequest(String chainTaskId) {
        return taskUpdateRequestManager.publishRequest(chainTaskId);
    }

    @SuppressWarnings("DuplicateBranchesInSwitch")
    void updateTask(String chainTaskId) {
        Optional<Task> optional = taskService.getTaskByChainTaskId(chainTaskId);
        if (optional.isEmpty()) {
            return;
        }
        Task task = optional.get();
        TaskStatus currentStatus = task.getCurrentStatus();

        boolean isFinalDeadlinePossible =
                !TaskStatus.getStatusesWhereFinalDeadlineIsImpossible().contains(currentStatus);
        if (isFinalDeadlinePossible && new Date().after(task.getFinalDeadline())){
            updateTaskStatusAndSave(task, FINAL_DEADLINE_REACHED);
            // Eventually should fire a "final deadline reached" notification to worker,
            // but here let's just trigger an updateTask() leading to a failed status
            // which will itself fire a generic "abort" notification
            publishUpdateTaskRequest(chainTaskId);
            return;
        }

        switch (currentStatus) {
            case RECEIVED:
                received2Initializing(task);
                break;
            case INITIALIZING:
                initializing2Initialized(task);
                break;
            case INITIALIZED:
                initialized2Running(task);
                initializedOrRunning2ContributionTimeout(task);
                break;
            case INITIALIZE_FAILED:
                toFailed(task);
                break;
            case RUNNING:
                running2ConsensusReached(task);
                running2RunningFailed(task);
                initializedOrRunning2ContributionTimeout(task);
                break;
            case RUNNING_FAILED:
                toFailed(task);
                break;
            case CONSENSUS_REACHED:
                consensusReached2AtLeastOneReveal2ResultUploading(task);
                consensusReached2Reopening(task);
                break;
            case CONTRIBUTION_TIMEOUT:
                toFailed(task);
                break;
            case AT_LEAST_ONE_REVEALED:
                requestUpload(task);
                break;
            case REOPENING:
                reopening2Reopened(task);
                break;
            case REOPENED:
                updateTaskStatusAndSave(task, INITIALIZED);
                break;
            case REOPEN_FAILED:
                toFailed(task);
                break;
            case RESULT_UPLOADING:
                resultUploading2Uploaded(task);
                resultUploading2UploadTimeout(task);
                break;
            case RESULT_UPLOADED:
                resultUploaded2Finalizing(task);
                break;
            case RESULT_UPLOAD_TIMEOUT:
                toFailed(task);
                break;
            case FINALIZING:
                finalizing2Finalized2Completed(task);
                break;
            case FINALIZED:
                finalizedToCompleted(task);
                break;
            case FINALIZE_FAILED:
                toFailed(task);
                break;
            case FINAL_DEADLINE_REACHED:
                toFailed(task);
                break;
            case COMPLETED:
            case FAILED:
                break;
        }
    }

    Task updateTaskStatusAndSave(Task task, TaskStatus newStatus) {
        return updateTaskStatusAndSave(task, newStatus, null);
    }

    Task updateTaskStatusAndSave(Task task, TaskStatus newStatus, ChainReceipt chainReceipt) {
        TaskStatus currentStatus = task.getCurrentStatus();
        task.changeStatus(newStatus, chainReceipt);
        Task savedTask = taskRepository.save(task);
        log.info("UpdateTaskStatus suceeded [chainTaskId:{}, currentStatus:{}, newStatus:{}]", task.getChainTaskId(), currentStatus, newStatus);
        return savedTask;
    }

    void received2Initializing(Task task) {
        boolean isCurrentStatusReceived = task.getCurrentStatus().equals(RECEIVED);

        if (!isCurrentStatusReceived) {
            log.error("Cannot initialize task [chainTaskId:{}, currentStatus:{}]",
                    task.getChainTaskId(), task.getCurrentStatus());
            return;
        }

        boolean hasEnoughGas = iexecHubService.hasEnoughGas();
        boolean isTaskUnsetOnChain = iexecHubService.isTaskInUnsetStatusOnChain(task.getChainDealId(), task.getTaskIndex());
        boolean isBeforeContributionDeadline = iexecHubService.isBeforeContributionDeadline(task.getChainDealId());

        if (!hasEnoughGas || !isTaskUnsetOnChain || !isBeforeContributionDeadline) {
            log.error("Cannot initialize task [chainTaskId:{}, hasEnoughGas:{}, "
                            + "isTaskUnsetOnChain:{}, isBeforeContributionDeadline:{}]",
                    task.getChainTaskId(), hasEnoughGas, isTaskUnsetOnChain,
                    isBeforeContributionDeadline);
            return;
        }

        blockchainAdapterService
                .requestInitialize(task.getChainDealId(), task.getTaskIndex())
                .filter(chainTaskId -> chainTaskId.equalsIgnoreCase(task.getChainTaskId()))
                .ifPresentOrElse(chainTaskId -> {
                    log.info("Requested initialize on blockchain [chainTaskId:{}]",
                            task.getChainTaskId());
                    updateTaskStatusAndSave(task, INITIALIZING);
                    //Watch initializing to initialized
                    updateTask(task.getChainTaskId());
                }, () -> {
                    log.error("Failed to request initialize on blockchain [chainTaskId:{}]",
                            task.getChainTaskId());
                    updateTaskStatusAndSave(task, INITIALIZE_FAILED);
                    updateTaskStatusAndSave(task, FAILED);
                });
    }

    void initializing2Initialized(Task task) {
        if (!INITIALIZING.equals(task.getCurrentStatus())){
            return;
        }
        // TODO: the block where initialization happened can be found
        blockchainAdapterService
                .isInitialized(task.getChainTaskId())
                .ifPresentOrElse(isSuccess -> {
                    if (isSuccess != null && isSuccess) {
                        log.info("Initialized on blockchain (tx mined) [chainTaskId:{}]",
                                task.getChainTaskId());
                        //Without receipt, using deal block for initialization block
                        task.setInitializationBlockNumber(task.getDealBlockNumber());
                        updateTaskStatusAndSave(task, INITIALIZED, null);
                        replicatesService.createEmptyReplicateList(task.getChainTaskId());
                        return;
                    }
                    log.error("Initialization failed on blockchain (tx reverted) [chainTaskId:{}]",
                            task.getChainTaskId());
                    updateTaskStatusAndSave(task, INITIALIZE_FAILED);
                    updateTaskStatusAndSave(task, FAILED);
                }, () -> log.error("Unable to check initialization on blockchain " +
                        "(likely too long), should use a detector [chainTaskId:{}]",
                        task.getChainTaskId()));
    }

    void initialized2Running(Task task) {
        String chainTaskId = task.getChainTaskId();

        // We explicitly exclude START_FAILED as it could denote some serious issues
        // The task should not transition to `RUNNING` in this case.
        final ReplicateStatus[] acceptableStatus = new ReplicateStatus[]{
                ReplicateStatus.STARTED,
                ReplicateStatus.APP_DOWNLOADING,
                ReplicateStatus.APP_DOWNLOAD_FAILED,
                ReplicateStatus.APP_DOWNLOADED,
                ReplicateStatus.DATA_DOWNLOADING,
                ReplicateStatus.DATA_DOWNLOAD_FAILED,
                ReplicateStatus.DATA_DOWNLOADED,
                ReplicateStatus.COMPUTING,
                ReplicateStatus.COMPUTE_FAILED,
                ReplicateStatus.COMPUTED,
                ReplicateStatus.CONTRIBUTING,
                ReplicateStatus.CONTRIBUTE_FAILED,
                ReplicateStatus.CONTRIBUTED
        };
        final int nbReplicatesContainingStartingStatus = replicatesService.getNbReplicatesWithLastRelevantStatus(chainTaskId, acceptableStatus);
        boolean condition1 = nbReplicatesContainingStartingStatus > 0;
        boolean condition2 = task.getCurrentStatus().equals(INITIALIZED);

        if (condition1 && condition2) {
            updateTaskStatusAndSave(task, RUNNING);
        }
    }

    public boolean isConsensusReached(Task task) {

        Optional<ChainTask> optional = iexecHubService.getChainTask(task.getChainTaskId());
        if (optional.isEmpty()) return false;

        ChainTask chainTask = optional.get();

        boolean isChainTaskRevealing = chainTask.getStatus().equals(ChainTaskStatus.REVEALING);

        int onChainWinners = chainTask.getWinnerCounter();
        int offChainWinners = isChainTaskRevealing ? replicatesService.getNbValidContributedWinners(task.getChainTaskId(), chainTask.getConsensusValue()) : 0;
        boolean offChainWinnersGreaterOrEqualsOnChainWinners = offChainWinners >= onChainWinners;

        return isChainTaskRevealing && offChainWinnersGreaterOrEqualsOnChainWinners;
    }

    void running2ConsensusReached(Task task) {
        boolean isTaskInRunningStatus = task.getCurrentStatus().equals(RUNNING);
        boolean isConsensusReached = isConsensusReached(task);

        if (isTaskInRunningStatus && isConsensusReached) {
            Optional<ChainTask> optional = iexecHubService.getChainTask(task.getChainTaskId());
            if (optional.isEmpty()) return;
            ChainTask chainTask = optional.get();

            // change the the revealDeadline and consensus of the task from the chainTask info
            task.setRevealDeadline(new Date(chainTask.getRevealDeadline()));
            task.setConsensus(chainTask.getConsensusValue());
            long consensusBlockNumber = iexecHubService.getConsensusBlock(task.getChainTaskId(), task.getInitializationBlockNumber()).getBlockNumber();
            task.setConsensusReachedBlockNumber(consensusBlockNumber);
            updateTaskStatusAndSave(task, CONSENSUS_REACHED);

            applicationEventPublisher.publishEvent(ConsensusReachedEvent.builder()
                    .chainTaskId(task.getChainTaskId())
                    .consensus(task.getConsensus())
                    .blockNumber(task.getConsensusReachedBlockNumber())
                    .build());
        }
    }

    void initializedOrRunning2ContributionTimeout(Task task) {
        boolean isInitializedOrRunningTask = task.getCurrentStatus().equals(INITIALIZED) ||
                task.getCurrentStatus().equals(RUNNING);
        boolean isNowAfterContributionDeadline = task.getContributionDeadline() != null && new Date().after(task.getContributionDeadline());

        if (isInitializedOrRunningTask && isNowAfterContributionDeadline) {
            updateTaskStatusAndSave(task, CONTRIBUTION_TIMEOUT);
            updateTaskStatusAndSave(task, FAILED);
            applicationEventPublisher.publishEvent(ContributionTimeoutEvent.builder()
                    .chainTaskId(task.getChainTaskId())
                    .build());
        }
    }

    /**
     * In TEE mode, when all workers have failed to run the task, we send the new status as soon as possible.
     * It avoids a state within which we know the task won't be updated anymore, but we wait for a timeout.
     * <br>
     * We consider that all workers are in a `RUNNING_FAILED` status
     * when all alive workers have tried to run the task, but they have failed.
     *
     * @param task Task to check and to make become {@link TaskStatus#RUNNING_FAILED}.
     */
    void running2RunningFailed(Task task) {
        boolean isRunningTask = task.getCurrentStatus().equals(RUNNING);
        if (!isRunningTask || !task.isTeeTask()) {
            return;
        }

        final ReplicatesList replicatesList = replicatesService.getReplicatesList(task.getChainTaskId()).orElseThrow();
        final List<Worker> aliveWorkers = workerService.getAliveWorkers();

        final List<Replicate> replicatesOfAliveWorkers = aliveWorkers
                .stream()
                .map(Worker::getWalletAddress)
                .map(replicatesList::getReplicateOfWorker)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        // If at least an alive worker has not run the task, it is not a `RUNNING_FAILURE`.
        final boolean allAliveWorkersTried = replicatesOfAliveWorkers.size() == aliveWorkers.size();

        if (!allAliveWorkersTried) {
            return;
        }

        // If not all alive workers have failed while running the task, that's not a running failure.
        boolean notAllReplicatesFailed = replicatesOfAliveWorkers
                .stream()
                .map(Replicate::getLastRelevantStatus)
                .map(Optional::get)
                .anyMatch(Predicate.not(ReplicateStatus::isFailedBeforeComputed));

        if (notAllReplicatesFailed) {
            return;
        }

        // If all alive workers have failed on this task, its computation should be stopped.
        // It could denote that the task is wrong
        // - e.g. failing script, dataset can't be retrieved, app can't be downloaded, ...
        updateTaskStatusAndSave(task, RUNNING_FAILED);
        updateTaskStatusAndSave(task, FAILED);
        applicationEventPublisher.publishEvent(TaskRunningFailedEvent.builder()
                .chainTaskId(task.getChainTaskId())
                .build());
    }

    void consensusReached2AtLeastOneReveal2ResultUploading(Task task) {
        boolean condition1 = task.getCurrentStatus().equals(CONSENSUS_REACHED);
        boolean condition2 = replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.REVEALED) > 0;

        if (condition1 && condition2) {
            updateTaskStatusAndSave(task, AT_LEAST_ONE_REVEALED);
            requestUpload(task);
        }
    }

    void consensusReached2Reopening(Task task) {
        Date now = new Date();

        boolean isConsensusReachedStatus = task.getCurrentStatus().equals(CONSENSUS_REACHED);
        boolean isAfterRevealDeadline = task.getRevealDeadline() != null && now.after(task.getRevealDeadline());
        boolean hasAtLeastOneReveal = replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.REVEALED) > 0;

        if (!isConsensusReachedStatus || !isAfterRevealDeadline || hasAtLeastOneReveal) {
            return;
        }

        boolean canReopen = iexecHubService.canReopen(task.getChainTaskId());
        boolean hasEnoughGas = iexecHubService.hasEnoughGas();

        if (!canReopen || !hasEnoughGas) {
            return;
        }

        updateTaskStatusAndSave(task, REOPENING);
        //TODO Update reopen call
        Optional<ChainReceipt> optionalChainReceipt = Optional.empty(); //iexecHubService.reOpen(task.getChainTaskId());

        if (optionalChainReceipt.isEmpty()) {
            log.error("Reopen failed [chainTaskId:{}]", task.getChainTaskId());
            updateTaskStatusAndSave(task, REOPEN_FAILED);
            updateTaskStatusAndSave(task, FAILED);
            return;
        }

        reopening2Reopened(task, optionalChainReceipt.get());
    }

    void reopening2Reopened(Task task) {
        reopening2Reopened(task, null);
    }

    void reopening2Reopened(Task task, ChainReceipt chainReceipt) {
        Optional<ChainTask> oChainTask = iexecHubService.getChainTask(task.getChainTaskId());
        if (oChainTask.isEmpty()) {
            return;
        }
        ChainTask chainTask = oChainTask.get();

        // re-initialize the task if it has been reopened
        if (chainTask.getStatus().equals(ChainTaskStatus.ACTIVE)) {

            // set replicates to REVEAL_TIMEOUT
            for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                replicatesService.setRevealTimeoutStatusIfNeeded(task.getChainTaskId(), replicate);
            }

            task.setConsensus(null);
            task.setRevealDeadline(new Date(0));

            updateTaskStatusAndSave(task, REOPENED, chainReceipt);
            updateTaskStatusAndSave(task, INITIALIZED, chainReceipt);
        }
    }

    void resultUploading2Uploaded(Task task) {
        boolean isTaskInResultUploading = task.getCurrentStatus().equals(RESULT_UPLOADING);
        Optional<Replicate> oUploadedReplicate = replicatesService.getReplicateWithResultUploadedStatus(task.getChainTaskId());
        boolean didReplicateUpload = oUploadedReplicate.isPresent();

        if (!isTaskInResultUploading) {
            return;
        }

        if (didReplicateUpload) {
            task.setResultLink(oUploadedReplicate.get().getResultLink());
            task.setChainCallbackData(oUploadedReplicate.get().getChainCallbackData());
            updateTaskStatusAndSave(task, RESULT_UPLOADED);
            resultUploaded2Finalizing(task);
            return;
        }

        String uploadingReplicateAddress = task.getUploadingWorkerWalletAddress();

        if (uploadingReplicateAddress == null || uploadingReplicateAddress.isEmpty()) {
            requestUpload(task);
            return;
        }

        Optional<Replicate> oReplicate = replicatesService.getReplicate(task.getChainTaskId(), uploadingReplicateAddress);

        if (oReplicate.isEmpty()) {
            requestUpload(task);
            return;
        }

        Replicate replicate = oReplicate.get();

        boolean isReplicateUploading = replicate.getCurrentStatus() == ReplicateStatus.RESULT_UPLOADING;
        boolean isReplicateRecoveringToUpload = replicate.getCurrentStatus() == ReplicateStatus.RECOVERING &&
                replicate.getLastRelevantStatus().isPresent() &&
                replicate.getLastRelevantStatus().get() == ReplicateStatus.RESULT_UPLOADING;

        if (!isReplicateUploading && !isReplicateRecoveringToUpload) {
            requestUpload(task);
        }
    }

    void resultUploading2UploadTimeout(Task task) {
        boolean isTaskInResultUploading = task.getCurrentStatus().equals(RESULT_UPLOADING);
        boolean isNowAfterFinalDeadline = task.getFinalDeadline() != null
                && new Date().after(task.getFinalDeadline());

        if (isTaskInResultUploading && isNowAfterFinalDeadline) {
            updateTaskStatusAndSave(task, RESULT_UPLOAD_TIMEOUT);
            applicationEventPublisher.publishEvent(ResultUploadTimeoutEvent.builder()
                    .chainTaskId(task.getChainTaskId())
                    .build());
            updateTaskStatusAndSave(task, FAILED);
        }
    }

    void requestUpload(Task task) {
        boolean isThereAWorkerUploading = replicatesService
                .getNbReplicatesWithCurrentStatus(task.getChainTaskId(),
                        ReplicateStatus.RESULT_UPLOADING,
                        ReplicateStatus.RESULT_UPLOAD_REQUESTED) > 0;

        if (isThereAWorkerUploading) {
            log.info("Upload is requested but an upload is already in process. [chainTaskId: {}]", task.getChainTaskId());
            return;
        }

        Optional<Replicate> optionalReplicate = replicatesService.getRandomReplicateWithRevealStatus(task.getChainTaskId());
        if (optionalReplicate.isPresent()) {
            Replicate replicate = optionalReplicate.get();

            // save in the task the workerWallet that is in charge of uploading the result
            task.setUploadingWorkerWalletAddress(replicate.getWalletAddress());
            updateTaskStatusAndSave(task, RESULT_UPLOADING);
            replicatesService.updateReplicateStatus(task.getChainTaskId(), replicate.getWalletAddress(),
                    ReplicateStatus.RESULT_UPLOAD_REQUESTED);

            applicationEventPublisher.publishEvent(new PleaseUploadEvent(task.getChainTaskId(), replicate.getWalletAddress()));
        }
    }

    void resultUploaded2Finalizing(Task task) {
        boolean isTaskInResultUploaded = task.getCurrentStatus().equals(RESULT_UPLOADED);
        boolean canFinalize = iexecHubService.canFinalize(task.getChainTaskId());

        Optional<ChainTask> optional = iexecHubService.getChainTask(task.getChainTaskId());
        if (optional.isEmpty()) {
            return;
        }
        ChainTask chainTask = optional.get();

        int onChainReveal = chainTask.getRevealCounter();
        int offChainReveal = replicatesService.getNbReplicatesContainingStatus(task.getChainTaskId(), ReplicateStatus.REVEALED);
        boolean offChainRevealEqualsOnChainReveal = offChainReveal == onChainReveal;

        if (!isTaskInResultUploaded || !canFinalize || !offChainRevealEqualsOnChainReveal) {
            return;
        }

        if (!iexecHubService.hasEnoughGas()) {
            return;
        }

        blockchainAdapterService
                .requestFinalize(task.getChainTaskId(), task.getResultLink(),
                        task.getChainCallbackData())
                .ifPresentOrElse(chainTaskId -> {
                    log.info("Requested finalize on blockchain [chainTaskId:{}]",
                            task.getChainTaskId());
                    updateTaskStatusAndSave(task, FINALIZING);
                    //Watch finalizing to finalized
                    updateTask(task.getChainTaskId());
                }, () -> {
                    log.error("Failed to request finalize on blockchain [chainTaskId:{}]",
                            task.getChainTaskId());
                    updateTaskStatusAndSave(task, FINALIZE_FAILED);
                    updateTaskStatusAndSave(task, FAILED);
                });
    }

    void finalizing2Finalized2Completed(Task task) {
        blockchainAdapterService
                .isFinalized(task.getChainTaskId())
                .ifPresentOrElse(isSuccess -> {
                    if (isSuccess != null && isSuccess) {
                        log.info("Finalized on blockchain (tx mined)" +
                                "[chainTaskId:{}]", task.getChainTaskId());
                        updateTaskStatusAndSave(task, FINALIZED, null);
                        finalizedToCompleted(task);
                        return;
                    }
                    log.error("Finalization failed on blockchain (tx reverted)" +
                            "[chainTaskId:{}]", task.getChainTaskId());
                    updateTaskStatusAndSave(task, FINALIZE_FAILED);
                    updateTaskStatusAndSave(task, FAILED);
                }, () -> log.error("Unable to check finalization on blockchain " +
                        "(likely too long), should use a detector " +
                        "[chainTaskId:{}]", task.getChainTaskId()));
    }

    void finalizedToCompleted(Task task) {
        if (!task.getCurrentStatus().equals(FINALIZED)) {
            return;
        }
        updateTaskStatusAndSave(task, COMPLETED);
        applicationEventPublisher.publishEvent(new TaskCompletedEvent(task));
    }

    void toFailed(Task task) {
        updateTaskStatusAndSave(task, FAILED);
        applicationEventPublisher.publishEvent(new TaskFailedEvent(task.getChainTaskId()));
    }
}
