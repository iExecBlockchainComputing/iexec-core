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

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
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


    @RequestMapping(method = RequestMethod.POST, path = "/tasks/{taskId}/replicates/updateStatus")
    public ResponseEntity updateReplicateStatus(@PathVariable(name = "taskId") String taskId,
                                                @RequestParam(name = "workerName") String workerName,
                                                @RequestParam(name = "replicateStatus") ReplicateStatus replicateStatus) {
        log.info("Update replicate status [taskId:{}, replicateStatus:{}, workerName:{}]", taskId, replicateStatus, workerName);
        Optional<Replicate> optional = taskService.updateReplicateStatus(taskId, workerName, replicateStatus);
        if (!optional.isPresent()) {
            return status(HttpStatus.NO_CONTENT).build();
        }
        return convertReplicateToModel(optional.get()).
                <ResponseEntity>map(ResponseEntity::ok)
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());

    }

    @RequestMapping(method = RequestMethod.GET, path = "/tasks/available")
    public ResponseEntity getReplicate(@RequestParam(name = "workerName") String workerName) {
        Optional<Replicate> optional = taskService.getAvailableReplicate(workerName);
        if (!optional.isPresent()) {
            return status(HttpStatus.NO_CONTENT).build();
        }
        return convertReplicateToModel(optional.get()).
                <ResponseEntity>map(ResponseEntity::ok)
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    Optional<ReplicateModel> convertReplicateToModel(Replicate replicate) {
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
                            .replicateStatus(replicate.getStatusChangeList().get(replicate.getStatusChangeList().size() - 1).getStatus())
                            .build()
        );
    }
}

