package com.iexec.core.detector.replicate;

import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.Web3jService;
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
    private final CoreConfigurationService coreConfigurationService;

    public RevealUnnotifiedDetector(TaskService taskService,
                                    ReplicatesService replicatesService,
                                    IexecHubService iexecHubService,
                                    CoreConfigurationService coreConfigurationService,
                                    Web3jService web3jService) {
        super(taskService, replicatesService, iexecHubService, web3jService);
        this.coreConfigurationService = coreConfigurationService;
        dectectWhenTaskStatuses = TaskStatus.getWaitingContributionStatuses();
        offchainCompleting = ReplicateStatus.REVEALING;
        offchainCompleted = ReplicateStatus.REVEALED;
        onchainCompleted = ChainContributionStatus.REVEALED;
    }

    /*
     * Detecting onchain REVEALED only if replicates are offchain REVEALING
     * (worker didn't notify last offchain REVEALED)
     * We want to detect them very often since it's highly probable
     */
    @Scheduled(fixedRateString = "#{coreConfigurationService.unnotifiedRevealDetectorPeriod}")
    public void detectOnchainRevealedWhenOffchainRevealed() {
        log.debug("Detect onchain Revealed (when offchain Revealing) [retryIn:{}]",
                coreConfigurationService.getUnnotifiedContributionDetectorPeriod());
        dectectOnchainCompletedWhenOffchainCompleting(dectectWhenTaskStatuses, offchainCompleting, offchainCompleted, onchainCompleted);
    }

    /*
     * Detecting onchain REVEALED if replicates are not REVEALED
     * (worker didn't notify any status)
     * We want to detect them:
     * - Frequently but no so often since it's eth node resource consuming and less probable
     * - When we receive a CANT_REVEAL
     */
    @Scheduled(fixedRateString = "#{coreConfigurationService.unnotifiedRevealDetectorPeriod*" + DETECTOR_MULTIPLIER + "}")
    public void detectOnchainRevealed() {
        log.debug("Detect onchain Revealed [retryIn:{}]",
                coreConfigurationService.getUnnotifiedContributionDetectorPeriod() * DETECTOR_MULTIPLIER);
        dectectOnchainCompleted(dectectWhenTaskStatuses, offchainCompleting, offchainCompleted, onchainCompleted);
    }

}
