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