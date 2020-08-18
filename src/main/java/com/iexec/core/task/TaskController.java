package com.iexec.core.task;

import static org.springframework.http.ResponseEntity.status;

import java.util.Optional;

import com.iexec.core.replicate.ReplicatesList;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.stdout.StdoutService;
import com.iexec.core.stdout.TaskStdout;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ResponseEntity<TaskStdout> getTaskStdout(
                @PathVariable("chainTaskId") String chainTaskId,
                @RequestParam(required = false) String replicateAddress) {
        Optional<TaskStdout> stdout = replicateAddress != null ?
                stdoutService.getReplicateStdout(chainTaskId, replicateAddress) :
                stdoutService.getTaskStdout(chainTaskId);
        return stdout
                .<ResponseEntity<TaskStdout>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * This is just temporary
     */
    @GetMapping("/tasks/{chainTaskId}/stdout/html")
    public ModelAndView getTaskStdoutHtmlPage(@PathVariable("chainTaskId") String chainTaskId) {
        Optional<TaskStdout> taskStdout = stdoutService.getTaskStdout(chainTaskId);
        ModelAndView modelAndView = new ModelAndView();
        modelAndView.setViewName("stdout");
        if (taskStdout.isPresent()) {
            modelAndView.addObject("taskStdout", taskStdout.get());
        } else {
            modelAndView.addObject("taskStdout", null);
            modelAndView.setStatus(HttpStatus.NOT_FOUND);
        }
        return modelAndView;
    }
}
