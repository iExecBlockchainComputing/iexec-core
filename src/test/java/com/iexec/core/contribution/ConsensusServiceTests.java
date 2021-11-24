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
import com.iexec.core.replicate.ReplicatesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;


class ConsensusServiceTests {

    private final static String CHAIN_TASK_ID = "0xtaskId";
    private final static List<Replicate> REPLICATES = Collections.emptyList();
    private final static long MAX_EXECUTION_TIME = 60000;

    /*
    *
    * Disclaimer: Non exhaustive tests
    *
    * */

    @Mock
    private PredictionService predictionService;

    @Mock
    private ReplicatesService replicatesService;

    @InjectMocks
    private ConsensusService consensusService;

    @BeforeEach
    void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void shouldNeedMoreContributionsTrust0() {
        int trust = 0;
        int bestPredictionWeight = 0;
        int worstPredictionsWeight = 0;

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(REPLICATES);
        when(predictionService.getBestPredictionWeight(REPLICATES, MAX_EXECUTION_TIME)).thenReturn(bestPredictionWeight);
        when(predictionService.getWorstPredictionsWeight(REPLICATES)).thenReturn(worstPredictionsWeight);
        boolean needMoreContributionsForConsensus =
                consensusService.doesTaskNeedMoreContributionsForConsensus(CHAIN_TASK_ID, trust, MAX_EXECUTION_TIME);

        assertThat(needMoreContributionsForConsensus).isTrue();
    }

    @Test
    void shouldNotNeedMoreContributionsTrust0() {
        int trust = 0;
        int bestPredictionWeight = 2;
        int worstPredictionsWeight = 0;

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(REPLICATES);
        when(predictionService.getBestPredictionWeight(REPLICATES, MAX_EXECUTION_TIME)).thenReturn(bestPredictionWeight);
        when(predictionService.getWorstPredictionsWeight(REPLICATES)).thenReturn(worstPredictionsWeight);
        boolean needMoreContributionsForConsensus =
                consensusService.doesTaskNeedMoreContributionsForConsensus(CHAIN_TASK_ID, trust, MAX_EXECUTION_TIME);

        assertThat(needMoreContributionsForConsensus).isFalse();
    }

    @Test
    void shouldNeedMoreContributionsTrust5() {
        int trust = 5;
        int bestPredictionWeight = 4;
        int worstPredictionsWeight = 0;

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(REPLICATES);
        when(predictionService.getBestPredictionWeight(REPLICATES, MAX_EXECUTION_TIME)).thenReturn(bestPredictionWeight);
        when(predictionService.getWorstPredictionsWeight(REPLICATES)).thenReturn(worstPredictionsWeight);
        boolean needMoreContributionsForConsensus =
                consensusService.doesTaskNeedMoreContributionsForConsensus(CHAIN_TASK_ID, trust, MAX_EXECUTION_TIME);

        assertThat(needMoreContributionsForConsensus).isTrue();
    }

    @Test
    void shouldNotNeedMoreContributionsTrust5() {
        int trust = 5;
        int bestPredictionWeight = 5;
        int worstPredictionsWeight = 0;

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(REPLICATES);
        when(predictionService.getBestPredictionWeight(REPLICATES, MAX_EXECUTION_TIME)).thenReturn(bestPredictionWeight);
        when(predictionService.getWorstPredictionsWeight(REPLICATES)).thenReturn(worstPredictionsWeight);
        boolean needMoreContributionsForConsensus =
                consensusService.doesTaskNeedMoreContributionsForConsensus(CHAIN_TASK_ID, trust, MAX_EXECUTION_TIME);

        assertThat(needMoreContributionsForConsensus).isFalse();
    }

    @Test
    void shouldNeedMoreContributionsTrust5Worst1() {
        int trust = 5;
        int bestPredictionWeight = 5;
        int worstPredictionsWeight = 1;

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(REPLICATES);
        when(predictionService.getBestPredictionWeight(REPLICATES, MAX_EXECUTION_TIME)).thenReturn(bestPredictionWeight);
        when(predictionService.getWorstPredictionsWeight(REPLICATES)).thenReturn(worstPredictionsWeight);
        boolean needMoreContributionsForConsensus =
                consensusService.doesTaskNeedMoreContributionsForConsensus(CHAIN_TASK_ID, trust, MAX_EXECUTION_TIME);

        assertThat(needMoreContributionsForConsensus).isTrue();
    }

    @Test
    void shouldNeedMoreContributionsTrust5Worst5() {
        int trust = 5;
        int bestPredictionWeight = 25;
        int worstPredictionsWeight = 5;

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(REPLICATES);
        when(predictionService.getBestPredictionWeight(REPLICATES, MAX_EXECUTION_TIME)).thenReturn(bestPredictionWeight);
        when(predictionService.getWorstPredictionsWeight(REPLICATES)).thenReturn(worstPredictionsWeight);
        boolean needMoreContributionsForConsensus =
                consensusService.doesTaskNeedMoreContributionsForConsensus(CHAIN_TASK_ID, trust, MAX_EXECUTION_TIME);

        assertThat(needMoreContributionsForConsensus).isFalse();
    }

}