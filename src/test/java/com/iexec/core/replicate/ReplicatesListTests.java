/*
 * Copyright 2022-2023 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.replicate;

import com.iexec.common.replicate.ReplicateStatusModifier;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatus.STARTING;
import static com.iexec.commons.poco.utils.TestUtils.*;
import static org.assertj.core.api.Assertions.assertThat;

class ReplicatesListTests {
    // FIXME: add tests

    // region getNbValidContributedWinners
    @Test
    void shouldGetOneContributionWinnerAmongTwoContributors() {
        String contributionHash = "hash";
        String badContributionHash = "badHash";
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate1.setContributionHash(contributionHash);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate2.setContributionHash(badContributionHash);
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID,
                Arrays.asList(replicate1, replicate2));

        assertThat(replicatesList.getNbValidContributedWinners(
                contributionHash
        )).isOne();
    }
    // endregion

    // region hasWorkerAlreadyParticipated
    @Test
    void shouldHaveWorkerAlreadyContributed() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(STARTING, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        assertThat(replicatesList.hasWorkerAlreadyParticipated(WALLET_WORKER_1)).isTrue();
        assertThat(replicatesList.hasWorkerAlreadyParticipated(WALLET_WORKER_2)).isTrue();
    }

    @Test
    void shouldNotHaveWorkerAlreadyContributed() {
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(COMPUTED, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(STARTING, ReplicateStatusModifier.WORKER);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Arrays.asList(replicate1, replicate2));

        assertThat(replicatesList.hasWorkerAlreadyParticipated(WALLET_WORKER_3)).isFalse();
    }
    // endregion
}
