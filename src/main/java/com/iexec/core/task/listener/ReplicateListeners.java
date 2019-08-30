package com.iexec.core.task.listener;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.core.detector.replicate.ContributionUnnotifiedDetector;
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
        ReplicateStatusUpdate statusUpdate = event.getReplicateStatusUpdate();
        ReplicateStatus newStatus = statusUpdate.getStatus();
        ReplicateStatusCause cause = statusUpdate.getDetails() != null ? statusUpdate.getDetails().getCause(): null;

        taskExecutorEngine.updateTask(event.getChainTaskId());

        if (newStatus.equals(ReplicateStatus.COMPUTED)) {
            workerService.removeComputedChainTaskIdFromWorker(event.getChainTaskId(), event.getWalletAddress());
        }

        /*
         * A CONTRIBUTE_FAILED status with the cause TASK_NOT_ACTIVE means this new worker have been
         * authorized to contribute (but couldn't) while we had a consensus_reached onchain but not
         * in database (meaning another worker didn't notify he had contributed).
         * We should start a detector which will look for unnotified contributions and will upgrade
         * task to consensus_reached
         */
        if (cause != null && cause.equals(TASK_NOT_ACTIVE)) {
            contributionUnnotifiedDetector.detectOnchainContributed();
        }

        /*
         * Should add FAILED status if not completable
         * */
        if (ReplicateStatus.getUncompletableStatuses().contains(newStatus)) {
            replicatesService.updateReplicateStatus(event.getChainTaskId(),
                    event.getWalletAddress(), ReplicateStatus.FAILED);
        }

        /*
         * Should release one CPU for this replicate if status is FAILED
         * */
        if (newStatus.equals(ReplicateStatus.FAILED)) {
            workerService.removeChainTaskIdFromWorker(event.getChainTaskId(), event.getWalletAddress());
        }
    }

}
