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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ContributionHelper {

    private ContributionHelper() {
    }

    /*
     *
     * Get weight of a contributed
     *
     * */
    static int getContributedWeight(List<Replicate> replicates, String contribution) {
        int groupWeight = 0;
        for (Replicate replicate : replicates) {

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
    static int getPendingWeight(List<Replicate> replicates, long maxExecutionTime) {
        int pendingGroupWeight = 0;

        for (Replicate replicate : replicates) {

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
    static Set<String> getDistinctContributions(List<Replicate> replicates) {

        Set<String> distinctContributions = new HashSet<>();

        for (Replicate replicate : replicates) {

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