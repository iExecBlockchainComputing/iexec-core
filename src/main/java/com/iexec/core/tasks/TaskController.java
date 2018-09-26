package com.iexec.core.tasks;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Controller
public class TaskController {

    private TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/tasks")
    @ResponseBody
    public String postTask(@RequestParam(name="commandLine") String commandLine) {
        Task task = taskService.addTask(commandLine);
        return task.getId();
    }

    @GetMapping("/tasks/{taskId}")
    @ResponseBody
    public Task getTask(@PathVariable("taskId") String taskId) {
        Optional<Task> optional = taskService.getTask(taskId);
        return optional.orElse(new Task());
    }
}

