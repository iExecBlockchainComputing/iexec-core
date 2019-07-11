package com.iexec.core.task.listener;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.detector.replicate.ContributionUnnotifiedDetector;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicateComputedEvent;
import com.iexec.core.replicate.ReplicateUpdatedEvent;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.TaskExecutorEngine;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static com.iexec.common.replicate.ReplicateStatusCause.TASK_NOT_ACTIVE;

@Slf4j
@Component
public class ReplicateListeners {

    private TaskExecutorEngine taskExecutorEngine;
    private WorkerService workerService;
    private ContributionUnnotifiedDetector contributionUnnotifiedDetector;
    private ReplicatesService replicatesService;


    public ReplicateListeners(TaskExecutorEngine taskExecutorEngine,
                              WorkerService workerService,
                              ContributionUnnotifiedDetector contributionUnnotifiedDetector,
                              ReplicatesService replicatesService) {
        this.taskExecutorEngine = taskExecutorEngine;
        this.workerService = workerService;
        this.contributionUnnotifiedDetector = contributionUnnotifiedDetector;
        this.replicatesService = replicatesService;
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
        if (event.getNewReplicateStatusCause() != null && event.getNewReplicateStatusCause().equals(TASK_NOT_ACTIVE)) {
            contributionUnnotifiedDetector.detectOnchainContributed();
        }

        /*
         * Should add FAILED status if not completable
         * */
        if (ReplicateStatus.getUncompletableStatuses().contains(event.getNewReplicateStatus())) {
            replicatesService.updateReplicateStatus(event.getChainTaskId(), event.getWalletAddress(), ReplicateStatus.FAILED, ReplicateStatusModifier.POOL_MANAGER);
        }

        /*
         * Should release one CPU for this replicate if status is FAILED
         * */
        if (event.getNewReplicateStatus().equals(ReplicateStatus.FAILED)) {
            workerService.removeChainTaskIdFromWorker(event.getChainTaskId(), event.getWalletAddress());
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
