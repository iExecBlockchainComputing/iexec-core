package com.iexec.core.task;

import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicateUpdatedEvent;
import com.iexec.core.task.event.TaskCompletedEvent;
import com.iexec.core.task.event.TaskCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
public class TaskListeners {

    private TaskService taskService;
    private TaskExecutorEngine taskExecutorEngine;

    public TaskListeners(TaskService taskService,
                         TaskExecutorEngine taskExecutorEngine) {
        this.taskService = taskService;
        this.taskExecutorEngine = taskExecutorEngine;
    }

    @EventListener
    public void onTaskCreatedEvent(TaskCreatedEvent event) {
        Task task = event.getTask();
        log.info("Received TaskCreatedEvent [chainDealId:{}, taskIndex:{}] ",
                task.getChainDealId(), task.getTaskIndex());
        taskExecutorEngine.updateTask(task);
    }

    @EventListener
    public void onReplicateUpdatedEvent(ReplicateUpdatedEvent event) {
        Replicate replicate = event.getReplicate();
        log.info("Received ReplicateUpdatedEvent [chainTaskId:{}, walletAddress:{}] ",
                replicate.getChainTaskId(), replicate.getWalletAddress());
        Optional<Task> optional = taskService.getTaskByChainTaskId(replicate.getChainTaskId());
        optional.ifPresent(task -> taskExecutorEngine.updateTask(task));
    }

    @EventListener
    public void onTaskCompletedEvent(TaskCompletedEvent event) {
        Task task = event.getTask();
        log.info("Received TaskCompletedEvent [chainTaskId:{}] ",
                task.getChainTaskId());
        taskExecutorEngine.removeTaskExecutor(task);
    }
}
