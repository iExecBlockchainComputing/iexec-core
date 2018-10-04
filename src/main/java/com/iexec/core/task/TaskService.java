package com.iexec.core.task;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.replicate.Replicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;

@Slf4j
@Service
public class TaskService {

    private TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
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
        int nbCompletedReplicates = 0;
        int nbContributionNeeded = task.getNbContributionNeeded();

        for (Replicate replicate : task.getReplicates()) {
            ReplicateStatus replicateStatus = replicate.getStatusList().get(replicate.getStatusList().size() - 1).getStatus();
            if (replicateStatus.equals(ReplicateStatus.RUNNING)) {
                nbRunningReplicates++;
            } else if  (replicateStatus.equals(ReplicateStatus.COMPLETED)) {
                nbCompletedReplicates++;
            }
        }

        if (nbCompletedReplicates == nbContributionNeeded &&
                !task.getCurrentStatus().equals(TaskStatus.COMPLETED)) {
            task.setCurrentStatus(TaskStatus.COMPLETED);
        }

        if (nbCompletedReplicates < nbContributionNeeded &&
                nbRunningReplicates > 0 && !task.getCurrentStatus().equals(TaskStatus.RUNNING)) {
            task.setCurrentStatus(TaskStatus.RUNNING);
        }
    }


    public Optional<Replicate> getAvailableReplicate(String workerName) {
        // an Replicate can contribute to a task in CREATED or in RUNNING status
        HashSet<Task> tasks = new HashSet<>();
        tasks.addAll(taskRepository.findByCurrentStatus(TaskStatus.CREATED));
        tasks.addAll(taskRepository.findByCurrentStatus(TaskStatus.RUNNING));
        // TODO: the task selection is basic for now, we may tune it later
        // return empty if no task
        if (tasks.isEmpty()) {
            return Optional.empty();
        }

        for (Task task : tasks) {
            if (!hasWorkerAlreadyContributed(task, workerName) &&
                    taskNeedMoreReplicates(task)) {
                Replicate newReplicate = new Replicate(workerName, task.getId());
                task.getReplicates().add(newReplicate);
                taskRepository.save(task);
                return Optional.of(newReplicate);
            }
        }

        return Optional.empty();
    }

    private boolean taskNeedMoreReplicates(Task task){
        int nbValidReplicates = 0;
        for (Replicate replicate:task.getReplicates()){
            if (!replicate.getLatestStatus().equals(ReplicateStatus.ERROR)){
                nbValidReplicates++;
            }
        }
        return nbValidReplicates < task.getNbContributionNeeded();
    }

    private boolean hasWorkerAlreadyContributed(Task task, String workerName) {
        for (Replicate replicate : task.getReplicates()) {
            if (replicate.getWorkerName().equals(workerName)) {
                return true;
            }
        }
        return false;
    }

}
