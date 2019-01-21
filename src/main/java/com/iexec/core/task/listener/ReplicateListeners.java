package com.iexec.core.task.listener;

import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicateComputedEvent;
import com.iexec.core.replicate.ReplicateUpdatedEvent;
import com.iexec.core.task.TaskExecutorEngine;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReplicateListeners {

    private TaskExecutorEngine taskExecutorEngine;
    private WorkerService workerService;

    public ReplicateListeners(TaskExecutorEngine taskExecutorEngine,
                              WorkerService workerService) {
        this.taskExecutorEngine = taskExecutorEngine;
        this.workerService = workerService;
    }

    @EventListener
    public void onReplicateUpdatedEvent(ReplicateUpdatedEvent event) {
        log.info("Received ReplicateUpdatedEvent [chainTaskId:{}] ", event.getChainTaskId());
        taskExecutorEngine.updateTask(event.getChainTaskId());
    }

    @EventListener
    public void onReplicateComputedEvent(ReplicateComputedEvent event) {
        Replicate replicate = event.getReplicate();
        log.info("Received ReplicateComputedEvent [chainTaskId:{}, walletAddress:{}] ",
                replicate.getChainTaskId(), replicate.getWalletAddress());
        workerService.removeComputedChainTaskIdFromWorker(replicate.getChainTaskId(), replicate.getWalletAddress());
    }
}
