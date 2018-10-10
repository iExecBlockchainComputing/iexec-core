package com.iexec.core.task;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class TaskService {

    private TaskRepository taskRepository;
    private WorkerService workerService;

    public TaskService(TaskRepository taskRepository, WorkerService workerService) {
        this.taskRepository = taskRepository;
        this.workerService = workerService;
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
            } else if (replicateStatus.equals(ReplicateStatus.COMPLETED)) {
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
        // TODO: the task selection is basic for now, we may tune it later
        HashSet<Task> tasks = getAllRunningTasks();
        if (tasks.isEmpty()) {
            return Optional.empty();// return empty if no task
        }

        int workerRunningReplicateNb = getRunningReplicatesOfWorker(workerName).size();
        Optional<Worker> worker = workerService.getWorker(workerName);
        if (worker.isPresent()){
            int workerCpuNb = worker.get().getCpuNb();
            if (workerRunningReplicateNb >= workerCpuNb) {
                log.info("Worker asking for too many replicates [workerName: {}, workerRunningReplicateNb:{}, workerCpuNb:{}]",
                        workerName, workerRunningReplicateNb, workerCpuNb);
                return Optional.empty();// return empty if worker has enough running tasks
            }
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
