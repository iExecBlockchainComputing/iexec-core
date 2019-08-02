package com.iexec.core.detector.replicate;

import com.iexec.common.chain.ChainReceipt;
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

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ContributionUnnotifiedDetectorTests {

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
    private ContributionUnnotifiedDetector contributionDetector;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }


    //Detector#1 after contributing

    @Test
    public void shouldDetectUnNotifiedContributedAfterContributing() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        replicate.setStatusChangeList(Collections.singletonList(new ReplicateStatusChange(ReplicateStatus.CONTRIBUTING, ReplicateStatusModifier.WORKER)));

        when(coreConfigurationService.getUnnotifiedContributionDetectorPeriod()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getContributionBlock(anyString(), anyString(), anyLong())).thenReturn(ChainReceipt.builder()
                .blockNumber(10L)
                .txHash("0xabcef")
                .build());

        contributionDetector.detectOnchainContributedWhenOffchainContributing();

        Mockito.verify(replicatesService, Mockito.times(1))//Missed CONTRIBUTED
                .updateReplicateStatus(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldDetectUnNotifiedContributedAfterContributingSinceBeforeContributing() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        replicate.setStatusChangeList(Collections.singletonList(new ReplicateStatusChange(ReplicateStatus.CAN_CONTRIBUTE, ReplicateStatusModifier.WORKER)));

        when(coreConfigurationService.getUnnotifiedContributionDetectorPeriod()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(true);
        contributionDetector.detectOnchainContributedWhenOffchainContributing();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any());
    }

    @Test
    public void shouldNotDetectUnNotifiedContributedAfterContributingSinceNotContributedOnChain() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        replicate.setStatusChangeList(Collections.singletonList(new ReplicateStatusChange(ReplicateStatus.CONTRIBUTING, ReplicateStatusModifier.WORKER)));

        when(coreConfigurationService.getUnnotifiedContributionDetectorPeriod()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(false);
        contributionDetector.detectOnchainContributedWhenOffchainContributing();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any());
    }


    //Detector#2

    @Test
    public void shouldDetectUnNotifiedContributed1() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        replicate.setStatusChangeList(Collections.singletonList(new ReplicateStatusChange(ReplicateStatus.CONTRIBUTING, ReplicateStatusModifier.WORKER)));

        when(coreConfigurationService.getUnnotifiedContributionDetectorPeriod()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getContributionBlock(anyString(), anyString(), anyLong())).thenReturn(ChainReceipt.builder()
                .blockNumber(10L)
                .txHash("0xabcef")
                .build());

        contributionDetector.detectOnchainContributed();

        Mockito.verify(replicatesService, Mockito.times(1))//Missed CONTRIBUTED
                .updateReplicateStatus(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldDetectUnNotifiedContributed2() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        replicate.setStatusChangeList(Collections.singletonList(new ReplicateStatusChange(ReplicateStatus.CAN_CONTRIBUTE, ReplicateStatusModifier.WORKER)));

        when(coreConfigurationService.getUnnotifiedContributionDetectorPeriod()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getContributionBlock(anyString(), anyString(), anyLong())).thenReturn(ChainReceipt.builder()
                .blockNumber(10L)
                .txHash("0xabcef")
                .build());

        contributionDetector.detectOnchainContributed();

        Mockito.verify(replicatesService, Mockito.times(1))//Missed CONTRIBUTING & CONTRIBUTED
                .updateReplicateStatus(any(), any(), any(), any(), any());
    }

    @Test
    public void shouldNotDetectUnNotifiedContributedSinceContributed() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        replicate.setStatusChangeList(Collections.singletonList(new ReplicateStatusChange(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER)));

        when(coreConfigurationService.getUnnotifiedContributionDetectorPeriod()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(true);
        contributionDetector.detectOnchainContributed();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any());
    }

}
