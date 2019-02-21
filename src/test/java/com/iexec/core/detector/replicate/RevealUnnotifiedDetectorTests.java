package com.iexec.core.detector.replicate;

import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.detector.replicate.RevealUnnotifiedDetector;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RevealUnnotifiedDetectorTests {

    private final static String WALLET_WORKER_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private final static String WALLET_WORKER_2 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd249";
    private final static String CHAIN_TASK_ID = "chainTaskId";

    @Mock
    private TaskService taskService;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private IexecHubService iexecHubService;

    @InjectMocks
    private RevealUnnotifiedDetector detector;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldDetectUnnotifiedRevealed() {
        Task task = mock(Task.class);
        when(task.getChainTaskId()).thenReturn(CHAIN_TASK_ID);
        when(task.isConsensusReachedSinceMultiplePeriods(1)).thenReturn(true);

        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);

        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));
        when(replicatesService.getReplicates(task.getChainTaskId())).thenReturn(Arrays.asList(replicate1));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(task.getChainTaskId(),
                WALLET_WORKER_1, ChainContributionStatus.REVEALED)).thenReturn(true);

        detector.detect();

        Mockito.verify(replicatesService, Mockito.times(1))
                .getReplicates(Mockito.any());

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(any(), any(), any(), any());
    }

    @Test
    public void shouldNotDetectUnnotifiedRevealedSinceReplicateContainsRevealedStatus() {
        Task task = mock(Task.class);
        when(task.getChainTaskId()).thenReturn(CHAIN_TASK_ID);
        when(task.isConsensusReachedSinceMultiplePeriods(1)).thenReturn(true);

        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));
        when(replicatesService.getReplicates(task.getChainTaskId())).thenReturn(Arrays.asList(replicate1));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(task.getChainTaskId(),
                WALLET_WORKER_1, ChainContributionStatus.REVEALED)).thenReturn(true);

        detector.detect();

        Mockito.verify(replicatesService, Mockito.times(1))
                .getReplicates(Mockito.any());

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any());
    }

    @Test
    public void shouldNotDetectUnnotifiedRevealedSinceOnChainIsNotRevealed() {
        Task task = mock(Task.class);
        when(task.getChainTaskId()).thenReturn(CHAIN_TASK_ID);
        when(task.isConsensusReachedSinceMultiplePeriods(1)).thenReturn(true);

        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);

        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));
        when(replicatesService.getReplicates(task.getChainTaskId())).thenReturn(Arrays.asList(replicate1));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(task.getChainTaskId(),
                WALLET_WORKER_1, ChainContributionStatus.REVEALED)).thenReturn(false);

        detector.detect();

        Mockito.verify(replicatesService, Mockito.times(1))
                .getReplicates(Mockito.any());

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any());
    }

    @Test
    public void shouldNotDetectUnnotifiedRevealedSinceConsensusReachedSinceNotLong() {
        Task task = mock(Task.class);
        when(task.getChainTaskId()).thenReturn(CHAIN_TASK_ID);
        when(task.isConsensusReachedSinceMultiplePeriods(1)).thenReturn(false);

        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);

        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));
        when(replicatesService.getReplicates(task.getChainTaskId())).thenReturn(Arrays.asList(replicate1));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(task.getChainTaskId(),
                WALLET_WORKER_1, ChainContributionStatus.REVEALED)).thenReturn(true);

        detector.detect();

        Mockito.verify(replicatesService, Mockito.times(1))
                .getReplicates(Mockito.any());

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any());
    }
}
