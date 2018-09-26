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
    public String postTask(@RequestParam(name="commandLine") String commandLine) {
        Task task = taskService.addTask(commandLine);
        return task.getId();
    }

    @GetMapping("/tasks/{taskId}")
    public Task getTask(@PathVariable("taskId") String taskId) {
        Optional<Task> optional = taskService.getTask(taskId);
        return optional.orElse(new Task());
    }

    @PostMapping("/tasks/{taskId}/updateStatus/")
    public Task getTask(@PathVariable("taskId") String taskId,
                        @RequestParam TaskStatus taskStatus){
        return taskService.updateTaskStatus(taskId, taskStatus);
    }
}

