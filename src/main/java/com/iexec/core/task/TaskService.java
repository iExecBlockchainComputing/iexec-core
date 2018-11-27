package com.iexec.core.task;

import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ChainTaskStatus;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.result.TaskNotification;
import com.iexec.common.result.TaskNotificationType;
import com.iexec.core.chain.CredibilityMap;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.pubsub.NotificationService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicateUpdatedEvent;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.iexec.core.chain.ContributionUtils.trustToCredibility;
import static com.iexec.core.task.TaskStatus.*;
import static com.iexec.core.utils.DateTimeUtils.now;

@Slf4j
@Service
public class TaskService {

    private TaskRepository taskRepository;
    private WorkerService workerService;
    private NotificationService notificationService;
    private IexecHubService iexecHubService;
    private ReplicatesService replicatesService;

    public TaskService(TaskRepository taskRepository,
                       WorkerService workerService,
                       NotificationService notificationService,
                       IexecHubService iexecHubService,
                       ReplicatesService replicatesService) {
        this.taskRepository = taskRepository;
        this.workerService = workerService;
        this.notificationService = notificationService;
        this.iexecHubService = iexecHubService;
        this.replicatesService = replicatesService;
    }

    public Task addTask(String dappName, String commandLine, int trust, String chainTaskId) {
        log.info("Adding new task [commandLine:{}, trust:{}]", commandLine, trust);
        return taskRepository.save(new Task(dappName, commandLine, trust, chainTaskId));
    }

    public Optional<Task> getTaskByChainTaskId(String chainTaskId) {
        return taskRepository.findByChainTaskId(chainTaskId);
    }

    public List<Task> getTasksByIds(List<String> ids) {
        return taskRepository.findById(ids);
    }

    public List<Task> getTasksByChainTaskIds(List<String> chainTaskIds) {
        return taskRepository.findByChainTaskId(chainTaskIds);
    }

    public List<Task> findByCurrentStatus(TaskStatus status) {
        return taskRepository.findByCurrentStatus(status);
    }

    public List<Task> getAllRunningTasks() {
        return taskRepository.findByCurrentStatus(Arrays.asList(CREATED, RUNNING));
    }

    // in case the task has been modified between reading and writing it, it is retried up to 5 times
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 5)
    public Optional<Replicate> getAvailableReplicate(String walletAddress) {
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

            if (!replicatesService.hasWorkerAlreadyContributed(chainTaskId, walletAddress) &&
                    replicatesService.moreReplicatesNeeded(chainTaskId, task.getTrust())) {
                replicatesService.createNewReplicate(chainTaskId, walletAddress);
                workerService.addChainTaskIdToWorker(chainTaskId, walletAddress);
                return replicatesService.getReplicate(chainTaskId, walletAddress);
            }
        }

        return Optional.empty();
    }

    @EventListener
    public void onReplicateUpdatedEvent(ReplicateUpdatedEvent event) {
        Replicate replicate = event.getReplicate();
        log.info("Received ReplicateUpdatedEvent [chainTaskId:{}, walletAddress:{}] ",
                replicate.getChainTaskId(), replicate.getWalletAddress());
        Optional<Task> optional = taskRepository.findByChainTaskId(replicate.getChainTaskId());
        optional.ifPresent(this::tryToMoveTaskToNextStatus);
    }

    void tryToMoveTaskToNextStatus(Task task) {
        log.info("Try to move task to next status [chainTaskId:{}, currentStatus:{}]", task.getChainTaskId(), task.getCurrentStatus());
        switch (task.getCurrentStatus()) {
            case CREATED:
                tryUpdateFromCreatedToRunning(task);
                break;
            case RUNNING:
                tryUpdateFromRunningToContributed(task);
                break;
            case CONTRIBUTED:
                tryUpdateFromContributedToAtLeastOneReveal(task);
                break;
            case UPLOAD_RESULT_REQUESTED:
                tryUpdateFromUploadRequestedToUploadingResult(task);
                break;
            case UPLOADING_RESULT:
                tryUpdateFromUploadingResultToResultUploaded(task);
                break;
            case RESULT_UPLOADED:
                tryUpdateFromResultUploadedToFinalize(task);
                break;
            case COMPLETED:
                break;
            case ERROR:
                break;
        }
    }

    private Task updateTaskStatusAndSave(Task task, TaskStatus newStatus) {
        TaskStatus currentStatus = task.getCurrentStatus();
        task.changeStatus(newStatus);
        Task savedTask = taskRepository.save(task);
        log.info("Update task to new status[taskId:{}, currentStatus:{}, newStatus:{}]", task.getId(), currentStatus, newStatus);
        return savedTask;
    }

    void tryUpdateFromCreatedToRunning(Task task) {
        String chainTaskId = task.getChainTaskId();
        boolean condition1 = replicatesService.getNbReplicatesWithStatus(chainTaskId, ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED) > 0;
        boolean condition2 = replicatesService.getNbReplicatesWithStatus(chainTaskId, ReplicateStatus.COMPUTED) < task.getTrust();
        boolean condition3 = task.getCurrentStatus().equals(CREATED);

        if (condition1 && condition2 && condition3) {
            updateTaskStatusAndSave(task, RUNNING);
        }
    }

    void tryUpdateFromRunningToContributed(Task task) {
        boolean isTaskInRunningStatus = task.getCurrentStatus().equals(RUNNING);

        CredibilityMap credibilityMap = new CredibilityMap(replicatesService.getReplicates(task.getChainTaskId()));
        String consensus = credibilityMap.getConsensus();
        boolean isBestCredibilityBetterThanTrust = credibilityMap.getBestCredibility() >= trustToCredibility(task.getTrust());

        ChainTask chainTask = iexecHubService.getChainTask(task.getChainTaskId());
        boolean isChainTaskInActiveStatus = chainTask.getStatus().equals(ChainTaskStatus.ACTIVE);
        boolean isConsensusDeadlineInFuture = now() < chainTask.getConsensusDeadline();

        if (isTaskInRunningStatus && isBestCredibilityBetterThanTrust &&
                isChainTaskInActiveStatus && isConsensusDeadlineInFuture) {
            task.setConsensus(consensus);
            updateTaskStatusAndSave(task, CONTRIBUTED);

            try {
                if (iexecHubService.consensus(task.getChainTaskId(), task.getConsensus())) {
                    //TODO call only winners PLEASE_REVEAL & losers PLEASE_ABORT
                    notificationService.sendTaskNotification(TaskNotification.builder()
                            .taskNotificationType(TaskNotificationType.PLEASE_REVEAL)
                            .chainTaskId(task.getChainTaskId())
                            .workerAddress("").build()
                    );
                }
            } catch (Exception e) {
                log.error("Failed to consensus [taskId:{}, consensus:{}]", task.getId(), task.getConsensus());
            }
        } else {
            log.info("Unsatisfied check(s) for consensus [isTaskInRunningStatus:{}, isBestCredibilityBetterThanTrust:{}, " +
                            "isChainTaskInActiveStatus:{}, isConsensusDeadlineInFuture:{}, ] ",
                    isTaskInRunningStatus, isBestCredibilityBetterThanTrust, isChainTaskInActiveStatus, isConsensusDeadlineInFuture);
        }
    }

    void tryUpdateFromContributedToAtLeastOneReveal(Task task) {
        boolean condition1 = task.getCurrentStatus().equals(CONTRIBUTED);
        boolean condition2 = replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.REVEALED) > 0;

        if (condition1 && condition2) {
            updateTaskStatusAndSave(task, AT_LEAST_ONE_REVEALED);
            requestUpload(task);
        }
    }

    private void requestUpload(Task task) {
        if (task.getCurrentStatus().equals(AT_LEAST_ONE_REVEALED)) {
            updateTaskStatusAndSave(task, UPLOAD_RESULT_REQUESTED);
        }

        if (task.getCurrentStatus().equals(TaskStatus.UPLOAD_RESULT_REQUESTED)) {
            for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                if (replicate.getCurrentStatus().equals(ReplicateStatus.REVEALED)) {
                    notificationService.sendTaskNotification(TaskNotification.builder()
                            .chainTaskId(task.getChainTaskId())
                            .workerAddress(replicate.getWalletAddress())
                            .taskNotificationType(TaskNotificationType.UPLOAD)
                            .build());
                    log.info("NotifyUploadingWorker completed[uploadingWorkerWallet={}]", replicate.getWalletAddress());

                    // save in the task the workerWallet that is in charge of uploading the result
                    task.setUploadingWorkerWalletAddress(replicate.getWalletAddress());
                    taskRepository.save(task);
                    return; //ask only 1 worker to upload
                }
            }
        }
    }

    void tryUpdateFromUploadRequestedToUploadingResult(Task task) {
        boolean condition1 = task.getCurrentStatus().equals(TaskStatus.UPLOAD_RESULT_REQUESTED);
        boolean condition2 = replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.UPLOADING_RESULT) > 0;

        if (condition1 && condition2) {
            updateTaskStatusAndSave(task, UPLOADING_RESULT);
        }
    }

    void tryUpdateFromUploadingResultToResultUploaded(Task task) {
        boolean condition1 = task.getCurrentStatus().equals(TaskStatus.UPLOADING_RESULT);
        boolean condition2 = replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.RESULT_UPLOADED) > 0;

        if (condition1 && condition2) {
            updateTaskStatusAndSave(task, RESULT_UPLOADED);
            tryUpdateFromResultUploadedToFinalize(task);
        } else if (replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.UPLOAD_RESULT_REQUEST_FAILED) > 0 &&
                replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.UPLOADING_RESULT) == 0) {
            // need to request upload again
            requestUpload(task);
        }
    }

    private void tryUpdateFromResultUploadedToFinalize(Task task) {
        boolean condition1 = task.getCurrentStatus().equals(RESULT_UPLOADED);
        boolean condition2 = iexecHubService.canFinalize(task.getChainTaskId());

        if (condition1 && condition2) {
            updateTaskStatusAndSave(task, FINALIZE_STARTED);
            if (iexecHubService.finalizeTask(task.getChainTaskId(), "GET /results/" + task.getChainTaskId())) {
                updateTaskStatusAndSave(task, FINALIZE_COMPLETED);
                updateFromFinalizedToCompleted(task);
            } else {
                updateTaskStatusAndSave(task, FINALIZE_FAILED);
            }
        }
    }

    private void updateFromFinalizedToCompleted(Task task) {
        if (task.getCurrentStatus().equals(FINALIZE_COMPLETED)) {
            updateTaskStatusAndSave(task, COMPLETED);

            String chainTaskId = task.getChainTaskId();
            for (Replicate replicate : replicatesService.getReplicates(chainTaskId)) {
                workerService.removeChainTaskIdFromWorker(chainTaskId, replicate.getWalletAddress());
            }

            notificationService.sendTaskNotification(TaskNotification.builder()
                    .chainTaskId(chainTaskId)
                    .taskNotificationType(TaskNotificationType.COMPLETED)
                    .workerAddress("")
                    .build());
        }
    }
}
