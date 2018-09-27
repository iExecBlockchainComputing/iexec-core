package com.iexec.core.tasks;

import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
public class TaskController {

    private TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/tasks")
    public String postTask(@RequestParam(name = "commandLine") String commandLine,
                           @RequestParam(name = "nbContributionNeeded") int nbContributionNeeded) {
        Task task = taskService.addTask(commandLine, nbContributionNeeded);
        return task.getId();
    }

    @GetMapping("/tasks/{taskId}")
    public Optional<Task> getTask(@PathVariable("taskId") String taskId) {
        return taskService.getTask(taskId);
    }

    @PostMapping("/tasks/{taskId}/updateStatus/")
    public Task updateTaskStatus(@PathVariable("taskId") String taskId,
                                 @RequestParam TaskStatus taskStatus) {
        return taskService.updateTaskStatus(taskId, taskStatus);
    }

    @PostMapping("/tasks/{taskId}/replicates/updateStatus")
    public Optional<Replicate> updateReplicateStatus(@PathVariable("taskId") String taskId,
                                           @RequestParam ReplicateStatus replicateStatus,
                                           @RequestParam String workerName) {
        return taskService.updateReplicateStatus(taskId, replicateStatus, workerName);
    }

    @GetMapping("/tasks/available")
    public Replicate getReplicate(@RequestParam String workerName) {
        Optional<Replicate> replicate = taskService.getAvailableReplicate(workerName);
        return replicate.orElseGet(Replicate::new);
    }
}

