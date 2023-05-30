package com.iexec.core.detector.replicate;

import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.commons.poco.chain.ChainContributionStatus;
import com.iexec.commons.poco.chain.ChainReceipt;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.configuration.CronConfiguration;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.Arrays;
import java.util.Collections;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusModifier.WORKER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ContributionAndFinalizationUnnotifiedDetectorTests {
    private final static String CHAIN_TASK_ID = "chainTaskId";
    private final static String WALLET_ADDRESS = "0x1";

    @Mock
    private TaskService taskService;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    public IexecHubService iexecHubService;

    @Mock
    private CronConfiguration cronConfiguration;

    @Mock
    private Web3jService web3jService;

    @Spy
    @InjectMocks
    private ContributionAndFinalizationUnnotifiedDetector detector;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    // Detector aggregator
    @Test
    void shouldDetectBothChangesOnChain() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingContributionStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder().status(CONTRIBUTE_AND_FINALIZE_ONGOING).modifier(WORKER).build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isStatusTrueOnChain(CHAIN_TASK_ID, WALLET_ADDRESS, ChainContributionStatus.REVEALED)).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getFinalizeBlock(CHAIN_TASK_ID, 0))
                .thenReturn(ChainReceipt.builder()
                        .blockNumber(10L)
                        .txHash("0xabcef")
                        .build()
                );

        for (int i = 0; i < 10; i++) {
            detector.detectOnChainChanges();
        }

        Mockito.verify(replicatesService, Mockito.times(11))    // 10 detectors #1 & 1 detector #2
                .updateReplicateStatus(
                        eq(CHAIN_TASK_ID),
                        eq(WALLET_ADDRESS),
                        eq(CONTRIBUTE_AND_FINALIZE_DONE),
                        any(ReplicateStatusDetails.class)
                );
    }


    //Detector#1 after ContributeAndFinalize ongoing

    @Test
    void shouldDetectUnNotifiedContributeAndFinalizeDoneAfterContributeAndFinalizeOngoing() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder().status(CONTRIBUTE_AND_FINALIZE_ONGOING).build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isStatusTrueOnChain(CHAIN_TASK_ID, WALLET_ADDRESS, ChainContributionStatus.REVEALED)).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getFinalizeBlock(CHAIN_TASK_ID, 0))
                .thenReturn(ChainReceipt.builder()
                        .blockNumber(10L)
                        .txHash("0xabcef")
                        .build()
                );

        detector.detectOnchainCompletedWhenOffchainCompleting();

        Mockito.verify(replicatesService, Mockito.times(1)) // Missed CONTRIBUTE_AND_FINALIZE_DONE
                .updateReplicateStatus(
                        eq(CHAIN_TASK_ID),
                        eq(WALLET_ADDRESS),
                        eq(CONTRIBUTE_AND_FINALIZE_DONE),
                        any(ReplicateStatusDetails.class)
                );
    }

    @Test
    void shouldDetectUnNotifiedContributeAndFinalizeDoneSinceBeforeContributeAndFinalizeOngoing() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder().status(COMPUTED).build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isStatusTrueOnChain(CHAIN_TASK_ID, WALLET_ADDRESS, ChainContributionStatus.REVEALED)).thenReturn(true);

        detector.detectOnchainCompletedWhenOffchainCompleting();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }

    @Test
    void shouldNotDetectUnNotifiedContributeAndFinalizeDoneSinceNotFinalizedOnChain() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder().status(CONTRIBUTE_AND_FINALIZE_ONGOING).build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isStatusTrueOnChain(CHAIN_TASK_ID, WALLET_ADDRESS, ChainContributionStatus.REVEALED)).thenReturn(false);
        detector.detectOnchainCompletedWhenOffchainCompleting();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }


    //Detector#2

    @Test
    void shouldDetectUnNotifiedContributeAndFinalizeOngoing() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder().status(CONTRIBUTE_AND_FINALIZE_ONGOING).build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isStatusTrueOnChain(CHAIN_TASK_ID, WALLET_ADDRESS, ChainContributionStatus.REVEALED)).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getFinalizeBlock(CHAIN_TASK_ID, 0)).thenReturn(ChainReceipt.builder()
                .blockNumber(10L)
                .txHash("0xabcef")
                .build());

        detector.detectOnchainCompleted();

        Mockito.verify(replicatesService, Mockito.times(1)) // Missed CONTRIBUTE_AND_FINALIZE_DONE
                .updateReplicateStatus(
                        eq(CHAIN_TASK_ID),
                        eq(WALLET_ADDRESS),
                        eq(CONTRIBUTE_AND_FINALIZE_DONE),
                        any(ReplicateStatusDetails.class)
                );
    }

    @Test
    void shouldNotDetectUnNotifiedContributedSinceContributeAndFinalizeDone() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder().status(CONTRIBUTE_AND_FINALIZE_DONE).build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isStatusTrueOnChain(CHAIN_TASK_ID, WALLET_ADDRESS, ChainContributionStatus.REVEALED)).thenReturn(true);
        detector.detectOnchainCompleted();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }

}
