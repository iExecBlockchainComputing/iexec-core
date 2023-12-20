/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.detector.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.replicate.ReplicateStatusUpdate;
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

import java.util.Collections;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusModifier.WORKER;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class RevealUnnotifiedDetectorTests {

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
    private CronConfiguration cronConfiguration;

    @Mock
    private Web3jService web3jService;

    @Spy
    @InjectMocks
    private RevealUnnotifiedDetector revealDetector;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    // region Detector aggregator

    /**
     * When running {@link RevealUnnotifiedDetector#detectOnChainChanges} 10 times,
     * {@link ReplicatesService#updateReplicateStatus(String, String, ReplicateStatus, ReplicateStatusDetails)} should be called 11 times:
     * <ol>
     *     <li>10 times from {@link RevealUnnotifiedDetector#detectOnchainDoneWhenOffchainOngoing()};</li>
     *     <li>1 time from {@link RevealUnnotifiedDetector#detectOnchainDone()}</li>
     * </ol>
     */
    @Test
    void shouldDetectBothChangesOnChain() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder().status(REVEALING).modifier(WORKER).build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));

        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isRevealed(any(), any())).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getRevealBlock(anyString(), anyString(), anyLong())).thenReturn(ChainReceipt.builder()
                .blockNumber(10L)
                .txHash("0xabcef")
                .build());

        for (int i = 0; i < 10; i++) {
            revealDetector.detectOnChainChanges();
        }

        Mockito.verify(replicatesService, Mockito.times(11))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }

    // endregion

    //region detectOnchainDoneWhenOffchainOngoing (REVEALING)

    @Test
    void shouldDetectUnNotifiedRevealedAfterRevealing() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder().status(REVEALING).modifier(WORKER).build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));

        when(cronConfiguration.getReveal()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isRevealed(any(), any())).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getRevealBlock(anyString(), anyString(), anyLong())).thenReturn(ChainReceipt.builder()
                .blockNumber(10L)
                .txHash("0xabcef")
                .build());

        revealDetector.detectOnchainDoneWhenOffchainOngoing();

        Mockito.verify(replicatesService, Mockito.times(1))//Missed REVEALED
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }

    @Test
    void shouldDetectUnNotifiedRevealedAfterRevealingSinceBeforeRevealing() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder().modifier(WORKER).status(CONTRIBUTING).build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));

        when(cronConfiguration.getReveal()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isRevealed(any(), any())).thenReturn(true);
        revealDetector.detectOnchainDoneWhenOffchainOngoing();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }

    @Test
    void shouldNotDetectUnNotifiedRevealedAfterRevealingSinceNotRevealedOnChain() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder().modifier(WORKER).status(REVEALING).build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));
        when(cronConfiguration.getReveal()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isRevealed(any(), any())).thenReturn(false);
        revealDetector.detectOnchainDoneWhenOffchainOngoing();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }

    // endregion

    // region detectOnchainDone (REVEALED)

    @Test
    void shouldDetectUnNotifiedRevealed1() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder().modifier(WORKER).status(REVEALING).build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));

        when(cronConfiguration.getReveal()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isRevealed(any(), any())).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getRevealBlock(anyString(), anyString(), anyLong())).thenReturn(ChainReceipt.builder()
                .blockNumber(10L)
                .txHash("0xabcef")
                .build());

        revealDetector.detectOnchainDone();

        Mockito.verify(replicatesService, Mockito.times(1))//Missed REVEALED
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }

    @Test
    void shouldDetectUnNotifiedRevealed2() {
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

        when(cronConfiguration.getReveal()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isRevealed(any(), any())).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getRevealBlock(anyString(), anyString(), anyLong())).thenReturn(ChainReceipt.builder()
                .blockNumber(10L)
                .txHash("0xabcef")
                .build());

        revealDetector.detectOnchainDone();

        Mockito.verify(replicatesService, Mockito.times(1))//Missed REVEALING & REVEALED
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }

    @Test
    void shouldNotDetectUnNotifiedRevealedSinceRevealed() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingRevealStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder().modifier(WORKER).status(REVEALED).build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));

        when(cronConfiguration.getReveal()).thenReturn(DETECTOR_PERIOD);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isRevealed(any(), any())).thenReturn(true);
        revealDetector.detectOnchainDone();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }

    // endregion
}
