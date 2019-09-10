package com.iexec.core.detector.replicate;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.replicate.ReplicateStatusUpdate;
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

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusModifier.*;
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
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder().status(REVEALING).modifier(WORKER).build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));

        when(coreConfigurationService.getUnnotifiedRevealDetectorPeriod()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isStatusTrueOnChain(any(), any(), any())).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getRevealBlock(anyString(), anyString(), anyLong())).thenReturn(ChainReceipt.builder()
                .blockNumber(10L)
                .txHash("0xabcef")
                .build());

        revealDetector.detectOnchainRevealedWhenOffchainRevealed();

        Mockito.verify(replicatesService, Mockito.times(1))//Missed REVEALED
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }

    @Test
    public void shouldDetectUnNotifiedRevealedAfterRevealingSinceBeforeRevealing() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder().modifier(WORKER).status(CONTRIBUTING).build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));

        when(coreConfigurationService.getUnnotifiedRevealDetectorPeriod()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isStatusTrueOnChain(any(), any(), any())).thenReturn(true);
        revealDetector.detectOnchainRevealedWhenOffchainRevealed();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }

    @Test
    public void shouldNotDetectUnNotifiedRevealedAfterRevealingSinceNotRevealedOnChain() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder().modifier(WORKER).status(REVEALING).build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));
        when(coreConfigurationService.getUnnotifiedRevealDetectorPeriod()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isStatusTrueOnChain(any(), any(), any())).thenReturn(false);
        revealDetector.detectOnchainRevealedWhenOffchainRevealed();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }


    //Detector#2

    @Test
    public void shouldDetectUnNotifiedRevealed1() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder().modifier(WORKER).status(REVEALING).build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));

        when(coreConfigurationService.getUnnotifiedRevealDetectorPeriod()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isStatusTrueOnChain(any(), any(), any())).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getRevealBlock(anyString(), anyString(), anyLong())).thenReturn(ChainReceipt.builder()
                .blockNumber(10L)
                .txHash("0xabcef")
                .build());

        revealDetector.detectOnchainRevealed();

        Mockito.verify(replicatesService, Mockito.times(1))//Missed REVEALED
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }

    @Test
    public void shouldDetectUnNotifiedRevealed2() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusDetails details = new ReplicateStatusDetails(10L);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER)
                .status(CONTRIBUTED)
                .details(details)
                .build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));

        when(coreConfigurationService.getUnnotifiedRevealDetectorPeriod()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isStatusTrueOnChain(any(), any(), any())).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getRevealBlock(anyString(), anyString(), anyLong())).thenReturn(ChainReceipt.builder()
                .blockNumber(10L)
                .txHash("0xabcef")
                .build());

        revealDetector.detectOnchainRevealed();

        Mockito.verify(replicatesService, Mockito.times(1))//Missed REVEALING & REVEALED
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }

    @Test
    public void shouldNotDetectUnNotifiedRevealedSinceRevealed() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder().modifier(WORKER).status(REVEALED).build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));

        when(coreConfigurationService.getUnnotifiedRevealDetectorPeriod()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isStatusTrueOnChain(any(), any(), any())).thenReturn(true);
        revealDetector.detectOnchainRevealed();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }
}
