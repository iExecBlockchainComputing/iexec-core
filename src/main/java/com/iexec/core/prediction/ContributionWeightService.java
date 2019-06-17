package com.iexec.core.prediction;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class ContributionWeightService {

    private final ReplicatesService replicatesService;
    private IexecHubService iexecHubService;

    public ContributionWeightService(ReplicatesService replicatesService, IexecHubService iexecHubService) {
        this.replicatesService = replicatesService;
        this.iexecHubService = iexecHubService;
    }

    /*
     *
     * Get weight of a contributed
     *
     * */
    public int getContributedWeight(String chainTaskId, String contribution) {
        int groupWeight = 0;
        for (Replicate replicate : replicatesService.getReplicates(chainTaskId)) {

            Optional<ReplicateStatus> lastRelevantStatus = replicate.getLastRelevantStatus();
            if (!lastRelevantStatus.isPresent()) {
                continue;
            }

            boolean isContributed = lastRelevantStatus.get().equals(ReplicateStatus.CONTRIBUTED);
            boolean haveSameContribution = contribution.equals(replicate.getContributionHash());

            if (isContributed && haveSameContribution) {
                int workerWeight = iexecHubService.getWorkerWeight(replicate.getWalletAddress());

                groupWeight = Math.max(groupWeight, 1) * workerWeight;
            }
        }
        return groupWeight;
    }

    //TODO: merge method with getContributedWeight
    /*
     *
     * Should exclude workers that have not CONTRIBUTED yet after t=date(CREATED)+1T
     *
     * */
    public int getPendingWeight(String chainTaskId, long maxExecutionTime) {
        int pendingGroupWeight = 0;

        for (Replicate replicate : replicatesService.getReplicates(chainTaskId)) {

            Optional<ReplicateStatus> lastRelevantStatus = replicate.getLastRelevantStatus();
            if (!lastRelevantStatus.isPresent()) {
                continue;
            }

            boolean isCreatedLessThanOnePeriodAgo = !replicate.isCreatedMoreThanNPeriodsAgo(1, maxExecutionTime);
            boolean isNotContributed = !lastRelevantStatus.get().equals(ReplicateStatus.CONTRIBUTED);
            boolean isNotFailed = !lastRelevantStatus.get().equals(ReplicateStatus.FAILED);

            if (isCreatedLessThanOnePeriodAgo && isNotContributed && isNotFailed) {
                int workerWeight = iexecHubService.getWorkerWeight(replicate.getWalletAddress());

                pendingGroupWeight = Math.max(pendingGroupWeight, 1) * workerWeight;
            }
        }
        return pendingGroupWeight;
    }

    /*
     *
     * Retrieves distinct contributions
     *
     * */
    public Set<String> getDistinctContributions(String chainTaskId) {

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