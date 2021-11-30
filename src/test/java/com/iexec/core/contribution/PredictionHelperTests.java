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


class PredictionHelperTests {

    private final static String CHAIN_TASK_ID = "0xtaskId";
    private final static long MAX_EXECUTION_TIME = 60000;

    @Test
    void shouldGetEmptyContributedBestPrediction() {
        Prediction contributedBestPrediction = PredictionHelper.getContributedBestPrediction(Collections.emptyList());

        assertThat(contributedBestPrediction.getContribution()).isEmpty();
        assertThat(contributedBestPrediction.getWeight()).isZero();

    }

    @Test
    void shouldGetContributedBestPrediction() {
        String contributionA = "a";
        String contributionB = "b";
        int contributionWeightA = 5;
        int contributionWeightB = 10;

        final Replicate replicateA = new Replicate("0x1", CHAIN_TASK_ID);
        replicateA.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicateA.setContributionHash(contributionA);
        replicateA.setWorkerWeight(contributionWeightA);

        final Replicate replicateB = new Replicate("0x2", CHAIN_TASK_ID);
        replicateB.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicateB.setContributionHash(contributionB);
        replicateB.setWorkerWeight(contributionWeightB);

        final List<Replicate> replicates = List.of(replicateA, replicateB);
        Prediction contributedBestPrediction = PredictionHelper.getContributedBestPrediction(replicates);

        assertThat(contributedBestPrediction.getContribution()).isEqualTo(contributionB);
        assertThat(contributedBestPrediction.getWeight()).isEqualTo(contributionWeightB);

    }

    @Test
    void shouldGetBestPredictionWeight() {
        String contributedBestPredictionContribution = "a";
        int contributedBestPredictionWeight = 5;
        int pendingWeight = 5;

        final Replicate replicate = new Replicate("0x1", CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate.setContributionHash(contributedBestPredictionContribution);
        replicate.setWorkerWeight(contributedBestPredictionWeight);

        final Replicate pendingReplicate = new Replicate("0x2", CHAIN_TASK_ID);
        pendingReplicate.updateStatus(ReplicateStatus.COMPUTING, ReplicateStatusModifier.WORKER);
        pendingReplicate.setWorkerWeight(pendingWeight);

        final List<Replicate> replicates = List.of(replicate, pendingReplicate);

        int bestPredictionWeight = PredictionHelper.getBestPredictionWeight(replicates, MAX_EXECUTION_TIME);

        assertThat(bestPredictionWeight).isEqualTo(25);//contributed * pending
    }

    @Test
    void shouldGetBestPredictionWeightWithNoContributed() {
        int pendingWeight = 5;

        final Replicate pendingReplicate = new Replicate("0x2", CHAIN_TASK_ID);
        pendingReplicate.updateStatus(ReplicateStatus.COMPUTING, ReplicateStatusModifier.WORKER);
        pendingReplicate.setWorkerWeight(pendingWeight);

        final List<Replicate> replicates = List.of(pendingReplicate);

        int bestPredictionWeight = PredictionHelper.getBestPredictionWeight(replicates, MAX_EXECUTION_TIME);

        assertThat(bestPredictionWeight).isEqualTo(5);//contributed * pending, no contributed should not multiply by zero
    }

    @Test
    void shouldGetBestPredictionWeightWithNoPending() {
        String contributedBestPredictionContribution = "a";
        int contributedBestPredictionWeight = 5;

        final Replicate replicate = new Replicate("0x1", CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate.setContributionHash(contributedBestPredictionContribution);
        replicate.setWorkerWeight(contributedBestPredictionWeight);

        final List<Replicate> replicates = List.of(replicate);

        int bestPredictionWeight = PredictionHelper.getBestPredictionWeight(replicates, MAX_EXECUTION_TIME);

        assertThat(bestPredictionWeight).isEqualTo(5);//contributed * pending, no pending should not multiply by zero
    }

    @Test
    void shouldGetWorstPredictionsWeight() {
        String contributionA = "a";
        String contributionB = "b";
        String contributionC = "c";
        int contributionWeightA = 5;
        int contributionWeightB = 10;
        int contributionWeightC = 20;

        final Replicate replicateA = new Replicate("0x1", CHAIN_TASK_ID);
        replicateA.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicateA.setContributionHash(contributionA);
        replicateA.setWorkerWeight(contributionWeightA);

        final Replicate replicateB = new Replicate("0x2", CHAIN_TASK_ID);
        replicateB.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicateB.setContributionHash(contributionB);
        replicateB.setWorkerWeight(contributionWeightB);

        final Replicate replicateC = new Replicate("0x3", CHAIN_TASK_ID);
        replicateC.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicateC.setContributionHash(contributionC);
        replicateC.setWorkerWeight(contributionWeightC);

        final List<Replicate> replicates = List.of(replicateA, replicateB, replicateC);

        int worstPredictionsWeight = PredictionHelper.getWorstPredictionsWeight(replicates);//w(worst) = w(A)+w(B) = 15

        assertThat(worstPredictionsWeight).isEqualTo(15);
    }

    @Test
    void shouldGetSameWorstPredictionsWeightSincePendingDoesNotAffectSum() {
        String contributionA = "a";
        String contributionB = "b";
        String contributionC = "c";
        int contributionWeightA = 5;
        int contributionWeightB = 10;
        int contributionWeightC = 20;
        int pendingWeight = 50;

        final Replicate replicateA = new Replicate("0x1", CHAIN_TASK_ID);
        replicateA.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicateA.setContributionHash(contributionA);
        replicateA.setWorkerWeight(contributionWeightA);

        final Replicate replicateB = new Replicate("0x2", CHAIN_TASK_ID);
        replicateB.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicateB.setContributionHash(contributionB);
        replicateB.setWorkerWeight(contributionWeightB);

        final Replicate replicateC = new Replicate("0x3", CHAIN_TASK_ID);
        replicateC.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicateC.setContributionHash(contributionC);
        replicateC.setWorkerWeight(contributionWeightC);

        final Replicate pendingReplicate = new Replicate("0x4", CHAIN_TASK_ID);
        pendingReplicate.updateStatus(ReplicateStatus.COMPUTING, ReplicateStatusModifier.WORKER);
        pendingReplicate.setWorkerWeight(pendingWeight);

        final List<Replicate> replicates = List.of(replicateA, replicateB, replicateC, pendingReplicate);

        int worstPredictionsWeight = PredictionHelper.getWorstPredictionsWeight(replicates);//w(worst) = w(A)+w(B) = 15

        assertThat(worstPredictionsWeight).isEqualTo(15);
    }

}