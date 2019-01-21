package com.iexec.core.task;

import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ChainTaskStatus;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.event.ConsensusReachedEvent;
import com.iexec.core.task.event.ContributionTimeoutEvent;
import com.iexec.core.task.event.PleaseUploadEvent;
import com.iexec.core.task.event.TaskCompletedEvent;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.iexec.core.task.TaskStatus.*;

@Slf4j
@Service
public class TaskService {

    private TaskRepository taskRepository;
    private WorkerService workerService;
    private IexecHubService iexecHubService;
    private ReplicatesService replicatesService;
    private ApplicationEventPublisher applicationEventPublisher;

    public TaskService(TaskRepository taskRepository,
                       WorkerService workerService,
                       IexecHubService iexecHubService,
                       ReplicatesService replicatesService,
                       ApplicationEventPublisher applicationEventPublisher) {
        this.taskRepository = taskRepository;
        this.workerService = workerService;
        this.iexecHubService = iexecHubService;
        this.replicatesService = replicatesService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public Optional<Task> addTask(String chainDealId, int taskIndex, String imageName, String commandLine, int trust, Date timeRef, String tag) {
        if (getTasksByChainDealIdAndTaskIndex(chainDealId, taskIndex).isEmpty()) {
            log.info("Add new task [chainDealId:{}, taskIndex:{}, imageName:{}, commandLine:{}, trust:{}]",
                    chainDealId, taskIndex, imageName, commandLine, trust);
            return Optional.of(taskRepository.save(new Task(chainDealId, taskIndex, imageName, commandLine, trust, timeRef, tag)));
        }
        log.info("Task already added [chainDealId:{}, taskIndex:{}, imageName:{}, commandLine:{}, trust:{}]",
                chainDealId, taskIndex, imageName, commandLine, trust);
        return Optional.empty();
    }

    public Optional<Task> getTaskByChainTaskId(String chainTaskId) {
        return taskRepository.findByChainTaskId(chainTaskId);
    }

    public List<Task> findByCurrentStatus(TaskStatus status) {
        return taskRepository.findByCurrentStatus(status);
    }

    public List<Task> findByCurrentStatus(List<TaskStatus> statusList) {
        return taskRepository.findByCurrentStatus(statusList);
    }

    private List<Task> getTasksByChainDealIdAndTaskIndex(String chainDealId, int taskIndex) {
        return taskRepository.findByChainDealIdAndTaskIndex(chainDealId, taskIndex);
    }

    private List<Task> getAllRunningTasks() {
        return taskRepository.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING));
    }

    // in case the task has been modified between reading and writing it, it is retried up to 5 times
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 5)
    Optional<Replicate> getAvailableReplicate(String walletAddress) {
        // return empty if the worker is not registered
        Optional<Worker> optional = workerService.getWorker(walletAddress);
        if (!optional.isPresent()) {
            return Optional.empty();
        }

        // return empty if there is no task to contribute
        List<Task> runningTasks = getAllRunningTasks();
        if (runningTasks.isEmpty()) {
            return Optional.empty();
        }

        // return empty if the worker already has enough running tasks
        if (!workerService.canAcceptMoreWorks(walletAddress)) {
            return Optional.empty();
        }

        for (Task task : runningTasks) {
            String chainTaskId = task.getChainTaskId();
            if (!replicatesService.hasWorkerAlreadyParticipated(chainTaskId, walletAddress) &&
                    replicatesService.moreReplicatesNeeded(chainTaskId, task.getNumWorkersNeeded(), task.getTimeRef())) {
                replicatesService.addNewReplicate(chainTaskId, walletAddress);
                workerService.addChainTaskIdToWorker(chainTaskId, walletAddress);
                return replicatesService.getReplicate(chainTaskId, walletAddress);
            }
        }

        return Optional.empty();
    }

    public void tryToMoveTaskToNextStatus(Task task) {
        switch (task.getCurrentStatus()) {
            case RECEIVED:
                received2Initialized(task);
                break;
            case INITIALIZED:
                initialized2Running(task);
                initializedOrRunning2ContributionTimeout(task);
                break;
            case RUNNING:
                running2ConsensusReached(task);
                initializedOrRunning2ContributionTimeout(task);
                break;
            case CONSENSUS_REACHED:
                consensusReached2AtLeastOneReveal2UploadRequested(task);
                break;
            case RESULT_UPLOAD_REQUESTED:
                uploadRequested2UploadingResult(task);
                break;
            case RESULT_UPLOADING:
                resultUploading2Uploaded(task);
                break;
            case RESULT_UPLOADED:
                updateResultUploaded2Finalized(task);
                break;
        }
    }

    public void reOpenTask(Task task) {
        boolean canReopen = iexecHubService.canReopen(task.getChainTaskId());
        boolean hasEnoughGas = iexecHubService.hasEnoughGas();

        if (canReopen) {
            if (!hasEnoughGas) {
                return;
            }

            updateTaskStatusAndSave(task, TaskStatus.REOPENING);
            boolean isReopened = iexecHubService.reOpen(task.getChainTaskId());

            if (isReopened) {
                task.setConsensus(null);
                task.setRevealDeadline(new Date(0));
                updateTaskStatusAndSave(task, TaskStatus.REOPENED);
                updateTaskStatusAndSave(task, TaskStatus.INITIALIZED);
            } else {
                updateTaskStatusAndSave(task, TaskStatus.REOPEN_FAILED);
            }
        }

    }

    private Task updateTaskStatusAndSave(Task task, TaskStatus newStatus) {
        TaskStatus currentStatus = task.getCurrentStatus();
        task.changeStatus(newStatus);
        Task savedTask = taskRepository.save(task);
        log.info("UpdateTaskStatus suceeded [taskId:{}, currentStatus:{}, newStatus:{}]", task.getId(), currentStatus, newStatus);
        return savedTask;
    }

    private void received2Initialized(Task task) {
        boolean isChainTaskIdEmpty = task.getChainTaskId() != null && task.getChainTaskId().isEmpty();
        boolean isCurrentStatusReceived = task.getCurrentStatus().equals(RECEIVED);

        if (isChainTaskIdEmpty && isCurrentStatusReceived) {

            boolean canInitialize = iexecHubService.canInitialize(task.getChainDealId(), task.getTaskIndex());
            boolean hasEnoughGas = iexecHubService.hasEnoughGas();

            if (!canInitialize || !hasEnoughGas){
                return;
            }

            updateTaskStatusAndSave(task, INITIALIZING);

            String chainTaskId = iexecHubService.initialize(task.getChainDealId(), task.getTaskIndex());
            if (!chainTaskId.isEmpty()) {
                Optional<ChainTask> optional = iexecHubService.getChainTask(chainTaskId);
                if (!optional.isPresent()) {
                    return;
                }
                ChainTask chainTask = optional.get();

                task.setChainTaskId(chainTaskId);
                task.setContributionDeadline(new Date(chainTask.getContributionDeadline()));
                task.setFinalDeadline(new Date(chainTask.getFinalDeadline()));
                //TODO Put other fields?

                updateTaskStatusAndSave(task, INITIALIZED);
                replicatesService.createEmptyReplicateList(chainTaskId);
            } else {
                updateTaskStatusAndSave(task, INITIALIZE_FAILED);
            }
        }
    }

    private void initialized2Running(Task task) {
        String chainTaskId = task.getChainTaskId();
        boolean condition1 = replicatesService.getNbReplicatesWithCurrentStatus(chainTaskId, ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED) > 0;
        boolean condition2 = replicatesService.getNbReplicatesWithCurrentStatus(chainTaskId, ReplicateStatus.COMPUTED) < task.getNumWorkersNeeded();
        boolean condition3 = task.getCurrentStatus().equals(INITIALIZED);

        if (condition1 && condition2 && condition3) {
            updateTaskStatusAndSave(task, RUNNING);
        }
    }


    private void running2ConsensusReached(Task task) {
        boolean isTaskInRunningStatus = task.getCurrentStatus().equals(RUNNING);

        Optional<ChainTask> optional = iexecHubService.getChainTask(task.getChainTaskId());
        if (!optional.isPresent()) {
            return;
        }
        ChainTask chainTask = optional.get();

        boolean isChainTaskRevealing = chainTask.getStatus().equals(ChainTaskStatus.REVEALING);

        int onChainWinners = chainTask.getWinnerCounter();
        int offChainWinners = replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.CONTRIBUTED);
        boolean offChainWinnersEqualsOnChainWinners = offChainWinners == onChainWinners;

        if (isTaskInRunningStatus && isChainTaskRevealing && offChainWinnersEqualsOnChainWinners) {

            // change the the revealDeadline and consensus of the task from the chainTask info
            task.setRevealDeadline(new Date(chainTask.getRevealDeadline()));
            task.setConsensus(chainTask.getConsensusValue());
            updateTaskStatusAndSave(task, CONSENSUS_REACHED);

            applicationEventPublisher.publishEvent(ConsensusReachedEvent.builder()
                    .chainTaskId(task.getChainTaskId())
                    .consensus(task.getConsensus())
                    .build());
        }
    }

    private void initializedOrRunning2ContributionTimeout(Task task) {
        boolean isInitializedOrRunningTask = task.getCurrentStatus().equals(INITIALIZED) ||
                task.getCurrentStatus().equals(RUNNING);
        boolean isNowAfterContributionDeadline = task.getContributionDeadline() != null && new Date().after(task.getContributionDeadline());

        Optional<ChainTask> optional = iexecHubService.getChainTask(task.getChainTaskId());
        if (!optional.isPresent()) {
            return;
        }
        ChainTask chainTask = optional.get();

        boolean isChainTaskActive = chainTask.getStatus() != null && chainTask.getStatus().equals(ChainTaskStatus.ACTIVE);

        if (isInitializedOrRunningTask && isChainTaskActive && isNowAfterContributionDeadline) {
            updateTaskStatusAndSave(task, CONTRIBUTION_TIMEOUT);
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

    private void uploadRequested2UploadingResult(Task task) {
        boolean condition1 = task.getCurrentStatus().equals(TaskStatus.RESULT_UPLOAD_REQUESTED);
        boolean condition2 = replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.RESULT_UPLOADING) > 0;

        if (condition1 && condition2) {
            updateTaskStatusAndSave(task, RESULT_UPLOADING);
        }
    }

    private void resultUploading2Uploaded(Task task) {
        boolean condition1 = task.getCurrentStatus().equals(TaskStatus.RESULT_UPLOADING);
        boolean condition2 = replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.RESULT_UPLOADED) > 0;

        if (condition1 && condition2) {
            updateTaskStatusAndSave(task, RESULT_UPLOADED);
            updateResultUploaded2Finalized(task);
        } else if (replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.RESULT_UPLOAD_REQUEST_FAILED) > 0 &&
                replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.RESULT_UPLOADING) == 0) {
            // need to request upload again
            requestUpload(task);
        }
    }

    private void requestUpload(Task task) {

        Optional<Replicate> optionalReplicate = replicatesService.getReplicateWithRevealStatus(task.getChainTaskId());
        if (optionalReplicate.isPresent()) {
            Replicate replicate = optionalReplicate.get();


            // save in the task the workerWallet that is in charge of uploading the result
            task.setUploadingWorkerWalletAddress(replicate.getWalletAddress());
            updateTaskStatusAndSave(task, RESULT_UPLOAD_REQUESTED);
            
            applicationEventPublisher.publishEvent(new PleaseUploadEvent(task.getChainTaskId(), replicate.getWalletAddress()));
        }
    }

    private void updateResultUploaded2Finalized(Task task) {
        boolean isTaskInResultUploaded = task.getCurrentStatus().equals(RESULT_UPLOADED);
        boolean canFinalize = iexecHubService.canFinalize(task.getChainTaskId());

        Optional<ChainTask> optional = iexecHubService.getChainTask(task.getChainTaskId());
        if (!optional.isPresent()) {
            return;
        }
        ChainTask chainTask = optional.get();

        int onChainReveal = chainTask.getRevealCounter();
        int offChainReveal = replicatesService.getNbReplicatesContainingStatus(task.getChainTaskId(), ReplicateStatus.REVEALED);
                //+ replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.RESULT_UPLOADED);
        boolean offChainRevealEqualsOnChainReveal = offChainReveal == onChainReveal;

        if (isTaskInResultUploaded && canFinalize && offChainRevealEqualsOnChainReveal) {
            if (!iexecHubService.hasEnoughGas()) {
                return;
            }
            updateTaskStatusAndSave(task, FINALIZING);
            boolean isFinalized = iexecHubService.finalizeTask(task.getChainTaskId(), "GET /results/" + task.getChainTaskId());

            if (isFinalized) {
                updateTaskStatusAndSave(task, FINALIZED);
                updateFromFinalizedToCompleted(task);
            } else {
                updateTaskStatusAndSave(task, FINALIZE_FAILED);
            }
        }
    }

    private void updateFromFinalizedToCompleted(Task task) {
        if (task.getCurrentStatus().equals(FINALIZED)) {
            updateTaskStatusAndSave(task, COMPLETED);

            applicationEventPublisher.publishEvent(new TaskCompletedEvent(task));
        }
    }

}
