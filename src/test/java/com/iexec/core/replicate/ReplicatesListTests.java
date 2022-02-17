package com.iexec.core.replicate;

import com.iexec.common.replicate.ReplicateStatusModifier;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatus.STARTING;
import static com.iexec.common.utils.TestUtils.*;
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
