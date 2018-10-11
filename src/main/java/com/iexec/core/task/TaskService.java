package com.iexec.core.task;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.result.TaskNotification;
import com.iexec.common.result.TaskNotificationType;
import com.iexec.core.pubsub.NotificationService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import com.iexec.core.workflow.ReplicateWorkflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.iexec.core.utils.DateTimeUtils.addMinutesToDate;

@Slf4j
@Service
public class TaskService {

    private TaskRepository taskRepository;
    private WorkerService workerService;
    private NotificationService notificationService;

    public TaskService(TaskRepository taskRepository,
                       WorkerService workerService,
                       NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.workerService = workerService;
        this.notificationService = notificationService;
    }

    public Task addTask(String dappName, String commandLine, int nbContributionNeeded) {
        log.info("Adding new task [commandLine:{}, nbContributionNeeded:{}]", commandLine, nbContributionNeeded);
        return taskRepository.save(new Task(dappName, commandLine, nbContributionNeeded));
    }

    public Optional<Task> getTask(String id) {
        return taskRepository.findById(id);
    }

    // in case the task has been modified between reading and writing it, it is retried up to 5 times
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 5)
    public Optional<Replicate> updateReplicateStatus(String taskId, String workerName, ReplicateStatus newStatus) {
        Optional<Task> optional = taskRepository.findById(taskId);
        if (!optional.isPresent()) {
            log.warn("No task found for replicate update [taskId:{}, workerName:{}, status:{}]", taskId, workerName, newStatus);
            return Optional.empty();
        }

        Task task = optional.get();
        for (Replicate replicate : task.getReplicates()) {
            if (replicate.getWorkerName().equals(workerName)) {
                ReplicateStatus currentStatus = replicate.getCurrentStatus();

                if (!ReplicateWorkflow.getInstance().isValidTransition(currentStatus, newStatus)) {
                    log.error("The replicate can't be updated to the new status [taskId:{}, workerName:{}, currentStatus:{}, newStatus:{}]",
                            taskId, workerName, currentStatus, newStatus);
                    return Optional.empty();
                }

                replicate.updateStatus(newStatus);
                // once the replicate status is updated, the task status has to be checked as well
                updateTaskStatus(task);
                log.info("Status of replicate updated [taskId:{}, workerName:{}, status:{}]", taskId,
                        workerName, newStatus);
                return Optional.of(replicate);

            }
        }

        log.warn("No replicate found for status update [taskId:{}, workerName:{}, status:{}]", taskId, workerName, newStatus);
        return Optional.empty();
    }


    // Timeout for the replicate uploading its result is 1min.
    @Scheduled(fixedRate = 20000)
    void detectUploadRequestTimeout() {

        // check all task with status upload result requested
        List<Task> tasks = taskRepository.findByCurrentStatus(TaskStatus.UPLOAD_RESULT_REQUESTED);
        for (Task task : tasks) {
            for (Replicate replicate : task.getReplicates()) {
                if (replicate.getCurrentStatus().equals(ReplicateStatus.UPLOAD_RESULT_REQUESTED)
                        && new Date().after(addMinutesToDate(replicate.getLatestStatusChange().getDate(), 1))) {
                    replicate.updateStatus(ReplicateStatus.UPLOAD_RESULT_REQUEST_FAILED);
                    taskRepository.save(task);
                    requestUpload(task);
                }
            }
        }
    }

    // in case the task has been modified between reading and writing it, it is retried up to 5 times
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 5)
    public Optional<Replicate> getAvailableReplicate(String workerName) {
        // return empty if the worker is not registered
        Optional<Worker> optional = workerService.getWorker(workerName);
        if (!optional.isPresent()) {
            return Optional.empty();
        }
        Worker worker = optional.get();

        // return empty if the worker already has enough running tasks
        int workerRunningReplicateNb = getRunningReplicatesOfWorker(workerName).size();
        int workerCpuNb = worker.getCpuNb();
        if (workerRunningReplicateNb >= workerCpuNb) {
            log.info("Worker asking for too many replicates [workerName: {}, workerRunningReplicateNb:{}, workerCpuNb:{}]",
                    workerName, workerRunningReplicateNb, workerCpuNb);
            return Optional.empty();
        }

        // return empty if there is no task to contribute
        HashSet<Task> tasks = getAllRunningTasks();
        if (tasks.isEmpty()) {
            return Optional.empty();
        }

        for (Task task : tasks) {
            if (!task.hasWorkerAlreadyContributed(workerName) &&
                    task.needMoreReplicates()) {
                task.createNewReplicate(workerName);
                Task savedTask = taskRepository.save(task);
                return savedTask.getReplicate(workerName);
            }
        }

        return Optional.empty();
    }

    private List<Replicate> getRunningReplicatesOfWorker(String workerName) {
        List<Replicate> workerActiveReplicates = new ArrayList<>();
        for (Task task : getAllRunningTasks()) {
            List<Replicate> replicates = task.getReplicates();
            for (Replicate replicate : replicates) {
                if (replicate.getWorkerName().equals(workerName) &&
                        (replicate.getCurrentStatus().equals(ReplicateStatus.CREATED) || replicate.getCurrentStatus().equals(ReplicateStatus.RUNNING))) {
                    workerActiveReplicates.add(replicate);
                }
            }
        }
        return workerActiveReplicates;
    }

    private HashSet<Task> getAllRunningTasks() {
        HashSet<Task> tasks = new HashSet<>();
        tasks.addAll(taskRepository.findByCurrentStatus(TaskStatus.CREATED));
        tasks.addAll(taskRepository.findByCurrentStatus(TaskStatus.RUNNING));
        return tasks;
    }

    void updateTaskStatus(Task task) {
        TaskStatus currentStatus = task.getCurrentStatus();
        switch (currentStatus) {
            case CREATED:
                tryUpdateToRunning(task);
                break;
            case RUNNING:
                tryUpdateToComputed(task);
                break;
            case UPLOAD_RESULT_REQUESTED:
                tryUpdateToUploadingResult(task);
                break;
            case UPLOADING_RESULT:
                tryUpdateToResultUploaded(task);
                break;
        }
    }

    void tryUpdateToRunning(Task task) {
        if (task.getNbReplicatesWithStatus(ReplicateStatus.COMPUTED) < task.getNbContributionNeeded() &&
                task.getNbReplicatesWithStatus(ReplicateStatus.RUNNING) > 0 && task.getCurrentStatus().equals(TaskStatus.CREATED)) {
            task.setCurrentStatus(TaskStatus.RUNNING);
            taskRepository.save(task);
        }
    }

    void tryUpdateToComputed(Task task) {
        if (task.getNbReplicatesWithStatus(ReplicateStatus.COMPUTED) == task.getNbContributionNeeded() &&
                task.getCurrentStatus().equals(TaskStatus.RUNNING)) {
            task.setCurrentStatus(TaskStatus.COMPUTED);
            task.setCurrentStatus(TaskStatus.UPLOAD_RESULT_REQUESTED);
            task = taskRepository.save(task);
            requestUpload(task);
        }
    }

    void tryUpdateToUploadingResult(Task task) {
        for (Replicate replicate : task.getReplicates()) {
            if (replicate.getCurrentStatus().equals(ReplicateStatus.UPLOADING_RESULT)) {
                task.setCurrentStatus(TaskStatus.UPLOADING_RESULT);
                taskRepository.save(task);
                break;
            }
        }
    }

    void tryUpdateToResultUploaded(Task task) {
        for (Replicate replicate : task.getReplicates()) {
            if (replicate.getCurrentStatus().equals(ReplicateStatus.RESULT_UPLOADED)) {
                task.setCurrentStatus(TaskStatus.RESULT_UPLOADED);
                task = taskRepository.save(task);
            }
        }
    }

    void requestUpload(Task task) {
        for (Replicate replicate : task.getReplicates()) {
            if (replicate.getCurrentStatus().equals(ReplicateStatus.COMPUTED)) {
                notificationService.sendTaskNotification(TaskNotification.builder()
                        .taskId(task.getId())
                        .workerAddress(replicate.getWorkerName())
                        .taskNotificationType(TaskNotificationType.UPLOAD)
                        .build());
                // TODO: this is the worker job to upload its status
                replicate.updateStatus(ReplicateStatus.UPLOAD_RESULT_REQUESTED);
                taskRepository.save(task);
                return;
            }
        }
    }

}
