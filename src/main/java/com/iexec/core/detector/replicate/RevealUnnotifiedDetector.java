package com.iexec.core.detector.replicate;

import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.detector.Detector;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static com.iexec.common.replicate.ReplicateStatus.REVEALED;

@Slf4j
@Service
public class RevealUnnotifiedDetector implements Detector {

    private TaskService taskService;
    private ReplicatesService replicatesService;
    private IexecHubService iexecHubService;

    public RevealUnnotifiedDetector(TaskService taskService,
                                    ReplicatesService replicatesService,
                                    IexecHubService iexecHubService) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
        this.iexecHubService = iexecHubService;
    }


    @Scheduled(fixedRateString = "${detector.reveal.unnotified.period}")
    public void detect() {
        log.debug("Trying to detectUnNotifiedRevealed");
        //check if a worker has revealed on-chain but hasn't notified off-chain
        for (Task task : taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())) {
            String taskId = task.getChainTaskId();
            for (Replicate replicate : replicatesService.getReplicates(taskId)) {
                boolean isStatusRevealedOffChain = replicate.containsStatus(REVEALED);
                boolean isConsensusReachedLongAgo = task.isConsensusReachedSinceMultiplePeriods(1);
                String wallet = replicate.getWalletAddress();

                if (!isStatusRevealedOffChain && isConsensusReachedLongAgo &&
                        iexecHubService.doesWishedStatusMatchesOnChainStatus(taskId, wallet, ChainContributionStatus.REVEALED)) {
                    replicatesService.updateReplicateStatus(taskId, wallet, REVEALED, ReplicateStatusModifier.POOL_MANAGER);
                }
            }
        }
    }
}
