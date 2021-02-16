/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

import java.util.List;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatus.WORKER_LOST;
import static com.iexec.common.replicate.ReplicateStatus.getMissingStatuses;

@Slf4j
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

    void dectectOnchainCompletedWhenOffchainCompleting(List<TaskStatus> detectWhenOffChainTaskStatuses,
                                                       ReplicateStatus offchainCompleting,
                                                       ReplicateStatus offchainCompleted,
                                                       ChainContributionStatus onchainCompleted) {
        for (Task task : taskService.findByCurrentStatus(detectWhenOffChainTaskStatuses)) {
            for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                Optional<ReplicateStatus> lastRelevantStatus = replicate.getLastRelevantStatus();
                if (!lastRelevantStatus.isPresent() || !lastRelevantStatus.get().equals(offchainCompleting)) {
                    continue;
                }

                boolean statusTrueOnChain = iexecHubService.isStatusTrueOnChain(task.getChainTaskId(), replicate.getWalletAddress(), onchainCompleted);

                if (statusTrueOnChain) {
                    log.info("Detected confirmed missing update (replicate) [is:{}, should:{}, taskId:{}]",
                            lastRelevantStatus.get(), onchainCompleted, task.getChainTaskId());
                    updateReplicateStatuses(task, replicate, offchainCompleted);
                }
            }
        }
    }

    void dectectOnchainCompleted(List<TaskStatus> detectWhenOffChainTaskStatuses,
                                 ReplicateStatus offchainCompleting,
                                 ReplicateStatus offchainCompleted,
                                 ChainContributionStatus onchainCompleted) {
        for (Task task : taskService.findByCurrentStatus(detectWhenOffChainTaskStatuses)) {
            for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                Optional<ReplicateStatus> lastRelevantStatus = replicate.getLastRelevantStatus();

                if (!lastRelevantStatus.isPresent() || lastRelevantStatus.get().equals(offchainCompleted)) {
                    continue;
                }

                boolean statusTrueOnChain = iexecHubService.isStatusTrueOnChain(task.getChainTaskId(), replicate.getWalletAddress(), onchainCompleted);

                if (statusTrueOnChain) {
                    log.info("Detected confirmed missing update (replicate) [is:{}, should:{}, taskId:{}]",
                            lastRelevantStatus.get(), onchainCompleted, task.getChainTaskId());
                    updateReplicateStatuses(task, replicate, offchainCompleted);
                }
            }
        }
    }

    /*
     * This method should stay private. We need to insure that
     * it is only called by the POOL_MANAGER.
     * The POOL_MANAGER has already verified the status onchain
     * in the caller method so this update can happen even if
     * we couldn't get the metadata (block number) of the tx.
     * In this case we put 0 as default block number.
     */
    private void updateReplicateStatuses(Task task, Replicate replicate, ReplicateStatus offchainCompleted) {
        String chainTaskId = task.getChainTaskId();
        long initBlockNumber = task.getInitializationBlockNumber();
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
                            wallet, initBlockNumber);
                    long contributedBlockNumber = contributedBlock != null ? contributedBlock.getBlockNumber() : 0;
                    replicatesService.updateReplicateStatus(chainTaskId, wallet,
                            statusToUpdate, new ReplicateStatusDetails(contributedBlockNumber));
                    break;
                case REVEALED:
                    // retrieve the reveal block for that wallet
                    ChainReceipt revealedBlock = iexecHubService.getRevealBlock(chainTaskId, wallet,
                            initBlockNumber);
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
