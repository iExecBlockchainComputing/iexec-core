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

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.when;


public class PredictionServiceTests {

    private final static String WALLET_WORKER_1 = "0x1";
    private final static String CHAIN_TASK_ID = "0xtaskId";
    private final static long MAX_EXECUTION_TIME = 60000;

    @Mock
    private ContributionService contributionService;

    @InjectMocks
    private PredictionService predictionService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldGetEmptyContributedBestPrediction() {
        when(contributionService.getDistinctContributions(CHAIN_TASK_ID)).thenReturn(new HashSet<>());

        Prediction contributedBestPrediction = predictionService.getContributedBestPrediction(CHAIN_TASK_ID);

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
        when(contributionService.getDistinctContributions(CHAIN_TASK_ID)).thenReturn(contributions);
        when(contributionService.getContributedWeight(CHAIN_TASK_ID, contributionA)).thenReturn(contributionWeightA);
        when(contributionService.getContributedWeight(CHAIN_TASK_ID, contributionB)).thenReturn(contributionWeightB);

        Prediction contributedBestPrediction = predictionService.getContributedBestPrediction(CHAIN_TASK_ID);

        assertThat(contributedBestPrediction.getContribution()).isEqualTo(contributionB);
        assertThat(contributedBestPrediction.getWeight()).isEqualTo(contributionWeightB);

    }

    @Test
    public void shouldGetBestPredictionWeight() {
        String contributedBestPredictionContribution = "a";
        int contributedBestPredictionWeight = 5;

        when(contributionService.getDistinctContributions(CHAIN_TASK_ID)).thenReturn(new HashSet<>(Collections.singletonList(contributedBestPredictionContribution)));
        when(contributionService.getContributedWeight(CHAIN_TASK_ID, contributedBestPredictionContribution)).thenReturn(contributedBestPredictionWeight);
        when(contributionService.getPendingWeight(CHAIN_TASK_ID, MAX_EXECUTION_TIME)).thenReturn(5);

        int bestPredictionWeight = predictionService.getBestPredictionWeight(CHAIN_TASK_ID, MAX_EXECUTION_TIME);

        assertThat(bestPredictionWeight).isEqualTo(25);//contributed * pending
    }

    @Test
    public void shouldGetBestPredictionWeightWithNoContributed() {
        String contributedBestPredictionContribution = "";
        int contributedBestPredictionWeight = 0;

        when(contributionService.getDistinctContributions(CHAIN_TASK_ID)).thenReturn(new HashSet<>(Collections.singletonList(contributedBestPredictionContribution)));
        when(contributionService.getContributedWeight(CHAIN_TASK_ID, contributedBestPredictionContribution)).thenReturn(contributedBestPredictionWeight);
        when(contributionService.getPendingWeight(CHAIN_TASK_ID, MAX_EXECUTION_TIME)).thenReturn(5);

        int bestPredictionWeight = predictionService.getBestPredictionWeight(CHAIN_TASK_ID, MAX_EXECUTION_TIME);

        assertThat(bestPredictionWeight).isEqualTo(5);//contributed * pending, no contributed should not multiply by zero
    }

    @Test
    public void shouldGetBestPredictionWeightWithNoPending() {
        String contributedBestPredictionContribution = "a";
        int contributedBestPredictionWeight = 5;

        when(contributionService.getDistinctContributions(CHAIN_TASK_ID)).thenReturn(new HashSet<>(Collections.singletonList(contributedBestPredictionContribution)));
        when(contributionService.getContributedWeight(CHAIN_TASK_ID, contributedBestPredictionContribution)).thenReturn(contributedBestPredictionWeight);
        when(contributionService.getPendingWeight(CHAIN_TASK_ID, MAX_EXECUTION_TIME)).thenReturn(0);

        int bestPredictionWeight = predictionService.getBestPredictionWeight(CHAIN_TASK_ID, MAX_EXECUTION_TIME);

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

        when(contributionService.getDistinctContributions(CHAIN_TASK_ID)).thenReturn(new HashSet<>(Arrays.asList(contributionA, contributionB, contributionC)));
        when(contributionService.getContributedWeight(CHAIN_TASK_ID, contributionA)).thenReturn(contributionWeightA);
        when(contributionService.getContributedWeight(CHAIN_TASK_ID, contributionB)).thenReturn(contributionWeightB);
        when(contributionService.getContributedWeight(CHAIN_TASK_ID, contributionC)).thenReturn(contributionWeightC);
        when(contributionService.getPendingWeight(CHAIN_TASK_ID, MAX_EXECUTION_TIME)).thenReturn(0);

        int worstPredictionsWeight = predictionService.getWorstPredictionsWeight(CHAIN_TASK_ID);//w(worst) = w(A)+w(B) = 15

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

        when(contributionService.getDistinctContributions(CHAIN_TASK_ID)).thenReturn(new HashSet<>(Arrays.asList(contributionA, contributionB, contributionC)));
        when(contributionService.getContributedWeight(CHAIN_TASK_ID, contributionA)).thenReturn(contributionWeightA);
        when(contributionService.getContributedWeight(CHAIN_TASK_ID, contributionB)).thenReturn(contributionWeightB);
        when(contributionService.getContributedWeight(CHAIN_TASK_ID, contributionC)).thenReturn(contributionWeightC);
        when(contributionService.getPendingWeight(CHAIN_TASK_ID, MAX_EXECUTION_TIME)).thenReturn(50);

        int worstPredictionsWeight = predictionService.getWorstPredictionsWeight(CHAIN_TASK_ID);//w(worst) = w(A)+w(B) = 15

        assertThat(worstPredictionsWeight).isEqualTo(15);
    }

}