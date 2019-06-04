package com.iexec.core.detector.replicate;

import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.configuration.CoreConfigurationService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatus.CONTRIBUTED;

@Slf4j
@Service
public class RevealUnnotifiedDetector {//implements Detector {

    private static final int DETECTOR_MULTIPLIER = 10;
    private TaskService taskService;
    private ReplicatesService replicatesService;
    private IexecHubService iexecHubService;
    private CoreConfigurationService coreConfigurationService;

    public RevealUnnotifiedDetector(TaskService taskService,
                                    ReplicatesService replicatesService,
                                    IexecHubService iexecHubService,
                                    CoreConfigurationService coreConfigurationService) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
        this.iexecHubService = iexecHubService;
        this.coreConfigurationService = coreConfigurationService;
    }

    /*
     * Detecting on-chain REVEALED only if replicates are REVEALING off-chain
     * (worker didn't notify after off-chain REVEALING)
     * We want to detect them very often since it's highly probable
     */
    @Scheduled(fixedRateString = "#{coreConfiguration.unnotifiedRevealDetectorPeriod}")
    public void detectIfOnChainRevealedHappenedAfterRevealing() {
        log.debug("Detect OnChain Revealed On OffChain Contributing Status [retryIn:{}]",
                coreConfigurationService.getUnnotifiedContributionDetectorPeriod());
        for (Task task : taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())) {
            for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                Optional<ReplicateStatus> lastRelevantStatus = replicate.getLastRelevantStatus();

                if (!lastRelevantStatus.isPresent()) {
                    continue;
                }

                boolean isReplicateStatusRevealing = lastRelevantStatus.get().equals(REVEALING);

                if (isReplicateStatusRevealing && iexecHubService.doesWishedStatusMatchesOnChainStatus(task.getChainTaskId(), replicate.getWalletAddress(), ChainContributionStatus.REVEALED)) {
                    updateReplicateStatuses(task.getChainTaskId(), replicate);
                }
            }
        }
    }

    /*
     * Detecting on-chain REVEALED if replicates are off-chain pre REVEALING
     * (worker didn't notify any status before off-chain REVEALING)
     * We want to detect them:
     * - Frequently but no so often since it's eth node resource consuming and less probable
     * - When we receive a CANT_REVEAL
     */
    @Scheduled(fixedRateString = "#{coreConfiguration.unnotifiedRevealDetectorPeriod*" + DETECTOR_MULTIPLIER + "}")
    public void detectIfOnChainRevealedHappened() {
        log.debug("Detect OnChain Revealed On OffChain Pre Revealing Status [retryIn:{}]",
                coreConfigurationService.getUnnotifiedContributionDetectorPeriod() * DETECTOR_MULTIPLIER);
        for (Task task : taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())) {
            for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                Optional<ReplicateStatus> lastRelevantStatus = replicate.getLastRelevantStatus();

                if (!lastRelevantStatus.isPresent()) {
                    continue;
                }

                boolean isNotOffChainRevealed = !lastRelevantStatus.get().equals(REVEALED);//avoid eth node call if already contributed

                if (isNotOffChainRevealed && iexecHubService.doesWishedStatusMatchesOnChainStatus(task.getChainTaskId(), replicate.getWalletAddress(), ChainContributionStatus.REVEALED)) {
                    updateReplicateStatuses(task.getChainTaskId(), replicate);
                }
            }
        }
    }

    private void updateReplicateStatuses(String chainTaskId, Replicate replicate) {
        List<ReplicateStatus> statusesToUpdate;
        if (replicate.getCurrentStatus().equals(WORKER_LOST)) {
            statusesToUpdate = getMissingStatuses(replicate.getLastButOneStatus(), REVEALED);
        } else {
            statusesToUpdate = getMissingStatuses(replicate.getCurrentStatus(), REVEALED);
        }

        for (ReplicateStatus statusToUpdate : statusesToUpdate) {
            replicatesService.updateReplicateStatus(chainTaskId, replicate.getWalletAddress(),
                    statusToUpdate, ReplicateStatusModifier.POOL_MANAGER);
        }
    }
}
