package com.iexec.core.task;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.result.TaskNotification;
import com.iexec.common.result.TaskNotificationType;
import com.iexec.core.pubsub.NotificationService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class TaskService {

    private TaskRepository taskRepository;
    private WorkerService workerService;
    private NotificationService notificationService;

    public TaskService(TaskRepository taskRepository, WorkerService workerService, NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.workerService = workerService;
        this.notificationService = notificationService;
    }

    private static Date addMinutesToDate(Date date, int minutes) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.MINUTE, minutes);
        return calendar.getTime();
    }

    public Task addTask(String dappName, String commandLine, int nbContributionNeeded) {
        log.info("Adding new task [commandLine:{}, nbContributionNeeded:{}]", commandLine, nbContributionNeeded);
        return taskRepository.save(new Task(dappName, commandLine, nbContributionNeeded));
    }

    public Optional<Task> getTask(String id) {
        return taskRepository.findById(id);
    }

    public Optional<Replicate> updateReplicateStatus(String taskId, ReplicateStatus status, String workerName) {
        Optional<Task> optional = taskRepository.findById(taskId);
        if (!optional.isPresent()) {
            log.warn("No task found for replicate update [taskId:{}, workerName:{}, status:{}]", taskId, workerName, status);
            return Optional.empty();
        }

        Task task = optional.get();
        for (Replicate replicate : task.getReplicates()) {
            if (replicate.getWorkerName().equals(workerName)) {
                // a control should happen here to check if the state transition is correct or not
                replicate.updateStatus(status);
                updateTaskStatus(task);
                taskRepository.save(task);
                log.info("Status of replicate updated [taskId:{}, workerName:{}, status:{}]", taskId, workerName, status);
                return Optional.of(replicate);
            }
        }

        log.warn("No replicate found for status update [taskId:{}, workerName:{}, status:{}]", taskId, workerName, status);
        return Optional.empty();
    }

    // this is the main method that will update the status of the task
    private void updateTaskStatus(Task task) {
        int nbRunningReplicates = 0;
        int nbComputedReplicates = 0;
        int nbContributionNeeded = task.getNbContributionNeeded();

        for (Replicate replicate : task.getReplicates()) {
            ReplicateStatus replicateStatus = replicate.getStatusChangeList().get(replicate.getStatusChangeList().size() - 1).getStatus();
            if (replicateStatus.equals(ReplicateStatus.RUNNING)) {
                nbRunningReplicates++;
            } else if (replicateStatus.equals(ReplicateStatus.COMPUTED)) {
                nbComputedReplicates++;
            }
        }

        if (nbComputedReplicates < nbContributionNeeded &&
                nbRunningReplicates > 0 && !task.getCurrentStatus().equals(TaskStatus.RUNNING)) {
            task.setCurrentStatus(TaskStatus.RUNNING);
        }

        if (nbComputedReplicates == nbContributionNeeded &&
                !task.getCurrentStatus().equals(TaskStatus.COMPUTED)) {
            task.setCurrentStatus(TaskStatus.COMPUTED);
        }

        if (task.getLatestStatusChange().getStatus().equals(TaskStatus.UPLOAD_RESULT_REQUESTED)) {
            for (Replicate replicate : task.getReplicates()) {
                if (replicate.getLatestStatus().equals(ReplicateStatus.UPLOADING_RESULT)) {
                    task.setCurrentStatus(TaskStatus.UPLOADING_RESULT);
                }
            }
        }

        if (task.getLatestStatusChange().getStatus().equals(TaskStatus.UPLOADING_RESULT)) {
            for (Replicate replicate : task.getReplicates()) {
                if (replicate.getLatestStatus().equals(ReplicateStatus.RESULT_UPLOADED)) {
                    task.setCurrentStatus(TaskStatus.RESULT_UPLOADED);
                }
            }
        }

        if (task.getCurrentStatus().equals(TaskStatus.COMPUTED)) {
            requestUpload(task);
        }


        //Should be called (here? +) elsewhere by a checkUploadStatus cron
        if (task.getCurrentStatus().equals(TaskStatus.UPLOAD_RESULT_REQUESTED) && detectUploadRequestTimeout(task)) {
            requestUpload(task);
        }


    }

    private void requestUpload(Task task) {
        for (Replicate replicate : task.getReplicates()) {
            if (replicate.getLatestStatus().equals(ReplicateStatus.COMPUTED)) {
                notificationService.sendTaskNotification(TaskNotification.builder()
                        .taskId(task.getId())
                        .workerAddress(replicate.getWorkerName())
                        .taskNotificationType(TaskNotificationType.UPLOAD)
                        .build());
                replicate.updateStatus(ReplicateStatus.UPLOAD_RESULT_REQUESTED);
                task.setCurrentStatus(TaskStatus.UPLOAD_RESULT_REQUESTED);
                return;
            }
        }
    }

    private boolean detectUploadRequestTimeout(Task task) {
        boolean uploadRequestTimeout = false;
        if (task.getCurrentStatus().equals(TaskStatus.UPLOAD_RESULT_REQUESTED)) {
            for (Replicate replicate : task.getReplicates()) {
                if (replicate.getLatestStatus().equals(ReplicateStatus.UPLOAD_RESULT_REQUESTED)
                        && new Date().after(addMinutesToDate(replicate.getLatestStatusChange().getDate(), 1))) {
                    replicate.updateStatus(ReplicateStatus.UPLOAD_RESULT_REQUEST_FAILED);
                    uploadRequestTimeout = true;
                }
            }
        }
        return uploadRequestTimeout;
    }

    public Optional<Replicate> getAvailableReplicate(String workerName) {
        // an Replicate can contribute to a task in CREATED or in RUNNING status
        // TODO: the task selection is basic for now, we may tune it later
        HashSet<Task> tasks = getAllRunningTasks();
        if (tasks.isEmpty()) {
            return Optional.empty();// return empty if no task
        }

        int workerRunningReplicateNb = getRunningReplicatesOfWorker(workerName).size();
        int workerCpuNb = workerService.getWorker(workerName).getCpuNb();
        if (workerRunningReplicateNb >= workerCpuNb) {
            log.info("Worker asking for too many replicates [workerName: {}, workerRunningReplicateNb:{}, workerCpuNb:{}]",
                    workerName, workerRunningReplicateNb, workerCpuNb);
            return Optional.empty();// return empty if worker has enough running tasks
        }

        for (Task task : tasks) {
            if (!task.hasWorkerAlreadyContributed(workerName) &&
                    task.needMoreReplicates()) {
                task.createNewReplicate(workerName);
                taskRepository.save(task);
                return task.getReplicate(workerName);
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
                        (replicate.getLatestStatus().equals(ReplicateStatus.CREATED) || replicate.getLatestStatus().equals(ReplicateStatus.RUNNING))) {
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


}
