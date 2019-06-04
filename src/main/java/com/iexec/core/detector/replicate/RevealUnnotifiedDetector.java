package com.iexec.core.detector.replicate;

import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.configuration.CoreConfigurationService;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class RevealUnnotifiedDetector extends UnnotifiedAbstractDetector {

    private static final int DETECTOR_MULTIPLIER = 10;
    private final List<TaskStatus> dectectWhenTaskStatuses;
    private final ReplicateStatus offchainCompleting;
    private final ReplicateStatus offchainCompleted;
    private final ChainContributionStatus onchainCompleted;

    public RevealUnnotifiedDetector(TaskService taskService,
                                    ReplicatesService replicatesService,
                                    IexecHubService iexecHubService,
                                    CoreConfigurationService coreConfigurationService) {
        super(taskService, replicatesService, iexecHubService, coreConfigurationService);
        dectectWhenTaskStatuses = TaskStatus.getWaitingContributionStatuses();
        offchainCompleting = ReplicateStatus.REVEALING;
        offchainCompleted = ReplicateStatus.REVEALED;
        onchainCompleted = ChainContributionStatus.REVEALED;
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
        dectectOnchainCompletedWhenOffchainCompleting(dectectWhenTaskStatuses, offchainCompleting, offchainCompleted, onchainCompleted);
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
        dectectOnchainCompleted(dectectWhenTaskStatuses, offchainCompleting, offchainCompleted, onchainCompleted);
    }

}
