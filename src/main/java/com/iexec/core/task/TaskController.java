package com.iexec.core.task;

import com.iexec.common.replicate.ReplicateModel;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.replicate.Replicate;
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
    private ReplicateConverterService replicateConverterService;

    public TaskController(TaskService taskService, ReplicateConverterService replicateConverterService) {
        this.taskService = taskService;
        this.replicateConverterService = replicateConverterService;
    }

    @PostMapping("/tasks")
    public ResponseEntity postTask(@RequestParam(name = "dappName") String dappName,
                                   @RequestParam(name = "commandLine") String commandLine,
                                   @RequestParam(name = "nbContributionNeeded") int nbContributionNeeded) {
        Task task = taskService.addTask(dappName, commandLine, nbContributionNeeded);
        log.info("New task created [taskId:{}]", task.getId());
        return ok(task.getId());
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity getTask(@PathVariable("taskId") String taskId) {
        Optional<Task> optional = taskService.getTask(taskId);
        return optional.
                <ResponseEntity>map(ResponseEntity::ok).
                orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    @PostMapping("/tasks/{taskId}/replicates/updateStatus")
    public ResponseEntity updateReplicateStatus(@PathVariable("taskId") String taskId,
                                                @RequestParam ReplicateStatus replicateStatus,
                                                @RequestParam String workerName) {
        Optional<Replicate> optional = taskService.updateReplicateStatus(taskId, replicateStatus, workerName);
        if (!optional.isPresent()) {
            return status(HttpStatus.NO_CONTENT).build();
        }
        return replicate2Dto(optional.get()).
                <ResponseEntity>map(ResponseEntity::ok)
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());

    }

    @GetMapping("/tasks/available")
    public ResponseEntity getAvailableReplicate(@RequestParam String workerName) {
        Optional<Replicate> optional = taskService.getAvailableReplicate(workerName);
        if (!optional.isPresent()) {
            return status(HttpStatus.NO_CONTENT).build();
        }
        return replicate2Dto(optional.get()).
                <ResponseEntity>map(ResponseEntity::ok)
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    public Optional<ReplicateModel> replicate2Dto(Replicate replicate) {
        Optional<Task> optional = taskService.getTask(replicate.getTaskId());
        if (!optional.isPresent()) {
            return Optional.empty();
        }
        Task task = optional.get();

        return Optional.of(ReplicateModel.builder()
                .taskId(replicate.getTaskId())
                .workerAddress(replicate.getWorkerName())
                .dappType(task.getDappType())
                .dappName(task.getDappName())
                .cmd(task.getCommandLine())
                .replicateStatus(replicate.getStatusList().get(replicate.getStatusList().size() - 1).getStatus())
                .build());
    }
}

