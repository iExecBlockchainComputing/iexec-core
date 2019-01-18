package com.iexec.core.detector;

import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.chain.IexecHubService;
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

import static com.iexec.common.replicate.ReplicateStatus.CONTRIBUTED;
import static com.iexec.common.replicate.ReplicateStatus.getMissingStatuses;

@Slf4j
@Service
public class ContributionUnnotifiedDetector implements Detector {

    private TaskService taskService;
    private ReplicatesService replicatesService;
    private IexecHubService iexecHubService;

    public ContributionUnnotifiedDetector(TaskService taskService,
                                          ReplicatesService replicatesService,
                                          IexecHubService iexecHubService) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
        this.iexecHubService = iexecHubService;
    }

    @Scheduled(fixedRateString = "${detector.contribution.unnotified.period}")
    @Override
    public void detect() {
        log.info("Trying to detect un-notified contributed");
        for (Task task : taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))) {
            boolean doesTaskNeedUpdate = false;
            for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                //check if a worker has contributed on-chain but hasn't notified off-chain
                boolean hasReplicateContributedOffChain = replicate.containsContributedStatus();
                boolean isReplicateOld = replicate.isCreatedMoreThanNPeriodsAgo(1, task.getTimeRef());

                if (!hasReplicateContributedOffChain && isReplicateOld &&
                        iexecHubService.checkContributionStatus(task.getChainTaskId(), replicate.getWalletAddress(), ChainContributionStatus.CONTRIBUTED)) {
                    updateReplicateStatuses(task.getChainTaskId(), replicate);
                    doesTaskNeedUpdate = true;
                }
            }
            if (doesTaskNeedUpdate) {
                taskService.tryToMoveTaskToNextStatus(task);
            }
        }
    }

    private void updateReplicateStatuses(String chainTaskId, Replicate replicate) {
        List<ReplicateStatus> statusesToUpdate = getMissingStatuses(replicate.getCurrentStatus(), CONTRIBUTED);
        for (ReplicateStatus statusToUpdate : statusesToUpdate) {
            replicatesService.updateReplicateStatus(chainTaskId, replicate.getWalletAddress(),
                    statusToUpdate, ReplicateStatusModifier.POOL_MANAGER);
        }
    }


}
