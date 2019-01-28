package com.iexec.core.task.listener;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.detector.ContributionUnnotifiedDetector;
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
    private ContributionUnnotifiedDetector contributionUnnotifiedDetector;


    public ReplicateListeners(TaskExecutorEngine taskExecutorEngine,
                              WorkerService workerService, ContributionUnnotifiedDetector contributionUnnotifiedDetector) {
        this.taskExecutorEngine = taskExecutorEngine;
        this.workerService = workerService;
        this.contributionUnnotifiedDetector = contributionUnnotifiedDetector;
    }

    @EventListener
    public void onReplicateUpdatedEvent(ReplicateUpdatedEvent event) {
        log.info("Received ReplicateUpdatedEvent [chainTaskId:{}] ", event.getChainTaskId());
        taskExecutorEngine.updateTask(event.getChainTaskId());

        /*
         * A CANT_CONTRIBUTE_SINCE_TASK_NOT_ACTIVE status means this new worker have been authorized to contribute
         * (but cant) while we had a consensus_reached onchain but not in database (meaning another didnt notified he had contributed).
         * We should start a detector which will look for unnotified contributions and will upgrade task to consensus_reached
         */
        if (event.getNewReplicateStatus().equals(ReplicateStatus.CANT_CONTRIBUTE_SINCE_TASK_NOT_ACTIVE)) {
            contributionUnnotifiedDetector.detect();
        }
    }

    @EventListener
    public void onReplicateComputedEvent(ReplicateComputedEvent event) {
        Replicate replicate = event.getReplicate();
        log.info("Received ReplicateComputedEvent [chainTaskId:{}, walletAddress:{}] ",
                replicate.getChainTaskId(), replicate.getWalletAddress());
        workerService.removeComputedChainTaskIdFromWorker(replicate.getChainTaskId(), replicate.getWalletAddress());
    }
}
