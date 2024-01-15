/*
 * Copyright 2023-2024 IEXEC BLOCKCHAIN TECH
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
import com.iexec.commons.poco.chain.ChainContributionStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.configuration.CronConfiguration;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ContributionAndFinalizationUnnotifiedDetector extends UnnotifiedAbstractDetector {

    public ContributionAndFinalizationUnnotifiedDetector(TaskService taskService,
                                                         ReplicatesService replicatesService,
                                                         IexecHubService iexecHubService,
                                                         CronConfiguration cronConfiguration) {
        super(
                taskService,
                replicatesService,
                iexecHubService,
                TaskStatus.getWaitingContributionStatuses(),
                ReplicateStatus.CONTRIBUTE_AND_FINALIZE_ONGOING,
                ReplicateStatus.CONTRIBUTE_AND_FINALIZE_DONE,
                ChainContributionStatus.REVEALED,
                cronConfiguration.getContributeAndFinalize());
    }

    @Override
    @Scheduled(fixedRateString = "#{@cronConfiguration.getContributeAndFinalize()}")
    public void detectOnChainChanges() {
        super.detectOnChainChanges();
    }

    @Override
    protected final boolean checkDetectionIsValid(Replicate replicate) {
        return iexecHubService.getTaskDescription(replicate.getChainTaskId()).isEligibleToContributeAndFinalize();
    }

    @Override
    protected boolean detectStatusReachedOnChain(Replicate replicate) {
        return iexecHubService.isRevealed(replicate.getChainTaskId(), replicate.getWalletAddress());
    }
}
