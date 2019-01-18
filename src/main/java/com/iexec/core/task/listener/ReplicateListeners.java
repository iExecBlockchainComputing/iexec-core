package com.iexec.core.task.listener;

import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicateComputedEvent;
import com.iexec.core.replicate.ReplicateUpdatedEvent;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskExecutorEngine;
import com.iexec.core.task.TaskService;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class ReplicateListeners {

    private TaskService taskService;
    private TaskExecutorEngine taskExecutorEngine;
    private WorkerService workerService;

    public ReplicateListeners(TaskService taskService,
                              TaskExecutorEngine taskExecutorEngine,
                              WorkerService workerService) {
        this.taskService = taskService;
        this.taskExecutorEngine = taskExecutorEngine;
        this.workerService = workerService;
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
    public void onReplicateComputedEvent(ReplicateComputedEvent event) {
        Replicate replicate = event.getReplicate();
        log.info("Received ReplicateComputedEvent [chainTaskId:{}, walletAddress:{}] ",
                replicate.getChainTaskId(), replicate.getWalletAddress());
        workerService.removeComputedChainTaskIdFromWorker(replicate.getChainTaskId(), replicate.getWalletAddress());
    }
}
