/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.commons.poco.chain.ChainContributionStatus;
import com.iexec.commons.poco.chain.ChainReceipt;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.iexec.common.replicate.ReplicateStatus.*;

@Slf4j
public abstract class UnnotifiedAbstractDetector {
    private static final int LESS_OFTEN_DETECTOR_FREQUENCY = 10;

    private final TaskService taskService;
    private final ReplicatesService replicatesService;
    private final IexecHubService iexecHubService;

    private final List<TaskStatus> detectWhenOffChainTaskStatuses;
    private final ReplicateStatus offchainOngoing;
    private final ReplicateStatus offchainDone;
    private final ChainContributionStatus onchainDone;
    private final int detectorRate;

    private int detectorOccurrence = 0;

    protected UnnotifiedAbstractDetector(TaskService taskService,
                                         ReplicatesService replicatesService,
                                         IexecHubService iexecHubService,
                                         List<TaskStatus> detectWhenOffChainTaskStatuses,
                                         ReplicateStatus offchainOngoing,
                                         ReplicateStatus offchainDone,
                                         ChainContributionStatus onchainDone,
                                         int detectorRate) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
        this.iexecHubService = iexecHubService;

        this.detectWhenOffChainTaskStatuses = detectWhenOffChainTaskStatuses;
        this.offchainOngoing = offchainOngoing;
        this.offchainDone = offchainDone;
        this.onchainDone = onchainDone;
        this.detectorRate = detectorRate;
    }

    /**
     * Detects the following issues:
     * <ul>
     * <li>`onchainDone` status only if replicates are in `offchainOngoing` status
     * <li>`onchainDone` if replicates are not in `offchainDone` status
     * </ul>
     * The second detection is not always ran, depending on the detector run occurrences.
     */
    void detectOnChainChanges() {
        detectOnchainDoneWhenOffchainOngoing();

        detectorOccurrence++;
        if (detectorOccurrence % LESS_OFTEN_DETECTOR_FREQUENCY == 0) {
            detectOnchainDone();
        }
    }

    /**
     * Detecting `onchainDone` status only if replicates are `offchainOngoing`
     * (worker didn't notify last offchain `offchainDone` status)
     * We want to detect them very often since it's highly probable
     */
    void detectOnchainDoneWhenOffchainOngoing() {
        log.debug("Detect onchain {} (when offchain {}) [retryIn:{}]",
                this.onchainDone, this.offchainOngoing, this.detectorRate);

        for (Task task : taskService.findByCurrentStatus(detectWhenOffChainTaskStatuses)) {
            replicatesService.getReplicates(task.getChainTaskId()).stream()
                    .filter(replicate -> replicate.getLastRelevantStatus() == offchainOngoing)
                    .filter(this::checkDetectionIsValid)
                    .filter(this::detectStatusReachedOnChain)
                    .forEach(replicate -> updateReplicateStatuses(task, replicate));
        }
    }

    /**
     * Detecting `onchainDone` if replicates are not in `offchainDone` status
     * (worker didn't notify any status)
     * We want to detect them:
     * - Frequently but no so often since it's eth node resource consuming and less probable
     * - When we receive a "can't do &lt;action&gt;" relative to the `onchainDone` status (e.g.: `CANNOT_REVEAL`)
     */
    public void detectOnchainDone() {
        log.debug("Detect onchain {} [retryIn:{}]", onchainDone, this.detectorRate * LESS_OFTEN_DETECTOR_FREQUENCY);
        for (Task task : taskService.findByCurrentStatus(detectWhenOffChainTaskStatuses)) {
            replicatesService.getReplicates(task.getChainTaskId()).stream()
                    .filter(replicate -> replicate.getLastRelevantStatus() != offchainDone)
                    .filter(this::checkDetectionIsValid)
                    .filter(this::detectStatusReachedOnChain)
                    .forEach(replicate -> updateReplicateStatuses(task, replicate));
        }
    }

    /**
     * Checks replicate eligibility to a detection against an {@code offchainDone} status.
     * <p>
     * All replicates are eligible to detection against {@code REVEALED}, but not against {@code CONTRIBUTED} or
     * {@code CONTRIBUTED_AND_FINALIZED}.
     *
     * @param replicate The replicate to check
     * @return {@literal true} if the replicate is eligible, {@literal false} otherwise
     */
    private boolean checkDetectionIsValid(Replicate replicate) {
        final boolean isEligibleToContributeAndFinalize = iexecHubService.getTaskDescription(replicate.getChainTaskId())
                .isEligibleToContributeAndFinalize();
        return offchainDone == REVEALED
                || (!isEligibleToContributeAndFinalize && offchainDone == CONTRIBUTED)
                || (isEligibleToContributeAndFinalize && offchainDone == CONTRIBUTE_AND_FINALIZE_DONE);
    }

    /**
     * Checks if {@code onchainDone} status has been reached on blockchain network.
     *
     * @param replicate Replicate whose on-chain status will be checked
     * @return {@literal true} if given status has been found on-chain, {@literal false} otherwise.
     */
    private boolean detectStatusReachedOnChain(Replicate replicate) {
        final String chainTaskId = replicate.getChainTaskId();
        final String walletAddress = replicate.getWalletAddress();
        switch (onchainDone) {
            case CONTRIBUTED:
                return iexecHubService.isContributed(chainTaskId, walletAddress);
            case REVEALED:
                return iexecHubService.isRevealed(chainTaskId, walletAddress);
            default:
                return false;
        }
    }

    /*
     * This method should stay private. We need to ensure that
     * it is only called by the POOL_MANAGER.
     * The POOL_MANAGER has already verified the status onchain
     * in the caller method so this update can happen even if
     * we couldn't get the metadata (block number) of the tx.
     * In this case we put 0 as default block number.
     */
    private void updateReplicateStatuses(Task task, Replicate replicate) {
        final String chainTaskId = task.getChainTaskId();
        final long initBlockNumber = task.getInitializationBlockNumber();
        final ReplicateStatus lastRelevantStatus = replicate.getLastRelevantStatus();
        final List<ReplicateStatus> statusesToUpdate = getMissingStatuses(lastRelevantStatus, offchainDone);
        final String wallet = replicate.getWalletAddress();

        log.info("Detected confirmed missing update (replicate) [is:{}, should:{}, taskId:{}]",
                lastRelevantStatus, onchainDone, task.getChainTaskId());

        for (ReplicateStatus statusToUpdate : statusesToUpdate) {
            // add details to the update if needed
            ReplicateStatusDetails details = null;
            switch (statusToUpdate) {
                case CONTRIBUTED:
                    // retrieve the contribution block for that wallet
                    final ChainReceipt contributedBlock = iexecHubService.getContributionBlock(chainTaskId,
                            wallet, initBlockNumber);
                    final long contributedBlockNumber = contributedBlock != null ? contributedBlock.getBlockNumber() : 0;
                    details = new ReplicateStatusDetails(contributedBlockNumber);
                    break;
                case REVEALED:
                    // retrieve the reveal block for that wallet
                    final ChainReceipt revealedBlock = iexecHubService.getRevealBlock(chainTaskId, wallet,
                            initBlockNumber);
                    final long revealedBlockNumber = revealedBlock != null ? revealedBlock.getBlockNumber() : 0;
                    details = new ReplicateStatusDetails(revealedBlockNumber);
                    break;
                case CONTRIBUTE_AND_FINALIZE_DONE:
                    // retrieve the finalize block
                    final ChainReceipt finalizeBlock = iexecHubService.getFinalizeBlock(chainTaskId, initBlockNumber);
                    final long finalizeBlockNumber = finalizeBlock != null ? finalizeBlock.getBlockNumber() : 0;
                    details = new ReplicateStatusDetails(finalizeBlockNumber);
                    break;
                default:
                    // by default, no need to retrieve anything
                    break;
            }
            replicatesService.updateReplicateStatus(chainTaskId, wallet, statusToUpdate, details);
        }
    }
}
