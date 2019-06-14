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
public class PredictionService {

    private final ReplicatesService replicatesService;
    private IexecHubService iexecHubService;

    public PredictionService(ReplicatesService replicatesService, IexecHubService iexecHubService) {
        this.replicatesService = replicatesService;
        this.iexecHubService = iexecHubService;
    }

    /*
     *
     * Get weight of a prediction
     *
     * */
    private int getPredictionWeight(String chainTaskId, String contribution) {
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

    /*
     *
     * Sum all prediction weights but exclude best prediction weight
     *
     * */
    private int getAllOtherPredictionsWeight(String chainTaskId, Set<String> distinctContributions) {
        String bestPredictionWeight = getBestPrediction(chainTaskId, distinctContributions).getContribution();

        int allOtherPredictionsWeight = 0;

        for (String contribution : distinctContributions) {
            int predictionWeight = getPredictionWeight(chainTaskId, contribution);

            if (!contribution.equals(bestPredictionWeight)) {
                allOtherPredictionsWeight = allOtherPredictionsWeight + predictionWeight;
            }
        }
        return allOtherPredictionsWeight;
    }

    //TODO: merge method with getPredictionWeight
    /*
     *
     * Should exclude workers that have not CONTRIBUTED yet after t=date(CREATED)+1T
     *
     * */
    private int getPendingWeight(String chainTaskId, long maxExecutionTime) {
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

    private Prediction getBestPrediction(String chainTaskId, Set<String> distinctContributions) {
        Prediction bestPrediction = Prediction.builder().contribution("").weight(0).build();

        for (String predictionContribution : distinctContributions) {
            int predictionWeight = getPredictionWeight(chainTaskId, predictionContribution);

            if (predictionWeight >= bestPrediction.getWeight()) {
                bestPrediction.setContribution(predictionContribution);
                bestPrediction.setWeight(predictionWeight);
            }
        }
        return bestPrediction;
    }

    /*
     *
     * Retrieves distinct contributions
     *
     * */
    private Set<String> getDistinctContributions(String chainTaskId) {

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

    /*
     *
     * Considering pending workers are going to contribute to the best prediction
     *
     * */
    private int getPendingAndBestPredictionWeight(int pendingWeight, int bestPredictionWeight) {
        int pendingAndBestPredictionWeight;
        if (pendingWeight == 0 && bestPredictionWeight == 0) {
            pendingAndBestPredictionWeight = 0;
        } else if (pendingWeight > 0 && bestPredictionWeight == 0) {
            pendingAndBestPredictionWeight = pendingWeight;
        } else if (pendingWeight == 0 && bestPredictionWeight > 0) {
            pendingAndBestPredictionWeight = bestPredictionWeight;
        } else {
            pendingAndBestPredictionWeight = bestPredictionWeight * pendingWeight;
        }
        return pendingAndBestPredictionWeight;
    }

    /*
     *
     * Estimating pending workers are going to contribute to the best prediction
     *
     * Return false means a consensus is not possible now, a new worker is welcome to add more weight
     * Return true means a consensus is possible now, no need to add new workers
     *
     */
    public boolean isConsensusPossibleNow(String chainTaskId, int trust, long maxExecutionTime) {
        trust = Math.max(trust, 1);//ensure trust equals 1
        Set<String> distinctContributions = getDistinctContributions(chainTaskId);
        Prediction bestPrediction = getBestPrediction(chainTaskId, distinctContributions);

        int pendingWeight = getPendingWeight(chainTaskId, maxExecutionTime);
        int bestPredictionWeight = bestPrediction.getWeight();
        int pendingAndBestPredictionWeight = getPendingAndBestPredictionWeight(pendingWeight, bestPredictionWeight);
        int allOtherPredictionsWeight = getAllOtherPredictionsWeight(chainTaskId, distinctContributions);
        int allPredictionsWeight = allOtherPredictionsWeight + pendingAndBestPredictionWeight;

        boolean isConsensusPossibleNow = pendingAndBestPredictionWeight * trust > (1 + allPredictionsWeight) * (trust - 1);

        log.info("Is consensus possible now? [chainTaskId:{}, isConsensusPossibleNow:{}, trust:{}, distinctContributions:{}, " +
                "bestPredictionContribution:{}, bestPredictionWeight:{}, pendingWeight:{}, pendingAndBestPredictionWeight:{}" +
                ", allPredictionsWeight:{}]", chainTaskId, isConsensusPossibleNow, trust, distinctContributions,
                bestPrediction.getContribution(), bestPredictionWeight, pendingWeight, pendingAndBestPredictionWeight, allPredictionsWeight);

        return isConsensusPossibleNow;
    }



}