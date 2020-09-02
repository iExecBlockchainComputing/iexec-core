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

package com.iexec.core.contribution;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class ContributionService {

    private final ReplicatesService replicatesService;

    public ContributionService(ReplicatesService replicatesService) {
        this.replicatesService = replicatesService;
    }

    /*
     *
     * Get weight of a contributed
     *
     * */
    int getContributedWeight(String chainTaskId, String contribution) {
        int groupWeight = 0;
        for (Replicate replicate : replicatesService.getReplicates(chainTaskId)) {

            Optional<ReplicateStatus> lastRelevantStatus = replicate.getLastRelevantStatus();
            if (!lastRelevantStatus.isPresent()) {
                continue;
            }

            boolean isContributed = lastRelevantStatus.get().equals(ReplicateStatus.CONTRIBUTED);
            boolean haveSameContribution = contribution.equals(replicate.getContributionHash());
            boolean hasWeight = replicate.getWorkerWeight() > 0;

            if (isContributed && haveSameContribution && hasWeight) {
                groupWeight = Math.max(groupWeight, 1) * replicate.getWorkerWeight();
            }
        }
        return groupWeight;
    }

    /*
     *
     * Should exclude workers that have not CONTRIBUTED yet after t=date(CREATED)+1T
     *
     * */
    int getPendingWeight(String chainTaskId, long maxExecutionTime) {
        int pendingGroupWeight = 0;

        for (Replicate replicate : replicatesService.getReplicates(chainTaskId)) {

            Optional<ReplicateStatus> lastRelevantStatus = replicate.getLastRelevantStatus();
            if (!lastRelevantStatus.isPresent()) {
                continue;
            }

            boolean isCreatedLessThanOnePeriodAgo = !replicate.isCreatedMoreThanNPeriodsAgo(1, maxExecutionTime);
            boolean isNotContributed = !lastRelevantStatus.get().equals(ReplicateStatus.CONTRIBUTED);
            boolean isNotFailed = !lastRelevantStatus.get().equals(ReplicateStatus.FAILED);
            boolean hasWeight = replicate.getWorkerWeight() > 0;

            if (isCreatedLessThanOnePeriodAgo && isNotContributed && isNotFailed && hasWeight) {
                pendingGroupWeight = Math.max(pendingGroupWeight, 1) * replicate.getWorkerWeight();
            }
        }
        return pendingGroupWeight;
    }

    /*
     *
     * Retrieves distinct contributions
     *
     * */
    Set<String> getDistinctContributions(String chainTaskId) {

        Set<String> distinctContributions = new HashSet<>();

        for (Replicate replicate : replicatesService.getReplicates(chainTaskId)) {

            Optional<ReplicateStatus> lastRelevantStatus = replicate.getLastRelevantStatus();
            if (!lastRelevantStatus.isPresent()) {
                continue;
            }

            if (lastRelevantStatus.get().equals(ReplicateStatus.CONTRIBUTED)) {
                distinctContributions.add(replicate.getContributionHash());
            }
        }
        return distinctContributions;
    }


}