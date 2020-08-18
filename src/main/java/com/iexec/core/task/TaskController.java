package com.iexec.core.task;

import static org.springframework.http.ResponseEntity.status;

import java.util.Optional;

import com.iexec.core.replicate.ReplicatesList;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.stdout.ReplicateStdout;
import com.iexec.core.stdout.StdoutService;
import com.iexec.core.stdout.TaskStdout;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

@RestController
public class TaskController {

    private TaskService taskService;
    private ReplicatesService replicatesService;
    private StdoutService stdoutService;

    public TaskController(TaskService taskService,
                          ReplicatesService replicatesService,
                          StdoutService stdoutService) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
        this.stdoutService = stdoutService;
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
    public ModelAndView getTaskStdout(@PathVariable("chainTaskId") String chainTaskId) {
        TaskStdout taskStdout = stdoutService.getTaskStdout(chainTaskId)
                .orElse(new TaskStdout(chainTaskId));
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setViewName("stdout");
        modelAndView.addObject("taskStdout", taskStdout);
        return modelAndView;
    }

    @GetMapping("/tasks/{chainTaskId}/stdout/{walletAddress}")
    public ResponseEntity<ReplicateStdout> getReplicateStdout(
                @PathVariable("chainTaskId") String chainTaskId,
                @PathVariable("walletAddress") String walletAddress) {
        return stdoutService.getReplicateStdout(chainTaskId, walletAddress)
                .<ResponseEntity<ReplicateStdout>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
