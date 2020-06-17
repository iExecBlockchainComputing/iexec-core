package com.iexec.core.task;

import static org.springframework.http.ResponseEntity.status;

import java.util.Optional;

import com.iexec.core.replicate.ReplicatesList;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.stdout.ReplicateStdout;
import com.iexec.core.task.stdout.TaskStdout;
import com.iexec.core.task.stdout.TaskStdoutService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskController {

    private TaskService taskService;
    private ReplicatesService replicatesService;
    private TaskStdoutService taskStdoutService;

    public TaskController(TaskService taskService,
                          ReplicatesService replicatesService,
                          TaskStdoutService taskStdoutService) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
        this.taskStdoutService = taskStdoutService;
    }

    // TODO: add auth

    @GetMapping("/tasks/{chainTaskId}")
    public ResponseEntity<TaskModel> getTask(@PathVariable("chainTaskId") String chainTaskId) {
        Optional<Task> optionalTask = taskService.getTaskByChainTaskId(chainTaskId);
        if (!optionalTask.isPresent()) {
            return status(HttpStatus.NOT_FOUND).build();
        }
        Task task = optionalTask.get();

        ReplicatesList replicates = replicatesService.getReplicatesList(chainTaskId)
                .orElseGet(ReplicatesList::new);

        TaskModel taskModel = new TaskModel(task, replicates.getReplicates());

        return ResponseEntity.ok(taskModel);
    }

    @GetMapping("/tasks/{chainTaskId}/stdout")
    public ResponseEntity<TaskStdout> getTaskStdout(@PathVariable("chainTaskId") String chainTaskId) {
        return taskStdoutService.getTaskStdout(chainTaskId)
                .<ResponseEntity<TaskStdout>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/tasks/{chainTaskId}/stdout/{walletAddress}")
    public ResponseEntity<ReplicateStdout> getReplicateStdout(@PathVariable("chainTaskId") String chainTaskId,
            @PathVariable("walletAddress") String walletAddress) {
        return taskStdoutService.getReplicateStdout(chainTaskId, walletAddress)
                .<ResponseEntity<ReplicateStdout>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
