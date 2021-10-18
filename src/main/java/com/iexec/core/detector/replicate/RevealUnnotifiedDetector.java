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
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.configuration.CronConfiguration;
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

    private static final int LESS_OFTEN_DETECTOR_FREQUENCY = 10;
    private final List<TaskStatus> dectectWhenTaskStatuses;
    private final ReplicateStatus offchainCompleting;
    private final ReplicateStatus offchainCompleted;
    private final ChainContributionStatus onchainCompleted;
    private final CronConfiguration cronConfiguration;

    private int detectorOccurrence;

    public RevealUnnotifiedDetector(TaskService taskService,
                                    ReplicatesService replicatesService,
                                    IexecHubService iexecHubService,
                                    Web3jService web3jService,
                                    CronConfiguration cronConfiguration) {
        super(taskService, replicatesService, iexecHubService, web3jService);
        this.cronConfiguration = cronConfiguration;
        dectectWhenTaskStatuses = TaskStatus.getWaitingRevealStatuses();
        offchainCompleting = ReplicateStatus.REVEALING;
        offchainCompleted = ReplicateStatus.REVEALED;
        onchainCompleted = ChainContributionStatus.REVEALED;
    }

    @Scheduled(fixedRateString = "#{@cronConfiguration.getReveal()}")
    public void detectOnChainChanges() {
        detectOnchainRevealedWhenOffchainRevealed();

        detectorOccurrence++;
        if (detectorOccurrence % LESS_OFTEN_DETECTOR_FREQUENCY == 0) {
            detectOnchainRevealed();
        }
    }

    /*
     * Detecting onchain REVEALED only if replicates are offchain REVEALING
     * (worker didn't notify last offchain REVEALED)
     * We want to detect them very often since it's highly probable
     */
    public void detectOnchainRevealedWhenOffchainRevealed() {
        log.debug("Detect onchain Revealed (when offchain Revealing) [retryIn:{}]",
                cronConfiguration.getReveal());
        dectectOnchainCompletedWhenOffchainCompleting(
                dectectWhenTaskStatuses,
                offchainCompleting,
                offchainCompleted,
                onchainCompleted
        );
    }

    /*
     * Detecting onchain REVEALED if replicates are not REVEALED
     * (worker didn't notify any status)
     * We want to detect them:
     * - Frequently but no so often since it's eth node resource consuming and less probable
     * - When we receive a CANT_REVEAL
     */
    public void detectOnchainRevealed() {
        log.debug("Detect onchain Revealed [retryIn:{}]",
                cronConfiguration.getReveal() * LESS_OFTEN_DETECTOR_FREQUENCY);
        dectectOnchainCompleted(
                dectectWhenTaskStatuses,
                offchainCompleting,
                offchainCompleted,
                onchainCompleted
        );
    }

}
