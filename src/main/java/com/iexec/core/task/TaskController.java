package com.iexec.core.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

@Slf4j
@RestController
public class TaskController {

    private TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/task")
    public ResponseEntity postTask(@RequestParam(name = "commandLine") String commandLine,
                                   @RequestParam(name = "nbContributionNeeded") int nbContributionNeeded) {
        Task task = taskService.addTask(commandLine, nbContributionNeeded);
        log.info("New task created [taskId:{}]", task.getId());
        return ok(task.getId());
    }

    @GetMapping("/task/{taskId}")
    public ResponseEntity getTask(@PathVariable("taskId") String taskId) {
        Optional<Task> optional = taskService.getTask(taskId);
        return optional.
                <ResponseEntity>map(ResponseEntity::ok).
                orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    @PostMapping("/task/{taskId}/updateStatus/")
    public ResponseEntity updateTaskStatus(@PathVariable("taskId") String taskId,
                                 @RequestParam TaskStatus taskStatus) {
        Optional<Task> optional = taskService.updateTaskStatus(taskId, taskStatus);
        return optional.
                <ResponseEntity>map(ResponseEntity::ok)
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    @PostMapping("/task/{taskId}/replicates/updateStatus")
    public ResponseEntity updateReplicateStatus(@PathVariable("taskId") String taskId,
                                                @RequestParam ReplicateStatus replicateStatus,
                                                @RequestParam String workerName) {
        Optional<Replicate> optional = taskService.updateReplicateStatus(taskId, replicateStatus, workerName);
        return optional.
                <ResponseEntity>map(ResponseEntity::ok)
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    @GetMapping("/task/available")
    public ResponseEntity getReplicate(@RequestParam String workerName) {
        Optional<Replicate> optional = taskService.getAvailableReplicate(workerName);
        return optional.
                <ResponseEntity>map(ResponseEntity::ok)
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }
}

