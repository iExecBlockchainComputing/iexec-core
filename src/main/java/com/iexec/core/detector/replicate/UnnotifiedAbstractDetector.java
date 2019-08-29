package com.iexec.core.detector.replicate;

import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.Web3jService;
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

@Service
public abstract class UnnotifiedAbstractDetector {


    protected TaskService taskService;
    protected ReplicatesService replicatesService;
    protected IexecHubService iexecHubService;
    protected Web3jService web3jService;

    public UnnotifiedAbstractDetector(TaskService taskService,
                                      ReplicatesService replicatesService,
                                      IexecHubService iexecHubService,
                                      Web3jService web3jService) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
        this.iexecHubService = iexecHubService;
        this.web3jService = web3jService;
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
                boolean isStatusTrueOnchain = iexecHubService.isStatusTrueOnChain(task.getChainTaskId(),
                        replicate.getWalletAddress(), onchainCompleted);

                if (isReplicateStatusCompleting && isStatusTrueOnchain) {
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

                if (isNotOffChainCompleted && iexecHubService.isStatusTrueOnChain(task.getChainTaskId(), replicate.getWalletAddress(), onchainCompleted)) {
                    updateReplicateStatuses(task.getChainTaskId(), replicate, offchainCompleted);
                }
            }
        }
    }

    private void updateReplicateStatuses(String chainTaskId, Replicate replicate, ReplicateStatus offchainCompleted) {
        List<ReplicateStatus> statusesToUpdate;
        if (replicate.getCurrentStatus().equals(WORKER_LOST)) {
            statusesToUpdate = getMissingStatuses(replicate.getLastButOneStatus(), offchainCompleted);
        } else {
            statusesToUpdate = getMissingStatuses(replicate.getCurrentStatus(), offchainCompleted);
        }

        String wallet = replicate.getWalletAddress();

        for (ReplicateStatus statusToUpdate : statusesToUpdate) {
            // add details to the update if needed
            switch (statusToUpdate) {
                case CONTRIBUTED:
                    // retrieve the contribution block for that wallet
                    ChainReceipt contributedBlock = iexecHubService.getContributionBlock(chainTaskId,
                            wallet, web3jService.getLatestBlockNumber());
                    long contributedBlockNumber = contributedBlock != null ? contributedBlock.getBlockNumber(): 0;
                    replicatesService.updateReplicateStatus(chainTaskId, wallet,
                            statusToUpdate, new ReplicateStatusDetails(contributedBlockNumber));
                    break;
                case REVEALED:
                    // retrieve the reveal block for that wallet
                    ChainReceipt revealedBlock = iexecHubService.getRevealBlock(chainTaskId, wallet,
                            web3jService.getLatestBlockNumber());
                    long revealedBlockNumber = revealedBlock != null ? revealedBlock.getBlockNumber() : 0;
                    replicatesService.updateReplicateStatus(chainTaskId, wallet,
                            statusToUpdate, new ReplicateStatusDetails(revealedBlockNumber));
                    break;
                default:
                    // by default, no need to retrieve anything
                    replicatesService.updateReplicateStatus(chainTaskId, wallet, statusToUpdate);

            }


        }
    }
}
