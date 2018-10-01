package com.iexec.core.tasks;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;

@Service
public class TaskService {

    private TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Task addTask(String commandLine, int nbContributionNeeded) {
        return taskRepository.save(new Task(commandLine, nbContributionNeeded));
    }

    public Optional<Task> getTask(String id) {
        return taskRepository.findById(id);
    }

    public Optional<Task> updateTaskStatus(String taskId, TaskStatus status) {
        Optional<Task> optional = taskRepository.findById(taskId);
        if (optional.isPresent()) {
            Task task = optional.get();
            task.getDateStatusList().add(new TaskStatusChange(status));
            return Optional.of(taskRepository.save(task));
        } else {
            return Optional.empty();
        }
    }

    public Optional<Replicate> updateReplicateStatus(String taskId, ReplicateStatus status, String workerName) {
        Optional<Task> optional = taskRepository.findById(taskId);
        if (!optional.isPresent()) {
            return Optional.empty();
        }

        Task task = optional.get();
        for (Replicate replicate : task.getReplicates()) {
            if (replicate.getWorkerName().equals(workerName)) {
                replicate.getStatusList().add(new ReplicateStatusChange(status));
                updateTaskStatus(task);
                taskRepository.save(task);
                return Optional.of(replicate);
            }
        }

        return Optional.empty();
    }

    // this is the main method that will update the status of the task
    private void updateTaskStatus(Task task) {
        int nbRunning = 0;
        int nbCompleted = 0;
        for (Replicate replicate : task.getReplicates()) {
            ReplicateStatus replicateStatus = replicate.getStatusList().get(replicate.getStatusList().size() - 1).getStatus();
            if (replicateStatus.equals(ReplicateStatus.RUNNING)) {
                nbRunning++;
            }

            if (replicateStatus.equals(ReplicateStatus.COMPLETED)) {
                nbCompleted++;
            }
        }

        if (nbCompleted == task.getNbContributionNeeded() && !task.getCurrentStatus().equals(TaskStatus.COMPLETED)){
            task.setCurrentStatus(TaskStatus.COMPLETED);
        }

        if (nbCompleted < task.getNbContributionNeeded() && nbRunning > 0 && !task.getCurrentStatus().equals(TaskStatus.RUNNING)){
            task.setCurrentStatus(TaskStatus.RUNNING);
        }
    }


    public Optional<Replicate> getAvailableReplicate(String workerName) {
        // CREATED task means they still need some contributions
        HashSet<Task> tasks = new HashSet<>();
        tasks.addAll(taskRepository.findByCurrentStatus(TaskStatus.CREATED));
        tasks.addAll(taskRepository.findByCurrentStatus(TaskStatus.RUNNING));
        // TODO: the task selection is basic for now, we may tune it later
        // return empty if no task
        if (tasks.isEmpty()) {
            return Optional.empty();
        }

        for(Task task:tasks){
            if (!hasWorkerAlreadyContributed(task, workerName)){
                Replicate newReplicate = new Replicate(workerName, task.getId());
                task.getReplicates().add(newReplicate);
                taskRepository.save(task);
                return Optional.of(newReplicate);
            }
        }

        return Optional.empty();

    }

    private boolean hasWorkerAlreadyContributed(Task task, String workerName){
        for (Replicate replicate : task.getReplicates()) {
            if (replicate.getWorkerName().equals(workerName)) {
                return true;
            }
        }
        return false;
    }
}
