package com.iexec.core.tasks;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class TaskService {

    private TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Task addTask(String commandLine){
        return taskRepository.save(new Task(commandLine));
    }

    public Optional<Task> getTask(String id){
        return taskRepository.findById(id);
    }
}
