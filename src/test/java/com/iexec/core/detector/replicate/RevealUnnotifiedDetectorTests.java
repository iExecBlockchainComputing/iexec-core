package com.iexec.core.detector.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusChange;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.configuration.CoreConfigurationService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RevealUnnotifiedDetectorTests {

    private final static String CHAIN_TASK_ID = "chainTaskId";
    private final static String WALLET_ADDRESS = "0x1";
    private final static int DETECTOR_PERIOD = 1000;

    @Mock
    private TaskService taskService;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private IexecHubService iexecHubService;

    @Mock
    private CoreConfigurationService coreConfigurationService;

    @Mock
    private Web3jService web3jService;

    @Spy
    @InjectMocks
    private RevealUnnotifiedDetector revealDetector;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }


    //Detector#1 after contributing

    @Test
    public void shouldDetectUnNotifiedRevealedAfterRevealing() {
        Task task = mock(Task.class);
        when(task.getChainTaskId()).thenReturn(any());
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS,CHAIN_TASK_ID);
        replicate.setStatusChangeList(Collections.singletonList(new ReplicateStatusChange(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER)));

        when(coreConfigurationService.getUnnotifiedRevealDetectorPeriod()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getRevealBlockNumber(anyString(), anyString(), anyLong())).thenReturn(10L);

        revealDetector.detectOnchainRevealedWhenOffchainRevealed();

        Mockito.verify(replicatesService, Mockito.times(1))//Missed REVEALED
                .updateReplicateStatus(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldDetectUnNotifiedRevealedAfterRevealingSinceBeforeRevealing() {
        Task task = mock(Task.class);
        when(task.getChainTaskId()).thenReturn(any());
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS,CHAIN_TASK_ID);
        replicate.setStatusChangeList(Collections.singletonList(new ReplicateStatusChange(ReplicateStatus.CAN_CONTRIBUTE, ReplicateStatusModifier.WORKER)));

        when(coreConfigurationService.getUnnotifiedRevealDetectorPeriod()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(true);
        revealDetector.detectOnchainRevealedWhenOffchainRevealed();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any());
    }

    @Test
    public void shouldNotDetectUnNotifiedRevealedAfterRevealingSinceNotRevealedOnChain() {
        Task task = mock(Task.class);
        when(task.getChainTaskId()).thenReturn(any());
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS,CHAIN_TASK_ID);
        replicate.setStatusChangeList(Collections.singletonList(new ReplicateStatusChange(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER)));

        when(coreConfigurationService.getUnnotifiedRevealDetectorPeriod()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(false);
        revealDetector.detectOnchainRevealedWhenOffchainRevealed();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any());
    }


    //Detector#2

    @Test
    public void shouldDetectUnNotifiedRevealed1() {
        Task task = mock(Task.class);
        when(task.getChainTaskId()).thenReturn(any());
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS,CHAIN_TASK_ID);
        replicate.setStatusChangeList(Collections.singletonList(new ReplicateStatusChange(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER)));

        when(coreConfigurationService.getUnnotifiedRevealDetectorPeriod()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getRevealBlockNumber(anyString(), anyString(), anyLong())).thenReturn(10L);

        revealDetector.detectOnchainRevealed();

        Mockito.verify(replicatesService, Mockito.times(1))//Missed REVEALED
                .updateReplicateStatus(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldDetectUnNotifiedRevealed2() {
        Task task = mock(Task.class);
        when(task.getChainTaskId()).thenReturn(any());
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS,CHAIN_TASK_ID);
        replicate.setStatusChangeList(Collections.singletonList(new ReplicateStatusChange(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER)));

        when(coreConfigurationService.getUnnotifiedRevealDetectorPeriod()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getRevealBlockNumber(anyString(), anyString(), anyLong())).thenReturn(10L);

        revealDetector.detectOnchainRevealed();

        Mockito.verify(replicatesService, Mockito.times(1))//Missed REVEALING & REVEALED
                .updateReplicateStatus(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldNotDetectUnNotifiedRevealedSinceRevealed() {
        Task task = mock(Task.class);
        when(task.getChainTaskId()).thenReturn(any());
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS,CHAIN_TASK_ID);
        replicate.setStatusChangeList(Collections.singletonList(new ReplicateStatusChange(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER)));

        when(coreConfigurationService.getUnnotifiedRevealDetectorPeriod()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(true);
        revealDetector.detectOnchainRevealed();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any());
    }
}
