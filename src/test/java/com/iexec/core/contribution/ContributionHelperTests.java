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
import com.iexec.core.replicate.Replicate;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class ContributionHelperTests {

    private final static String A = "0xA";
    private final static String B = "0xB";
    private final static long MAX_EXECUTION_TIME = 60000;

    @Test
    void shouldGetContributedWeight() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CONTRIBUTED));
        when(replicate1.getContributionHash()).thenReturn(A);
        when(replicate1.getWorkerWeight()).thenReturn(3);

        Replicate replicate2 = mock(Replicate.class);
        when(replicate2.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CONTRIBUTED));
        when(replicate2.getContributionHash()).thenReturn(A);
        when(replicate2.getWorkerWeight()).thenReturn(5);

        Replicate replicate3 = mock(Replicate.class);
        when(replicate3.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CONTRIBUTED));
        when(replicate3.getContributionHash()).thenReturn(B);
        when(replicate3.getWorkerWeight()).thenReturn(10);

        List<Replicate> replicates = Arrays.asList(replicate1, replicate2, replicate3);

        assertThat(ContributionHelper.getContributedWeight(replicates, A)).isEqualTo(15);
        assertThat(ContributionHelper.getContributedWeight(replicates, B)).isEqualTo(10);
    }

    @Test
    void shouldNotGetContributedWeightSinceNoContribution() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CONTRIBUTED));
        when(replicate1.getContributionHash()).thenReturn("");
        when(replicate1.getWorkerWeight()).thenReturn(3);

        List<Replicate> replicates = Collections.singletonList(replicate1);

        assertThat(ContributionHelper.getContributedWeight(replicates, A)).isZero();
    }

    @Test
    void shouldNotGetContributedWeightSinceNoWeight() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CONTRIBUTED));
        when(replicate1.getContributionHash()).thenReturn(A);
        when(replicate1.getWorkerWeight()).thenReturn(0);

        List<Replicate> replicates = Collections.singletonList(replicate1);

        assertThat(ContributionHelper.getContributedWeight(replicates, A)).isZero();
    }

    @Test
    void shouldNotGetContributedWeightSinceNoTContributed() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CONTRIBUTING));
        when(replicate1.getContributionHash()).thenReturn(A);
        when(replicate1.getWorkerWeight()).thenReturn(3);

        List<Replicate> replicates = Collections.singletonList(replicate1);

        assertThat(ContributionHelper.getContributedWeight(replicates, A)).isZero();
    }

    @Test
    void shouldGetPendingWeight() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CREATED));
        when(replicate1.getWorkerWeight()).thenReturn(3);

        Replicate replicate2 = mock(Replicate.class);
        when(replicate2.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate2.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CREATED));
        when(replicate2.getWorkerWeight()).thenReturn(5);

        List<Replicate> replicates = Arrays.asList(replicate1, replicate2);

        assertThat(ContributionHelper.getPendingWeight(replicates, MAX_EXECUTION_TIME)).isEqualTo(15);
    }

    @Test
    void shouldCount0PendingWeightSinceContributed() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CONTRIBUTED));
        when(replicate1.getWorkerWeight()).thenReturn(3);

        List<Replicate> replicates = Collections.singletonList(replicate1);

        assertThat(ContributionHelper.getPendingWeight(replicates, MAX_EXECUTION_TIME)).isZero();
    }

    @Test
    void shouldCountOnlyPendingWeightForOneSinceOtherFailed() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CREATED));
        when(replicate1.getWorkerWeight()).thenReturn(3);

        Replicate replicate2 = mock(Replicate.class);
        when(replicate2.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate2.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.FAILED));
        when(replicate2.getWorkerWeight()).thenReturn(5);

        List<Replicate> replicates = Arrays.asList(replicate1, replicate2);

        assertThat(ContributionHelper.getPendingWeight(replicates, MAX_EXECUTION_TIME)).isEqualTo(3);
    }

    @Test
    void shouldCountOnlyPendingWeightForOneSinceOtherContributed() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CREATED));
        when(replicate1.getWorkerWeight()).thenReturn(3);

        Replicate replicate2 = mock(Replicate.class);
        when(replicate2.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate2.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CONTRIBUTED));
        when(replicate2.getWorkerWeight()).thenReturn(5);

        List<Replicate> replicates = Arrays.asList(replicate1, replicate2);

        assertThat(ContributionHelper.getPendingWeight(replicates, MAX_EXECUTION_TIME)).isEqualTo(3);
    }

    @Test
    void shouldCountOnlyPendingWeightForOneSinceOtherVeryOld() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CREATED));
        when(replicate1.getWorkerWeight()).thenReturn(3);

        Replicate replicate2 = mock(Replicate.class);
        when(replicate2.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(true);
        when(replicate2.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CREATED));
        when(replicate2.getWorkerWeight()).thenReturn(5);

        List<Replicate> replicates = Arrays.asList(replicate1, replicate2);

        assertThat(ContributionHelper.getPendingWeight(replicates, MAX_EXECUTION_TIME)).isEqualTo(3);
    }

    @Test
    void shouldCountOnlyPendingWeightForOneSinceOtherNoWeight() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CREATED));
        when(replicate1.getWorkerWeight()).thenReturn(3);

        Replicate replicate2 = mock(Replicate.class);
        when(replicate2.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate2.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CREATED));
        when(replicate2.getWorkerWeight()).thenReturn(0);

        List<Replicate> replicates = Arrays.asList(replicate1, replicate2);

        assertThat(ContributionHelper.getPendingWeight(replicates, MAX_EXECUTION_TIME)).isEqualTo(3);
    }

    @Test
    void shouldGetDistinctContributions() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CONTRIBUTED));
        when(replicate1.getContributionHash()).thenReturn(A);
        when(replicate1.getWorkerWeight()).thenReturn(3);

        Replicate replicate2 = mock(Replicate.class);
        when(replicate2.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CONTRIBUTED));
        when(replicate2.getContributionHash()).thenReturn(A);
        when(replicate2.getWorkerWeight()).thenReturn(5);

        Replicate replicate3 = mock(Replicate.class);
        when(replicate3.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CONTRIBUTED));
        when(replicate3.getContributionHash()).thenReturn(B);
        when(replicate3.getWorkerWeight()).thenReturn(10);


        List<Replicate> replicates = Arrays.asList(replicate1, replicate2, replicate3);

        assertThat(ContributionHelper.getDistinctContributions(replicates)).isEqualTo(new HashSet<>(Arrays.asList(A, B)));
    }

    @Test
    void shouldNotGetDistinctContributionsSinceNotContributed() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CREATED));

        List<Replicate> replicates = Collections.singletonList(replicate1);

        assertThat(ContributionHelper.getDistinctContributions(replicates)).isEmpty();
    }

}