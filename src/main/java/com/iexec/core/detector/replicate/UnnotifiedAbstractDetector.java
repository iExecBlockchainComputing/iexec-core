package com.iexec.core.detector.replicate;

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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatus.WORKER_LOST;
import static com.iexec.common.replicate.ReplicateStatus.getMissingStatuses;

@Slf4j
@Service
public abstract class UnnotifiedAbstractDetector {


    protected TaskService taskService;
    protected ReplicatesService replicatesService;
    protected IexecHubService iexecHubService;

    public UnnotifiedAbstractDetector(TaskService taskService,
                                      ReplicatesService replicatesService,
                                      IexecHubService iexecHubService) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
        this.iexecHubService = iexecHubService;
    }

    void dectectOnchainCompletedWhenOffchainCompleting(List<TaskStatus> dectectWhenOffchainTaskStatuses,
                                                       ReplicateStatus offchainCompleting,
                                                       ReplicateStatus offchainCompleted,
                                                       ChainContributionStatus onchainCompleted) {
        for (Task task : taskService.findByCurrentStatus(dectectWhenOffchainTaskStatuses)) {
            for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                Optional<ReplicateStatus> lastRelevantStatus = replicate.getLastRelevantStatus();

                if (!lastRelevantStatus.isPresent()) {
                    continue;
                }

                boolean isReplicateStatusCompleting = lastRelevantStatus.get().equals(offchainCompleting);

                if (isReplicateStatusCompleting && iexecHubService.doesWishedStatusMatchesOnChainStatus(task.getChainTaskId(), replicate.getWalletAddress(), onchainCompleted)) {
                    updateReplicateStatuses(task.getChainTaskId(), replicate, offchainCompleted);
                }
            }
        }
    }

    void dectectOnchainCompleted(List<TaskStatus> dectectWhenOffchainTaskStatuses,
                                 ReplicateStatus offchainCompleting,
                                 ReplicateStatus offchainCompleted,
                                 ChainContributionStatus onchainCompleted) {
        for (Task task : taskService.findByCurrentStatus(dectectWhenOffchainTaskStatuses)) {
            for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                Optional<ReplicateStatus> lastRelevantStatus = replicate.getLastRelevantStatus();

                if (!lastRelevantStatus.isPresent()) {
                    continue;
                }

                boolean isNotOffChainCompleted = !lastRelevantStatus.get().equals(offchainCompleted);//avoid eth node call if already contributed

                if (isNotOffChainCompleted && iexecHubService.doesWishedStatusMatchesOnChainStatus(task.getChainTaskId(), replicate.getWalletAddress(), onchainCompleted)) {
                    updateReplicateStatuses(task.getChainTaskId(), replicate, offchainCompleted);
                }
            }
        }
    }

    void updateReplicateStatuses(String chainTaskId, Replicate replicate, ReplicateStatus offchainCompleted) {
        List<ReplicateStatus> statusesToUpdate;
        if (replicate.getCurrentStatus().equals(WORKER_LOST)) {
            statusesToUpdate = getMissingStatuses(replicate.getLastButOneStatus(), offchainCompleted);
        } else {
            statusesToUpdate = getMissingStatuses(replicate.getCurrentStatus(), offchainCompleted);
        }

        for (ReplicateStatus statusToUpdate : statusesToUpdate) {
            replicatesService.updateReplicateStatus(chainTaskId, replicate.getWalletAddress(),
                    statusToUpdate, ReplicateStatusModifier.POOL_MANAGER);
        }
    }


}
