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

import com.iexec.core.replicate.Replicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;


public class PredictionServiceTests {

    private final static List<Replicate> REPLICATES = Collections.emptyList();
    private final static long MAX_EXECUTION_TIME = 60000;

    @Mock
    private ContributionService contributionService;

    @InjectMocks
    private PredictionService predictionService;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldGetEmptyContributedBestPrediction() {

        when(contributionService.getDistinctContributions(REPLICATES)).thenReturn(new HashSet<>());

        Prediction contributedBestPrediction = predictionService.getContributedBestPrediction(REPLICATES);

        assertThat(contributedBestPrediction.getContribution()).isEqualTo("");
        assertThat(contributedBestPrediction.getWeight()).isEqualTo(0);

    }

    @Test
    public void shouldGetContributedBestPrediction() {
        String contributionA = "a";
        String contributionB = "b";
        int contributionWeightA = 5;
        int contributionWeightB = 10;

        Set<String> contributions = new HashSet<>(Arrays.asList(contributionA, contributionB));
        when(contributionService.getDistinctContributions(REPLICATES)).thenReturn(contributions);
        when(contributionService.getContributedWeight(REPLICATES, contributionA)).thenReturn(contributionWeightA);
        when(contributionService.getContributedWeight(REPLICATES, contributionB)).thenReturn(contributionWeightB);

        Prediction contributedBestPrediction = predictionService.getContributedBestPrediction(REPLICATES);

        assertThat(contributedBestPrediction.getContribution()).isEqualTo(contributionB);
        assertThat(contributedBestPrediction.getWeight()).isEqualTo(contributionWeightB);

    }

    @Test
    public void shouldGetBestPredictionWeight() {
        String contributedBestPredictionContribution = "a";
        int contributedBestPredictionWeight = 5;

        when(contributionService.getDistinctContributions(REPLICATES)).thenReturn(new HashSet<>(Collections.singletonList(contributedBestPredictionContribution)));
        when(contributionService.getContributedWeight(REPLICATES, contributedBestPredictionContribution)).thenReturn(contributedBestPredictionWeight);
        when(contributionService.getPendingWeight(REPLICATES, MAX_EXECUTION_TIME)).thenReturn(5);

        int bestPredictionWeight = predictionService.getBestPredictionWeight(REPLICATES, MAX_EXECUTION_TIME);

        assertThat(bestPredictionWeight).isEqualTo(25);//contributed * pending
    }

    @Test
    public void shouldGetBestPredictionWeightWithNoContributed() {
        String contributedBestPredictionContribution = "";
        int contributedBestPredictionWeight = 0;

        when(contributionService.getDistinctContributions(REPLICATES)).thenReturn(new HashSet<>(Collections.singletonList(contributedBestPredictionContribution)));
        when(contributionService.getContributedWeight(REPLICATES, contributedBestPredictionContribution)).thenReturn(contributedBestPredictionWeight);
        when(contributionService.getPendingWeight(REPLICATES, MAX_EXECUTION_TIME)).thenReturn(5);

        int bestPredictionWeight = predictionService.getBestPredictionWeight(REPLICATES, MAX_EXECUTION_TIME);

        assertThat(bestPredictionWeight).isEqualTo(5);//contributed * pending, no contributed should not multiply by zero
    }

    @Test
    public void shouldGetBestPredictionWeightWithNoPending() {
        String contributedBestPredictionContribution = "a";
        int contributedBestPredictionWeight = 5;

        when(contributionService.getDistinctContributions(REPLICATES)).thenReturn(new HashSet<>(Collections.singletonList(contributedBestPredictionContribution)));
        when(contributionService.getContributedWeight(REPLICATES, contributedBestPredictionContribution)).thenReturn(contributedBestPredictionWeight);
        when(contributionService.getPendingWeight(REPLICATES, MAX_EXECUTION_TIME)).thenReturn(0);

        int bestPredictionWeight = predictionService.getBestPredictionWeight(REPLICATES, MAX_EXECUTION_TIME);

        assertThat(bestPredictionWeight).isEqualTo(5);//contributed * pending, no pending should not multiply by zero
    }

    @Test
    public void shouldGetWorstPredictionsWeight() {
        String contributionA = "a";
        String contributionB = "b";
        String contributionC = "c";
        int contributionWeightA = 5;
        int contributionWeightB = 10;
        int contributionWeightC = 20;

        when(contributionService.getDistinctContributions(REPLICATES)).thenReturn(new HashSet<>(Arrays.asList(contributionA, contributionB, contributionC)));
        when(contributionService.getContributedWeight(REPLICATES, contributionA)).thenReturn(contributionWeightA);
        when(contributionService.getContributedWeight(REPLICATES, contributionB)).thenReturn(contributionWeightB);
        when(contributionService.getContributedWeight(REPLICATES, contributionC)).thenReturn(contributionWeightC);
        when(contributionService.getPendingWeight(REPLICATES, MAX_EXECUTION_TIME)).thenReturn(0);

        int worstPredictionsWeight = predictionService.getWorstPredictionsWeight(REPLICATES);//w(worst) = w(A)+w(B) = 15

        assertThat(worstPredictionsWeight).isEqualTo(15);
    }

    @Test
    public void shouldGetSameWorstPredictionsWeightSincePendingDoesNotAffectSum() {
        String contributionA = "a";
        String contributionB = "b";
        String contributionC = "c";
        int contributionWeightA = 5;
        int contributionWeightB = 10;
        int contributionWeightC = 20;

        when(contributionService.getDistinctContributions(REPLICATES)).thenReturn(new HashSet<>(Arrays.asList(contributionA, contributionB, contributionC)));
        when(contributionService.getContributedWeight(REPLICATES, contributionA)).thenReturn(contributionWeightA);
        when(contributionService.getContributedWeight(REPLICATES, contributionB)).thenReturn(contributionWeightB);
        when(contributionService.getContributedWeight(REPLICATES, contributionC)).thenReturn(contributionWeightC);
        when(contributionService.getPendingWeight(REPLICATES, MAX_EXECUTION_TIME)).thenReturn(50);

        int worstPredictionsWeight = predictionService.getWorstPredictionsWeight(REPLICATES);//w(worst) = w(A)+w(B) = 15

        assertThat(worstPredictionsWeight).isEqualTo(15);
    }

}