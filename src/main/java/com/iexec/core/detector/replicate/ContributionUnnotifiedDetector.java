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
public class ContributionUnnotifiedDetector extends UnnotifiedAbstractDetector {

    private static final int DETECTOR_MULTIPLIER = 10;
    private final List<TaskStatus> dectectWhenTaskStatuses;
    private final ReplicateStatus offchainCompleting;
    private final ReplicateStatus offchainCompleted;
    private final ChainContributionStatus onchainCompleted;

    public ContributionUnnotifiedDetector(TaskService taskService,
                                          ReplicatesService replicatesService,
                                          IexecHubService iexecHubService,
                                          CoreConfigurationService coreConfigurationService) {
        super(taskService, replicatesService, iexecHubService, coreConfigurationService);
        dectectWhenTaskStatuses = TaskStatus.getWaitingContributionStatuses();
        offchainCompleting = ReplicateStatus.CONTRIBUTING;
        offchainCompleted = ReplicateStatus.CONTRIBUTED;
        onchainCompleted = ChainContributionStatus.CONTRIBUTED;
    }
    
    /*
     * Detecting on-chain CONTRIBUTED only if replicates are CONTRIBUTING off-chain
     * (worker didn't notify last off-chain CONTRIBUTING)
     * We want to detect them very often since it's highly probable
     */
    @Scheduled(fixedRateString = "#{coreConfigurationService.unnotifiedContributionDetectorPeriod}")
    public void detectIfOnChainContributedHappenedAfterContributing() {
        log.debug("Detect OnChain Contributed On OffChain Contributing Status [retryIn:{}]",
                coreConfigurationService.getUnnotifiedContributionDetectorPeriod());
        dectectOnchainCompletedWhenOffchainCompleting(dectectWhenTaskStatuses, offchainCompleting, offchainCompleted, onchainCompleted);
    }

    /*
     * Detecting on-chain CONTRIBUTED if replicates are off-chain pre CONTRIBUTING
     * (worker didn't notify any status before off-chain CONTRIBUTING)
     * We want to detect them:
     * - Frequently but no so often since it's eth node resource consuming and less probable
     * - When we receive a CANT_CONTRIBUTE_SINCE_TASK_NOT_ACTIVE
     */
    @Scheduled(fixedRateString = "#{coreConfigurationService.unnotifiedContributionDetectorPeriod*" + DETECTOR_MULTIPLIER + "}")
    public void detectIfOnChainContributedHappened() {
        log.debug("Detect OnChain Contributed On OffChain Pre Contributing Status [retryIn:{}]",
                coreConfigurationService.getUnnotifiedContributionDetectorPeriod() * DETECTOR_MULTIPLIER);

        dectectOnchainCompleted(dectectWhenTaskStatuses, offchainCompleting, offchainCompleted, onchainCompleted);
    }

}
