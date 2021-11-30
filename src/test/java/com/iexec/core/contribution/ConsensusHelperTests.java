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
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.replicate.Replicate;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


class ConsensusHelperTests {

    private final static String CHAIN_TASK_ID = "0xtaskId";
    private final static long MAX_EXECUTION_TIME = 60000;

    /*
    *
    * Disclaimer: Non exhaustive tests
    *
    * */

    @Test
    void shouldNeedMoreContributionsTrust0() {
        int trust = 0;

        final List<Replicate> replicates = Collections.emptyList();

        boolean needMoreContributionsForConsensus =
                ConsensusHelper.doesTaskNeedMoreContributionsForConsensus(CHAIN_TASK_ID, replicates, trust, MAX_EXECUTION_TIME);

        assertThat(needMoreContributionsForConsensus).isTrue();
    }

    @Test
    void shouldNotNeedMoreContributionsTrust0() {
        int trust = 0;
        int bestPredictionWeight = 2;

        final Replicate replicate = new Replicate("0x1", CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate.setWorkerWeight(bestPredictionWeight);

        final List<Replicate> replicates = List.of(replicate);

        boolean needMoreContributionsForConsensus =
                ConsensusHelper.doesTaskNeedMoreContributionsForConsensus(CHAIN_TASK_ID, replicates, trust, MAX_EXECUTION_TIME);

        assertThat(needMoreContributionsForConsensus).isFalse();
    }

    @Test
    void shouldNeedMoreContributionsTrust5() {
        int trust = 5;
        int bestPredictionWeight = 4;

        final Replicate replicate = new Replicate("0x1", CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate.setWorkerWeight(bestPredictionWeight);

        final List<Replicate> replicates = List.of(replicate);

        boolean needMoreContributionsForConsensus =
                ConsensusHelper.doesTaskNeedMoreContributionsForConsensus(CHAIN_TASK_ID, replicates, trust, MAX_EXECUTION_TIME);

        assertThat(needMoreContributionsForConsensus).isTrue();
    }

    @Test
    void shouldNotNeedMoreContributionsTrust5() {
        int trust = 5;
        int bestPredictionWeight = 5;

        final Replicate replicate = new Replicate("0x1", CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate.setWorkerWeight(bestPredictionWeight);

        final List<Replicate> replicates = List.of(replicate);

        boolean needMoreContributionsForConsensus =
                ConsensusHelper.doesTaskNeedMoreContributionsForConsensus(CHAIN_TASK_ID, replicates, trust, MAX_EXECUTION_TIME);

        assertThat(needMoreContributionsForConsensus).isFalse();
    }

    @Test
    void shouldNeedMoreContributionsTrust5Worst1() {
        int trust = 5;
        int bestPredictionWeight = 5;
        int worstPredictionsWeight = 1;
        String bestContribution = "best";
        String worstContribution = "worst";

        final Replicate bestReplicate = new Replicate("0x1", CHAIN_TASK_ID);
        bestReplicate.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        bestReplicate.setWorkerWeight(bestPredictionWeight);
        bestReplicate.setContributionHash(bestContribution);

        final Replicate worstReplicate = new Replicate("0x2", CHAIN_TASK_ID);
        worstReplicate.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        worstReplicate.setWorkerWeight(worstPredictionsWeight);
        worstReplicate.setContributionHash(worstContribution);

        final List<Replicate> replicates = List.of(bestReplicate, worstReplicate);

        boolean needMoreContributionsForConsensus =
                ConsensusHelper.doesTaskNeedMoreContributionsForConsensus(CHAIN_TASK_ID, replicates, trust, MAX_EXECUTION_TIME);

        assertThat(needMoreContributionsForConsensus).isTrue();
    }

    @Test
    void shouldNotNeedMoreContributionsTrust5Worst5() {
        int trust = 5;
        int bestPredictionWeight = 25;
        int worstPredictionsWeight = 5;
        String bestContribution = "best";
        String worstContribution = "worst";

        final Replicate bestReplicate = new Replicate("0x1", CHAIN_TASK_ID);
        bestReplicate.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        bestReplicate.setWorkerWeight(bestPredictionWeight);
        bestReplicate.setContributionHash(bestContribution);

        final Replicate worstReplicate = new Replicate("0x2", CHAIN_TASK_ID);
        worstReplicate.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        worstReplicate.setWorkerWeight(worstPredictionsWeight);
        worstReplicate.setContributionHash(worstContribution);

        final List<Replicate> replicates = List.of(bestReplicate, worstReplicate);

        boolean needMoreContributionsForConsensus =
                ConsensusHelper.doesTaskNeedMoreContributionsForConsensus(CHAIN_TASK_ID, replicates, trust, MAX_EXECUTION_TIME);

        assertThat(needMoreContributionsForConsensus).isFalse();
    }

}