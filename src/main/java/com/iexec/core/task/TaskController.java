package com.iexec.core.task;

import com.iexec.core.replicate.ReplicatesList;
import com.iexec.core.replicate.ReplicatesService;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

import static org.springframework.http.ResponseEntity.status;


@Slf4j
@RestController
public class TaskController {

    private TaskService taskService;
    private ReplicatesService replicatesService;


    public TaskController(TaskService taskService,
                          ReplicatesService replicatesService) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
    }

    @GetMapping("/tasks/{chainTaskId}")
    public ResponseEntity getTask(@PathVariable("chainTaskId") String chainTaskId) {
        Optional<Task> optionalTask = taskService.getTaskByChainTaskId(chainTaskId);
        if (!optionalTask.isPresent()) {
            return status(HttpStatus.NO_CONTENT).build();
        }
        Task task = optionalTask.get();

        ReplicatesList replicates = replicatesService.getReplicatesList(chainTaskId)
                .orElseGet(ReplicatesList::new);

        TaskModel taskModel = new TaskModel(task, replicates.getReplicates());

        return ResponseEntity.ok(taskModel);
    }
}

