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

import java.util.Set;

@Slf4j
@Service
public class PredictionService {

    private ContributionService contributionService;

    public PredictionService(ContributionService contributionService) {
        this.contributionService = contributionService;
    }

    Prediction getContributedBestPrediction(String chainTaskId) {
        Set<String> distinctContributions = contributionService.getDistinctContributions(chainTaskId);
        Prediction bestPrediction = Prediction.builder().contribution("").weight(0).build();

        for (String predictionContribution : distinctContributions) {
            int predictionWeight = contributionService.getContributedWeight(chainTaskId, predictionContribution);

            if (predictionWeight >= bestPrediction.getWeight()) {
                bestPrediction.setContribution(predictionContribution);
                bestPrediction.setWeight(predictionWeight);
            }
        }
        return bestPrediction;
    }

    private int getContributedBestPredictionWeight(String chainTaskId) {
        return this.getContributedBestPrediction(chainTaskId).getWeight();
    }

    /*
     *
     * Considering pending workers are going to contribute to the best prediction
     * Counting pending and contributed
     *
     * */
    int getBestPredictionWeight(String chainTaskId, long maxExecutionTime) {
        int contributedBestPredictionWeight = getContributedBestPredictionWeight(chainTaskId);
        int pendingWeight = contributionService.getPendingWeight(chainTaskId, maxExecutionTime);

        int bestPredictionWeight;
        if (pendingWeight == 0 && contributedBestPredictionWeight == 0) {
            bestPredictionWeight = 0;
        } else if (pendingWeight > 0 && contributedBestPredictionWeight == 0) {
            bestPredictionWeight = pendingWeight;
        } else if (pendingWeight == 0 && contributedBestPredictionWeight > 0) {
            bestPredictionWeight = contributedBestPredictionWeight;
        } else {
            bestPredictionWeight = contributedBestPredictionWeight * pendingWeight;
        }
        return bestPredictionWeight;
    }

    /*
     *
     * Sum all prediction weights but exclude contributed best prediction weight
     *
     * */
    int getWorstPredictionsWeight(String chainTaskId) {
        Set<String> distinctContributions = contributionService.getDistinctContributions(chainTaskId);
        String bestPredictionContribution = this.getContributedBestPrediction(chainTaskId).getContribution();

        int allOtherPredictionsWeight = 0;

        for (String contribution : distinctContributions) {
            int predictionWeight = contributionService.getContributedWeight(chainTaskId, contribution);

            if (!contribution.equals(bestPredictionContribution)) {
                allOtherPredictionsWeight = allOtherPredictionsWeight + predictionWeight;
            }
        }
        return allOtherPredictionsWeight;
    }

}