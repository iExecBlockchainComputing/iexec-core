package com.iexec.core.contribution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ConsensusService {

    private PredictionService predictionService;

    public ConsensusService(PredictionService predictionService) {
        this.predictionService = predictionService;
    }


    /*
     *
     * Estimating pending workers are going to contribute to the best prediction
     *
     * Return true means a consensus is not possible now, a new worker is welcome to add more weight
     * Return false means a consensus is possible now, no need to add new workers
     *
     */
    public boolean doesTaskNeedMoreContributionsForConsensus(String chainTaskId, int trust, long maxExecutionTime) {
        trust = Math.max(trust, 1);//ensure trust equals 1

        int bestPredictionWeight = predictionService.getBestPredictionWeight(chainTaskId, maxExecutionTime);
        int worstPredictionsWeight = predictionService.getWorstPredictionsWeight(chainTaskId);

        int allPredictionsWeight = worstPredictionsWeight + bestPredictionWeight;

        boolean needsMoreContributions = !isConsensusPossibleNow(trust, bestPredictionWeight, allPredictionsWeight);

        if (needsMoreContributions){
            log.info("More contributions needed [chainTaskId:{}, trust:{}, bestPredictionWeight:{}, " +
                            "allPredictionsWeight:{}]", chainTaskId, trust, bestPredictionWeight, allPredictionsWeight);
        }

        return needsMoreContributions;
    }

    private boolean isConsensusPossibleNow(int trust, int pendingAndContributedBestPredictionWeight, int allPredictionsWeight) {
        return pendingAndContributedBestPredictionWeight * trust > (1 + allPredictionsWeight) * (trust - 1);
    }






}