package com.iexec.core.tasks;

import org.springframework.stereotype.Service;

import java.util.Date;
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

    public Task updateTaskStatus(String taskId, TaskStatus status){
        Optional<Task> optional = taskRepository.findById(taskId);
        if(optional.isPresent()){
            Task task = optional.get();
            task.getDateStatusList().add(new TaskStatusChange(new Date(), status));
            return taskRepository.save(task);
        } else {
            return new Task();
        }
    }
}
