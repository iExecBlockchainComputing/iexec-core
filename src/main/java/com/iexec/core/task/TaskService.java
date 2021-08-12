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

package com.iexec.core.task;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ChainTaskStatus;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.chain.adapter.BlockchainAdapterService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.event.*;
import com.iexec.core.task.update.TaskUpdateRequestConsumer;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.iexec.core.task.TaskStatus.*;

@Slf4j
@Service
public class TaskService implements TaskUpdateRequestConsumer {

    private final ConcurrentHashMap<String, Boolean>
            taskAccessForNewReplicateLock = new ConcurrentHashMap<>();

    private final TaskRepository taskRepository;
    private final TaskUpdateRequestManager taskUpdateRequestManager;
    private final IexecHubService iexecHubService;
    private final ReplicatesService replicatesService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Web3jService web3jService;
    private final BlockchainAdapterService blockchainAdapterService;

    public TaskService(
        TaskRepository taskRepository,
        TaskUpdateRequestManager taskUpdateRequestManager,
        IexecHubService iexecHubService,
        ReplicatesService replicatesService,
        ApplicationEventPublisher applicationEventPublisher,
        Web3jService web3jService,
        BlockchainAdapterService blockchainAdapterService
    ) {
        this.taskRepository = taskRepository;
        this.taskUpdateRequestManager = taskUpdateRequestManager;
        this.iexecHubService = iexecHubService;
        this.replicatesService = replicatesService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.web3jService = web3jService;
        this.blockchainAdapterService = blockchainAdapterService;
        this.taskUpdateRequestManager.setRequestConsumer(this);
    }

    /**
     * Save task in database if it does not
     * already exist.
     *
     * @param chainDealId
     * @param taskIndex
     * @param dealBlockNumber
     * @param imageName
     * @param commandLine
     * @param trust
     * @param maxExecutionTime
     * @param tag
     * @param contributionDeadline
     * @param finalDeadline
     * @return optional containing the saved
     * task, {@link Optional#empty()} otherwise.
     */
    public Optional<Task> addTask(
            String chainDealId,
            int taskIndex,
            long dealBlockNumber,
            String imageName,
            String commandLine,
            int trust,
            long maxExecutionTime,
            String tag,
            Date contributionDeadline,
            Date finalDeadline
    ) {
        return taskRepository
                .findByChainDealIdAndTaskIndex(chainDealId, taskIndex)
                .<Optional<Task>>map((task) -> {
                        log.info("Task already added [chainDealId:{}, taskIndex:{}, " +
                                "imageName:{}, commandLine:{}, trust:{}]", chainDealId,
                                taskIndex, imageName, commandLine, trust);
                        return Optional.empty();
                })
                .orElseGet(() -> {
                        Task newTask = new Task(chainDealId, taskIndex, imageName,
                                commandLine, trust, maxExecutionTime, tag);
                        newTask.setDealBlockNumber(dealBlockNumber);
                        newTask.setFinalDeadline(finalDeadline);
                        newTask.setContributionDeadline(contributionDeadline);
                        newTask = taskRepository.save(newTask);
                        log.info("Added new task [chainDealId:{}, taskIndex:{}, imageName:{}, " +
                                "commandLine:{}, trust:{}, chainTaskId:{}]", chainDealId,
                                taskIndex, imageName, commandLine, trust, newTask.getChainTaskId());
                        return Optional.of(newTask);
                });
    }

    public Optional<Task> getTaskByChainTaskId(String chainTaskId) {
        return taskRepository.findByChainTaskId(chainTaskId);
    }

    public List<Task> getTasksByChainTaskIds(List<String> chainTaskIds) {
        return taskRepository.findByChainTaskId(chainTaskIds);
    }

    public List<Task> findByCurrentStatus(TaskStatus status) {
        return taskRepository.findByCurrentStatus(status);
    }

    public List<Task> findByCurrentStatus(List<TaskStatus> statusList) {
        return taskRepository.findByCurrentStatus(statusList);
    }

    public List<Task> getInitializedOrRunningTasks() {
        return taskRepository.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING));
    }

    public List<Task> getTasksInNonFinalStatuses() {
        return taskRepository.findByCurrentStatusNotIn(TaskStatus.getFinalStatuses());
    }

    public List<Task> getTasksWhereFinalDeadlineIsPossible() {
        return taskRepository.findByCurrentStatusNotIn(TaskStatus.getStatusesWhereFinalDeadlineIsImpossible());
    }

    public List<String> getChainTaskIdsOfTasksExpiredBefore(Date expirationDate) {
        return taskRepository.findChainTaskIdsByFinalDeadlineBefore(expirationDate)
                .stream()
                .map(Task::getChainTaskId)
                .collect(Collectors.toList());
    }

    /**
     * An initializable task is in RECEIVED or
     * INITIALIZED status and has a contribution
     * deadline that is still in the future.
     *
     * @return list of initializable tasks
     */
    public List<Task> getInitializableTasks() {
        return taskRepository
                .findByCurrentStatusInAndContributionDeadlineAfter(
                        List.of(RECEIVED, INITIALIZING), new Date());
    }

    public boolean isConsensusReached(Task task) {

        Optional<ChainTask> optional = iexecHubService.getChainTask(task.getChainTaskId());
        if (!optional.isPresent()) return false;

        ChainTask chainTask = optional.get();

        boolean isChainTaskRevealing = chainTask.getStatus().equals(ChainTaskStatus.REVEALING);

        int onChainWinners = chainTask.getWinnerCounter();
        int offChainWinners = isChainTaskRevealing ? replicatesService.getNbValidContributedWinners(task.getChainTaskId(), chainTask.getConsensusValue()) : 0;
        boolean offChainWinnersGreaterOrEqualsOnChainWinners = offChainWinners >= onChainWinners;

        return isChainTaskRevealing && offChainWinnersGreaterOrEqualsOnChainWinners;
    }

    public boolean isExpired(String chainTaskId) {
        Date finalDeadline = getTaskFinalDeadline(chainTaskId);
        return finalDeadline != null && finalDeadline.before(new Date());
    }

    public Date getTaskFinalDeadline(String chainTaskId) {
        return getTaskByChainTaskId(chainTaskId)
                .map(Task::getFinalDeadline)
                .orElse(null);
    }

    /**
     * Async method for requesting task update
     * @param chainTaskId
     * @return
     */
    public CompletableFuture<Boolean> updateTask(String chainTaskId) {
        return taskUpdateRequestManager.publishRequest(chainTaskId);
    }

    /**
     * Async called when a task update request is received
     * @param chainTaskId
     */
    @Override
    public void onTaskUpdateRequest(String chainTaskId) {
        log.info("Received task update request [chainTaskId:{}]", chainTaskId);
        this.updateTaskRunnable(chainTaskId);
    }

    void updateTaskRunnable(String chainTaskId) {
        Optional<Task> optional = getTaskByChainTaskId(chainTaskId);
        if (!optional.isPresent()) {
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
            updateTask(chainTaskId);
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
                initializedOrRunning2ContributionTimeout(task);
                break;
            case CONSENSUS_REACHED:
                consensusReached2AtLeastOneReveal2UploadRequested(task);
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
            case RESULT_UPLOAD_REQUESTED:
                uploadRequested2UploadingResult(task);
                uploadRequested2UploadRequestTimeout(task);
                break;
            case RESULT_UPLOADING:
                resultUploading2Uploaded(task);
                resultUploading2UploadTimeout(task);
                break;
            case RESULT_UPLOADED:
                resultUploaded2Finalizing(task);
                break;
            case RESULT_UPLOAD_REQUEST_TIMEOUT:
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

    private Task updateTaskStatusAndSave(Task task, TaskStatus newStatus) {
        return updateTaskStatusAndSave(task, newStatus, null);
    }

    private Task updateTaskStatusAndSave(Task task, TaskStatus newStatus, ChainReceipt chainReceipt) {
        TaskStatus currentStatus = task.getCurrentStatus();
        task.changeStatus(newStatus, chainReceipt);
        Task savedTask = taskRepository.save(task);
        log.info("UpdateTaskStatus suceeded [chainTaskId:{}, currentStatus:{}, newStatus:{}]", task.getChainTaskId(), currentStatus, newStatus);
        return savedTask;
    }

    private void received2Initializing(Task task) {
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
                    log.info("Requested initialize on blockchain " +
                            "[chainTaskId:{}]", task.getChainTaskId());
                    updateTaskStatusAndSave(task, INITIALIZING);
                    //Watch initializing to initialized
                    updateTaskRunnable(task.getChainTaskId());
                }, () -> {
                    log.error("Failed to request initialize on blockchain " +
                            "[chainTaskId:{}]", task.getChainTaskId());
                    updateTaskStatusAndSave(task, INITIALIZE_FAILED);
                    updateTaskStatusAndSave(task, FAILED);
                });
    }

    private void initializing2Initialized(Task task) {
        // TODO: the block where initialization happened can be found
        blockchainAdapterService
                .isInitialized(task.getChainTaskId())
                .ifPresentOrElse(isSuccess -> {
                    if (isSuccess.equals(Boolean.TRUE)) {
                        log.info("Initialized on blockchain (tx mined)" +
                                "[chainTaskId:{}]", task.getChainTaskId());
                        //Without receipt, using deal block for initialization block
                        task.setInitializationBlockNumber(task.getDealBlockNumber());
                        updateTaskStatusAndSave(task, INITIALIZED, null);
                        replicatesService.createEmptyReplicateList(task.getChainTaskId());
                        return;
                    }
                    log.error("Initialization failed on blockchain (tx reverted)" +
                            "[chainTaskId:{}]", task.getChainTaskId());
                    updateTaskStatusAndSave(task, INITIALIZE_FAILED);
                    updateTaskStatusAndSave(task, FAILED);
                }, () -> log.error("Unable to check initialization on blockchain " +
                        "(likely too long), should use a detector " +
                        "[chainTaskId:{}]", task.getChainTaskId()));
    }

    private void initialized2Running(Task task) {
        String chainTaskId = task.getChainTaskId();
        boolean condition1 = replicatesService.getNbReplicatesWithCurrentStatus(chainTaskId, ReplicateStatus.STARTING, ReplicateStatus.COMPUTED) > 0;
        boolean condition2 = task.getCurrentStatus().equals(INITIALIZED);

        if (condition1 && condition2) {
            updateTaskStatusAndSave(task, RUNNING);
        }
    }

    private void running2ConsensusReached(Task task) {
        boolean isTaskInRunningStatus = task.getCurrentStatus().equals(RUNNING);
        boolean isConsensusReached = isConsensusReached(task);

        if (isTaskInRunningStatus && isConsensusReached) {
            Optional<ChainTask> optional = iexecHubService.getChainTask(task.getChainTaskId());
            if (!optional.isPresent()) return;
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

    private void initializedOrRunning2ContributionTimeout(Task task) {
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

    private void consensusReached2AtLeastOneReveal2UploadRequested(Task task) {
        boolean condition1 = task.getCurrentStatus().equals(CONSENSUS_REACHED);
        boolean condition2 = replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.REVEALED) > 0;

        if (condition1 && condition2) {
            updateTaskStatusAndSave(task, AT_LEAST_ONE_REVEALED);
            requestUpload(task);
        }
    }

    public void consensusReached2Reopening(Task task) {
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

        if (!optionalChainReceipt.isPresent()) {
            log.error("Reopen failed [chainTaskId:{}]", task.getChainTaskId());
            updateTaskStatusAndSave(task, REOPEN_FAILED);
            updateTaskStatusAndSave(task, FAILED);
            return;
        }

        reopening2Reopened(task, optionalChainReceipt.get());
    }

    public void reopening2Reopened(Task task) {
        reopening2Reopened(task, null);
    }

    public void reopening2Reopened(Task task, ChainReceipt chainReceipt) {
        Optional<ChainTask> oChainTask = iexecHubService.getChainTask(task.getChainTaskId());
        if (!oChainTask.isPresent()) {
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

    private void uploadRequested2UploadingResult(Task task) {
        boolean isTaskInUploadRequested = task.getCurrentStatus().equals(RESULT_UPLOAD_REQUESTED);

        boolean isThereAWorkerUploading =
                replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(),
                ReplicateStatus.RESULT_UPLOADING) > 0;

        boolean isThereAWorkerRequestedToUpload =
                replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(),
                ReplicateStatus.RESULT_UPLOAD_REQUESTED) > 0;

        if (!isTaskInUploadRequested) {
            return;
        }

        if (isThereAWorkerUploading) {
            updateTaskStatusAndSave(task, RESULT_UPLOADING);
            return;
        }

        if (!isThereAWorkerRequestedToUpload) {
            requestUpload(task);
        }
    }

    private void uploadRequested2UploadRequestTimeout(Task task) {
        boolean isTaskInUploadRequested = task.getCurrentStatus().equals(RESULT_UPLOAD_REQUESTED);
        boolean isNowAfterFinalDeadline = task.getFinalDeadline() != null
                                        && new Date().after(task.getFinalDeadline());

        if (isTaskInUploadRequested && isNowAfterFinalDeadline) {
            updateTaskStatusAndSave(task, RESULT_UPLOAD_REQUEST_TIMEOUT);
            applicationEventPublisher.publishEvent(ResultUploadTimeoutEvent.builder()
                    .chainTaskId(task.getChainTaskId())
                    .build());
            updateTaskStatusAndSave(task, FAILED);
        }
    }

    private void resultUploading2Uploaded(Task task) {
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

        if (!oReplicate.isPresent()) {
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

    private void resultUploading2UploadTimeout(Task task) {
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

    private void requestUpload(Task task) {

        Optional<Replicate> optionalReplicate = replicatesService.getRandomReplicateWithRevealStatus(task.getChainTaskId());
        if (optionalReplicate.isPresent()) {
            Replicate replicate = optionalReplicate.get();

            // save in the task the workerWallet that is in charge of uploading the result
            task.setUploadingWorkerWalletAddress(replicate.getWalletAddress());
            updateTaskStatusAndSave(task, RESULT_UPLOAD_REQUESTED);
            replicatesService.updateReplicateStatus(task.getChainTaskId(), replicate.getWalletAddress(),
                    ReplicateStatus.RESULT_UPLOAD_REQUESTED);

            applicationEventPublisher.publishEvent(new PleaseUploadEvent(task.getChainTaskId(), replicate.getWalletAddress()));
        }
    }

    private void resultUploaded2Finalizing(Task task) {
        boolean isTaskInResultUploaded = task.getCurrentStatus().equals(RESULT_UPLOADED);
        boolean canFinalize = iexecHubService.canFinalize(task.getChainTaskId());

        Optional<ChainTask> optional = iexecHubService.getChainTask(task.getChainTaskId());
        if (!optional.isPresent()) {
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
                    log.info("Requested finalize on blockchain " +
                            "[chainTaskId:{}]", task.getChainTaskId());
                    updateTaskStatusAndSave(task, FINALIZING);
                    //Watch finalizing to finalized
                    updateTaskRunnable(task.getChainTaskId());
                }, () -> {
                    log.error("Failed to request finalize on blockchain " +
                            "[chainTaskId:{}]", task.getChainTaskId());
                    updateTaskStatusAndSave(task, FINALIZE_FAILED);
                    updateTaskStatusAndSave(task, FAILED);
                });
    }

    private void finalizing2Finalized2Completed(Task task) {
        blockchainAdapterService
                .isFinalized(task.getChainTaskId())
                .ifPresentOrElse(isSuccess -> {
                    if (isSuccess.equals(Boolean.TRUE)) {
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

    private void finalizedToCompleted(Task task) {
        if (!task.getCurrentStatus().equals(FINALIZED)) {
            return;
        }
        updateTaskStatusAndSave(task, COMPLETED);
        applicationEventPublisher.publishEvent(new TaskCompletedEvent(task));
    }

    private void toFailed(Task task) {
        updateTaskStatusAndSave(task, FAILED);
        applicationEventPublisher.publishEvent(new TaskFailedEvent(task.getChainTaskId()));
    }
    public void initializeTaskAccessForNewReplicateLock(String chainTaskId) {
        taskAccessForNewReplicateLock.putIfAbsent(chainTaskId, false);
    }

    public Boolean isTaskBeingAccessedForNewReplicate(String chainTaskId) {
        return taskAccessForNewReplicateLock.get(chainTaskId);
    }

    public void lockTaskAccessForNewReplicate(String chainTaskId) {
        setTaskAccessForNewReplicateLock(chainTaskId, true);
    }

    public void unlockTaskAccessForNewReplicate(String chainTaskId) {
        setTaskAccessForNewReplicateLock(chainTaskId, false);
    }

    private void setTaskAccessForNewReplicateLock(String chainTaskId, boolean isTaskBeingAccessedForNewReplicate) {
        taskAccessForNewReplicateLock.replace(chainTaskId, isTaskBeingAccessedForNewReplicate);
    }


}
