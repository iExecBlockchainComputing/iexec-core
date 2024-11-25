/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.commons.poco.chain.ChainReceipt;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.utils.BytesUtils;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusModifier.WORKER;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContributionUnnotifiedDetectorTests {

    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final String WALLET_ADDRESS = "0x1";

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
    private ContributionUnnotifiedDetector contributionDetector;

    @BeforeEach
    void init() {
        ReflectionTestUtils.setField(contributionDetector, "detectorRate", 1000);
        when(iexecHubService.getTaskDescription(anyString())).thenReturn(TaskDescription.builder()
                .trust(BigInteger.ONE)
                .isTeeTask(false)
                .build());
    }

    private Replicate getReplicateWithStatus(final ReplicateStatus replicateStatus) {
        Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER).status(replicateStatus).build();
        replicate.setStatusUpdateList(Collections.singletonList(statusUpdate));
        return replicate;
    }

    // region detectOnChainChanges

    /**
     * When running {@link ContributionUnnotifiedDetector#detectOnChainChanges} 10 times,
     * {@link ReplicatesService#updateReplicateStatus(String, String, ReplicateStatusUpdate)} should be called 11 times:
     * <ol>
     *     <li>10 times from {@link ContributionUnnotifiedDetector#detectOnchainDoneWhenOffchainOngoing()};</li>
     *     <li>1 time from {@link ContributionUnnotifiedDetector#detectOnchainDone()}</li>
     * </ol>
     */
    @Test
    void shouldDetectBothChangesOnChain() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingContributionStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = getReplicateWithStatus(CONTRIBUTING);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isContributed(any(), any())).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getContributionBlock(anyString(), anyString(), anyLong())).thenReturn(ChainReceipt.builder()
                .blockNumber(10L)
                .txHash("0xabcef")
                .build());

        for (int i = 0; i < 10; i++) {
            contributionDetector.detectOnChainChanges();
        }

        Mockito.verify(replicatesService, Mockito.times(11))
                .updateReplicateStatus(any(), any(), any(ReplicateStatusUpdate.class));
    }

    // endregion

    // region detectOnchainDoneWhenOffchainOngoing (CONTRIBUTING)

    @Test
    void shouldDetectMissedUpdateSinceOffChainOngoing() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingContributionStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = getReplicateWithStatus(CONTRIBUTING);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isContributed(any(), any())).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getContributionBlock(anyString(), anyString(), anyLong()))
                .thenReturn(ChainReceipt.builder().blockNumber(10L).txHash("0xabcef").build());

        contributionDetector.detectOnchainDoneWhenOffchainOngoing();

        Mockito.verify(replicatesService, Mockito.times(1)) // Missed CONTRIBUTED
                .updateReplicateStatus(any(), any(), any(ReplicateStatusUpdate.class));
    }

    @Test
    void shouldNotDetectMissedUpdateSinceNotOffChainOngoing() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingContributionStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = getReplicateWithStatus(COMPUTED);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isContributed(any(), any())).thenReturn(true);

        contributionDetector.detectOnchainDoneWhenOffchainOngoing();

        Mockito.verify(replicatesService, never())
                .updateReplicateStatus(any(), any(), any(ReplicateStatusUpdate.class));
    }

    @Test
    void shouldNotDetectMissedUpdateSinceNotOnChainDone() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingContributionStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = getReplicateWithStatus(CONTRIBUTING);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isContributed(any(), any())).thenReturn(false);
        contributionDetector.detectOnchainDoneWhenOffchainOngoing();

        Mockito.verify(replicatesService, never())
                .updateReplicateStatus(any(), any(), any(ReplicateStatusUpdate.class));
    }

    // endregion

    // region detectOnchainDone (CONTRIBUTED)

    @ParameterizedTest
    @EnumSource(value = ReplicateStatus.class, names = {"COMPUTED", "CONTRIBUTING"})
    void shouldDetectMissedUpdateSinceOnChainDoneNotOffChainDone(ReplicateStatus replicateStatus) {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingContributionStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = getReplicateWithStatus(replicateStatus);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isContributed(any(), any())).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getContributionBlock(anyString(), anyString(), anyLong())).thenReturn(ChainReceipt.builder()
                .blockNumber(10L)
                .txHash("0xabcef")
                .build());

        contributionDetector.detectOnchainDone();

        final ArgumentCaptor<ReplicateStatusUpdate> statusUpdate = ArgumentCaptor.forClass(ReplicateStatusUpdate.class);
        final List<ReplicateStatus> missingStatuses = ReplicateStatus.getMissingStatuses(replicateStatus, CONTRIBUTED);
        Mockito.verify(replicatesService, Mockito.times(missingStatuses.size()))//Missed CONTRIBUTED
                .updateReplicateStatus(any(), any(), statusUpdate.capture());
        final List<ReplicateStatus> newStatuses = statusUpdate.getAllValues().stream()
                .map(ReplicateStatusUpdate::getStatus)
                .collect(Collectors.toList());
        assertThat(newStatuses).isEqualTo(missingStatuses);
    }

    @Test
    void shouldNotDetectMissedUpdateSinceOnChainDoneAndOffChainDone() {
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingContributionStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = getReplicateWithStatus(CONTRIBUTED);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isContributed(any(), any())).thenReturn(true);
        contributionDetector.detectOnchainDone();

        Mockito.verify(replicatesService, never())
                .updateReplicateStatus(any(), any(), any(ReplicateStatusUpdate.class));
    }

    @Test
    void shouldNotDetectMissedUpdateSinceOnChainDoneAndEligibleToContributeAndFinalize() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(
                TaskDescription.builder().trust(BigInteger.ONE).isTeeTask(true).callback(BytesUtils.EMPTY_ADDRESS).build());
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingContributionStatuses())).thenReturn(Collections.singletonList(task));

        Replicate replicate = getReplicateWithStatus(CONTRIBUTING);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isContributed(any(), any())).thenReturn(true);
        when(web3jService.getLatestBlockNumber()).thenReturn(11L);
        when(iexecHubService.getContributionBlock(anyString(), anyString(), anyLong())).thenReturn(ChainReceipt.builder()
                .blockNumber(10L)
                .txHash("0xabcef")
                .build());

        contributionDetector.detectOnchainDone();

        Mockito.verify(replicatesService, never())
                .updateReplicateStatus(any(), any(), any(ReplicateStatusUpdate.class));
    }

    // endregion
}
