package com.iexec.core.detector.replicate;

import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.configuration.CoreConfiguration;
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

@Slf4j
@Service
public class ContributionUnnotifiedDetector { //implements Detector {


    private static final int DETECTOR_MULTIPLIER = 10;
    private TaskService taskService;
    private ReplicatesService replicatesService;
    private IexecHubService iexecHubService;
    private CoreConfiguration coreConfiguration;

    public ContributionUnnotifiedDetector(TaskService taskService,
                                          ReplicatesService replicatesService,
                                          IexecHubService iexecHubService,
                                          CoreConfiguration coreConfiguration) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
        this.iexecHubService = iexecHubService;
        this.coreConfiguration = coreConfiguration;
    }

    /*
     * Detecting on-chain CONTRIBUTED only if replicates are CONTRIBUTING off-chain
     * (worker didn't notify last off-chain CONTRIBUTING)
     * We want to detect them very often since it's highly probable
     */
    @Scheduled(fixedRateString = "#{coreConfiguration.unnotifiedContributionDetectorPeriod}")
    public void detectIfOnChainContributedHappenedAfterContributing() {
        log.info("Detect OnChain Contributed On OffChain Contributing Status [retryIn:{}]",
                coreConfiguration.getUnnotifiedContributionDetectorPeriod());
        for (Task task : taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))) {
            for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                Optional<ReplicateStatus> lastRelevantStatus = replicate.getLastRelevantStatus();

                if (!lastRelevantStatus.isPresent()) {
                    continue;
                }

                boolean isReplicateStatusContributing = lastRelevantStatus.get().equals(ReplicateStatus.CONTRIBUTING);

                if (isReplicateStatusContributing && iexecHubService.doesWishedStatusMatchesOnChainStatus(task.getChainTaskId(), replicate.getWalletAddress(), ChainContributionStatus.CONTRIBUTED)) {
                    updateReplicateStatuses(task.getChainTaskId(), replicate);
                }
            }
        }
    }

    /*
     * Detecting on-chain CONTRIBUTED if replicates are off-chain pre CONTRIBUTING
     * (worker didn't notify any status before off-chain CONTRIBUTING)
     * We want to detect them:
     * - Frequently but no so often since it's eth node resource consuming and less probable
     * - When we receive a CANT_CONTRIBUTE_SINCE_TASK_NOT_ACTIVE
     */
    @Scheduled(fixedRateString = "#{coreConfiguration.unnotifiedContributionDetectorPeriod*" + DETECTOR_MULTIPLIER + "}")
    public void detectIfOnChainContributedHappened() {
        log.info("Detect OnChain Contributed On OffChain Pre Contributing Status [retryIn:{}]",
                coreConfiguration.getUnnotifiedContributionDetectorPeriod() * DETECTOR_MULTIPLIER);
        for (Task task : taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))) {
            for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                Optional<ReplicateStatus> lastRelevantStatus = replicate.getLastRelevantStatus();

                if (!lastRelevantStatus.isPresent()) {
                    continue;
                }

                boolean isNotOffChainContributed = !lastRelevantStatus.get().equals(ReplicateStatus.CONTRIBUTED);//avoid eth node call if already contributed

                if (isNotOffChainContributed && iexecHubService.doesWishedStatusMatchesOnChainStatus(task.getChainTaskId(), replicate.getWalletAddress(), ChainContributionStatus.CONTRIBUTED)) {
                    updateReplicateStatuses(task.getChainTaskId(), replicate);
                }
            }
        }
    }

    private void updateReplicateStatuses(String chainTaskId, Replicate replicate) {
        List<ReplicateStatus> statusesToUpdate;
        if (replicate.getCurrentStatus().equals(WORKER_LOST)) {
            statusesToUpdate = getMissingStatuses(replicate.getLastButOneStatus(), CONTRIBUTED);
        } else {
            statusesToUpdate = getMissingStatuses(replicate.getCurrentStatus(), CONTRIBUTED);
        }

        for (ReplicateStatus statusToUpdate : statusesToUpdate) {
            replicatesService.updateReplicateStatus(chainTaskId, replicate.getWalletAddress(),
                    statusToUpdate, ReplicateStatusModifier.POOL_MANAGER);
        }
    }


}
