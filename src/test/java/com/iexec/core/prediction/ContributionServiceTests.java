package com.iexec.core.prediction;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class ContributionServiceTests {

    private final static String CHAIN_TASK_ID = "0xtaskId";
    private final static String A = "0xA";
    private final static String B = "0xB";
    private final static long MAX_EXECUTION_TIME = 60000;

    @Mock
    private ReplicatesService replicatesService;

    @InjectMocks
    private ContributionService contributionService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldGetContributedWeight() {
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

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(replicates);

        assertThat(contributionService.getContributedWeight(CHAIN_TASK_ID, A)).isEqualTo(15);
        assertThat(contributionService.getContributedWeight(CHAIN_TASK_ID, B)).isEqualTo(10);
    }

    @Test
    public void shouldNotGetContributedWeightSinceNoContribution() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CONTRIBUTED));
        when(replicate1.getContributionHash()).thenReturn("");
        when(replicate1.getWorkerWeight()).thenReturn(3);

        List<Replicate> replicates = Collections.singletonList(replicate1);

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(replicates);

        assertThat(contributionService.getContributedWeight(CHAIN_TASK_ID, A)).isEqualTo(0);
    }

    @Test
    public void shouldNotGetContributedWeightSinceNoWeight() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CONTRIBUTED));
        when(replicate1.getContributionHash()).thenReturn(A);
        when(replicate1.getWorkerWeight()).thenReturn(0);

        List<Replicate> replicates = Collections.singletonList(replicate1);

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(replicates);

        assertThat(contributionService.getContributedWeight(CHAIN_TASK_ID, A)).isEqualTo(0);
    }

    @Test
    public void shouldNotGetContributedWeightSinceNoTContributed() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CONTRIBUTING));
        when(replicate1.getContributionHash()).thenReturn(A);
        when(replicate1.getWorkerWeight()).thenReturn(3);

        List<Replicate> replicates = Collections.singletonList(replicate1);

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(replicates);

        assertThat(contributionService.getContributedWeight(CHAIN_TASK_ID, A)).isEqualTo(0);
    }

    @Test
    public void shouldGetPendingWeight() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CREATED));
        when(replicate1.getWorkerWeight()).thenReturn(3);

        Replicate replicate2 = mock(Replicate.class);
        when(replicate2.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate2.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CREATED));
        when(replicate2.getWorkerWeight()).thenReturn(5);

        List<Replicate> replicates = Arrays.asList(replicate1, replicate2);

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(replicates);

        assertThat(contributionService.getPendingWeight(CHAIN_TASK_ID, MAX_EXECUTION_TIME)).isEqualTo(15);
    }

    @Test
    public void shouldCount0PendingWeightSinceContributed() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CONTRIBUTED));
        when(replicate1.getWorkerWeight()).thenReturn(3);

        List<Replicate> replicates = Collections.singletonList(replicate1);

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(replicates);

        assertThat(contributionService.getPendingWeight(CHAIN_TASK_ID, MAX_EXECUTION_TIME)).isEqualTo(0);
    }

    @Test
    public void shouldCountOnlyPendingWeightForOneSinceOtherFailed() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CREATED));
        when(replicate1.getWorkerWeight()).thenReturn(3);

        Replicate replicate2 = mock(Replicate.class);
        when(replicate2.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate2.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.FAILED));
        when(replicate2.getWorkerWeight()).thenReturn(5);

        List<Replicate> replicates = Arrays.asList(replicate1, replicate2);

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(replicates);

        assertThat(contributionService.getPendingWeight(CHAIN_TASK_ID, MAX_EXECUTION_TIME)).isEqualTo(3);
    }

    @Test
    public void shouldCountOnlyPendingWeightForOneSinceOtherContributed() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CREATED));
        when(replicate1.getWorkerWeight()).thenReturn(3);

        Replicate replicate2 = mock(Replicate.class);
        when(replicate2.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate2.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CONTRIBUTED));
        when(replicate2.getWorkerWeight()).thenReturn(5);

        List<Replicate> replicates = Arrays.asList(replicate1, replicate2);

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(replicates);

        assertThat(contributionService.getPendingWeight(CHAIN_TASK_ID, MAX_EXECUTION_TIME)).isEqualTo(3);
    }

    @Test
    public void shouldCountOnlyPendingWeightForOneSinceOtherVeryOld() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CREATED));
        when(replicate1.getWorkerWeight()).thenReturn(3);

        Replicate replicate2 = mock(Replicate.class);
        when(replicate2.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(true);
        when(replicate2.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CREATED));
        when(replicate2.getWorkerWeight()).thenReturn(5);

        List<Replicate> replicates = Arrays.asList(replicate1, replicate2);

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(replicates);

        assertThat(contributionService.getPendingWeight(CHAIN_TASK_ID, MAX_EXECUTION_TIME)).isEqualTo(3);
    }

    @Test
    public void shouldCountOnlyPendingWeightForOneSinceOtherNoWeight() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CREATED));
        when(replicate1.getWorkerWeight()).thenReturn(3);

        Replicate replicate2 = mock(Replicate.class);
        when(replicate2.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);
        when(replicate2.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CREATED));
        when(replicate2.getWorkerWeight()).thenReturn(0);

        List<Replicate> replicates = Arrays.asList(replicate1, replicate2);

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(replicates);

        assertThat(contributionService.getPendingWeight(CHAIN_TASK_ID, MAX_EXECUTION_TIME)).isEqualTo(3);
    }

    @Test
    public void shouldGetDistinctContributions() {
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

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(replicates);

        assertThat(contributionService.getDistinctContributions(CHAIN_TASK_ID)).isEqualTo(new HashSet<>(Arrays.asList(A, B)));
    }

    @Test
    public void shouldNotGetDistinctContributionsSinceNotContributed() {
        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.getLastRelevantStatus()).thenReturn(Optional.of(ReplicateStatus.CREATED));

        List<Replicate> replicates = Collections.singletonList(replicate1);

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(replicates);

        assertThat(contributionService.getDistinctContributions(CHAIN_TASK_ID)).isEmpty();
    }

}