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
    private final List<TaskStatus> dectectWhenOffchainTaskStatuses;
    private final ReplicateStatus offchainCompleting;
    private final ReplicateStatus offchainCompleted;
    private final ChainContributionStatus onchainCompleted;
    private final CoreConfigurationService coreConfigurationService;

    public ContributionUnnotifiedDetector(TaskService taskService,
                                          ReplicatesService replicatesService,
                                          IexecHubService iexecHubService,
                                          CoreConfigurationService coreConfigurationService) {
        super(taskService, replicatesService, iexecHubService);
        this.coreConfigurationService = coreConfigurationService;
        dectectWhenOffchainTaskStatuses = TaskStatus.getWaitingContributionStatuses();
        offchainCompleting = ReplicateStatus.CONTRIBUTING;
        offchainCompleted = ReplicateStatus.CONTRIBUTED;
        onchainCompleted = ChainContributionStatus.CONTRIBUTED;
    }

    /*
     * Detecting onchain CONTRIBUTED only if replicates are offchain CONTRIBUTING
     * (worker didn't notify last offchain CONTRIBUTED)
     * We want to detect them very often since it's highly probable
     */
    @Scheduled(fixedRateString = "#{coreConfigurationService.unnotifiedContributionDetectorPeriod}")
    public void detectOnchainContributedWhenOffchainContributing() {
        log.debug("Detect onchain Contributed (when offchain Contributing) [retryIn:{}]",
                coreConfigurationService.getUnnotifiedContributionDetectorPeriod());
        dectectOnchainCompletedWhenOffchainCompleting(dectectWhenOffchainTaskStatuses, offchainCompleting, offchainCompleted, onchainCompleted);
    }

    /*
     * Detecting onchain CONTRIBUTED if replicates are not CONTRIBUTED
     * (worker didn't notify any status)
     * We want to detect them:
     * - Frequently but no so often since it's eth node resource consuming and less probable
     * - When we receive a CANT_CONTRIBUTE_SINCE_TASK_NOT_ACTIVE
     */
    @Scheduled(fixedRateString = "#{coreConfigurationService.unnotifiedContributionDetectorPeriod*" + DETECTOR_MULTIPLIER + "}")
    public void detectOnchainContributed() {
        log.debug("Detect onchain Contributed [retryIn:{}]",
                coreConfigurationService.getUnnotifiedContributionDetectorPeriod() * DETECTOR_MULTIPLIER);

        dectectOnchainCompleted(dectectWhenOffchainTaskStatuses, offchainCompleting, offchainCompleted, onchainCompleted);
    }

}
