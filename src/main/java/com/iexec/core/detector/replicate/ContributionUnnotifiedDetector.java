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
public class ContributionUnnotifiedDetector extends UnnotifiedAbstractDetector {

    private static final int LESS_OFTEN_DETECTOR_FREQUENCY = 10;
    private final List<TaskStatus> dectectWhenOffchainTaskStatuses;
    private final ReplicateStatus offchainCompleting;
    private final ReplicateStatus offchainCompleted;
    private final ChainContributionStatus onchainCompleted;
    private final int detectorRate;

    private int detectorOccurrence;

    public ContributionUnnotifiedDetector(TaskService taskService,
                                          ReplicatesService replicatesService,
                                          IexecHubService iexecHubService,
                                          Web3jService web3jService,
                                          CronConfiguration cronConfiguration) {
        super(taskService, replicatesService, iexecHubService, web3jService);
        dectectWhenOffchainTaskStatuses = TaskStatus.getWaitingContributionStatuses();
        offchainCompleting = ReplicateStatus.CONTRIBUTING;
        offchainCompleted = ReplicateStatus.CONTRIBUTED;
        onchainCompleted = ChainContributionStatus.CONTRIBUTED;
        this.detectorRate = cronConfiguration.getContribute();
    }

    /**
     * Detects onchain CONTRIBUTED only if replicates are offchain CONTRIBUTING and
     * onchain CONTRIBUTED if replicates are not CONTRIBUTED.
     * The second detection is not always ran, depending on the detector run occurrences.
     */
    @Scheduled(fixedRateString = "#{@cronConfiguration.getContribute()}")
    public void detectOnChainChanges() {
        detectOnchainContributedWhenOffchainContributing();

        detectorOccurrence++;
        if (detectorOccurrence % LESS_OFTEN_DETECTOR_FREQUENCY == 0) {
            detectOnchainContributed();
        }
    }

    /*
     * Detecting onchain CONTRIBUTED only if replicates are offchain CONTRIBUTING
     * (worker didn't notify last offchain CONTRIBUTED)
     * We want to detect them very often since it's highly probable
     */
    public void detectOnchainContributedWhenOffchainContributing() {
        log.debug("Detect onchain Contributed (when offchain Contributing) [retryIn:{}]",
                this.detectorRate);
        dectectOnchainCompletedWhenOffchainCompleting(
                dectectWhenOffchainTaskStatuses,
                offchainCompleting,
                offchainCompleted,
                onchainCompleted
        );
    }

    /*
     * Detecting onchain CONTRIBUTED if replicates are not CONTRIBUTED
     * (worker didn't notify any status)
     * We want to detect them:
     * - Frequently but no so often since it's eth node resource consuming and less probable
     * - When we receive a CANT_CONTRIBUTE_SINCE_TASK_NOT_ACTIVE
     */
    public void detectOnchainContributed() {
        log.debug("Detect onchain Contributed [retryIn:{}]", this.detectorRate * LESS_OFTEN_DETECTOR_FREQUENCY);
        dectectOnchainCompleted(
                dectectWhenOffchainTaskStatuses,
                offchainCompleting,
                offchainCompleted,
                onchainCompleted
        );
    }

}
