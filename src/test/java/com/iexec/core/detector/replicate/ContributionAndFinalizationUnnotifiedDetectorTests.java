/*
 * Copyright 2023-2025 IEXEC BLOCKCHAIN TECH
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
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.tee.TeeFramework;
import com.iexec.core.chain.IexecHubService;
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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusModifier.WORKER;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContributionAndFinalizationUnnotifiedDetectorTests {
    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final String WALLET_ADDRESS = "0x1";
    private static final String CALLBACK = "callback";

    @Mock
    private TaskService taskService;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    public IexecHubService iexecHubService;

    @Mock
    private CronConfiguration cronConfiguration;

    @Spy
    @InjectMocks
    private ContributionAndFinalizationUnnotifiedDetector detector;

    @Captor
    private ArgumentCaptor<ReplicateStatusUpdate> statusUpdate;

    @BeforeEach
    void init() {
        ReflectionTestUtils.setField(detector, "detectorRate", 1000);
    }

    private Replicate getReplicateWithStatus(final ReplicateStatus replicateStatus) {
        final Replicate replicate = new Replicate(WALLET_ADDRESS, CHAIN_TASK_ID);
        final ReplicateStatusUpdate replicateStatusUpdate = ReplicateStatusUpdate.builder()
                .modifier(WORKER).status(replicateStatus).build();
        replicate.setStatusUpdateList(Collections.singletonList(replicateStatusUpdate));
        return replicate;
    }

    // Helper method to avoid redundancy
    private void mockTaskAndTaskDecription(final String callback) {
        final Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(iexecHubService.getTaskDescription(anyString())).thenReturn(
                TaskDescription.builder()
                        .trust(BigInteger.ONE)
                        .isTeeTask(true)
                        .teeFramework(TeeFramework.SCONE)
                        .callback(callback)
                        .build()
        );
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingContributionStatuses())).thenReturn(Collections.singletonList(task));
    }

    // region detectOnChainChanges

    /**
     * When running {@link ContributionAndFinalizationUnnotifiedDetector#detectOnChainChanges} 10 times,
     * {@link ReplicatesService#updateReplicateStatus(String, String, ReplicateStatusUpdate)} should be called 11 times:
     * <ol>
     *     <li>10 times from {@link ContributionAndFinalizationUnnotifiedDetector#detectOnchainDoneWhenOffchainOngoing()};</li>
     *     <li>1 time from {@link ContributionAndFinalizationUnnotifiedDetector#detectOnchainDone()}</li>
     * </ol>
     */
    @ParameterizedTest
    @ValueSource(strings = {"", CALLBACK})
    void shouldDetectBothChangesOnChain(final String callback) {
        mockTaskAndTaskDecription(callback);

        final Replicate replicate = getReplicateWithStatus(CONTRIBUTE_AND_FINALIZE_ONGOING);
        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isRevealed(CHAIN_TASK_ID, WALLET_ADDRESS)).thenReturn(true);

        for (int i = 0; i < 10; i++) {
            detector.detectOnChainChanges();
        }

        Mockito.verify(replicatesService, Mockito.times(11))
                .updateReplicateStatus(
                        eq(CHAIN_TASK_ID),
                        eq(WALLET_ADDRESS),
                        statusUpdate.capture()
                );
    }

    // endregion

    // region detectOnchainDoneWhenOffchainOngoing (ContributeAndFinalizeOngoing)

    @ParameterizedTest
    @ValueSource(strings = {"", CALLBACK})
    void shouldDetectMissedUpdateSinceOffChainOngoing(final String callback) {
        mockTaskAndTaskDecription(callback);

        final Replicate replicate = getReplicateWithStatus(CONTRIBUTE_AND_FINALIZE_ONGOING);
        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isRevealed(CHAIN_TASK_ID, WALLET_ADDRESS)).thenReturn(true);

        detector.detectOnchainDoneWhenOffchainOngoing();

        Mockito.verify(replicatesService, Mockito.times(1)) // Missed CONTRIBUTE_AND_FINALIZE_DONE
                .updateReplicateStatus(
                        eq(CHAIN_TASK_ID),
                        eq(WALLET_ADDRESS),
                        statusUpdate.capture()
                );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", CALLBACK})
    void shouldNotDetectMissedUpdateSinceNotOffChainOngoing(final String callback) {
        detector.detectOnchainDoneWhenOffchainOngoing();

        Mockito.verify(replicatesService, never())
                .updateReplicateStatus(any(), any(), any(ReplicateStatusUpdate.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", CALLBACK})
    void shouldNotDetectMissedUpdateSinceNotOnChainDone(final String callback) {
        mockTaskAndTaskDecription(callback);

        final Replicate replicate = getReplicateWithStatus(CONTRIBUTE_AND_FINALIZE_ONGOING);
        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isRevealed(CHAIN_TASK_ID, WALLET_ADDRESS)).thenReturn(false);
        detector.detectOnchainDoneWhenOffchainOngoing();

        Mockito.verify(replicatesService, never())
                .updateReplicateStatus(any(), any(), any(ReplicateStatusUpdate.class));
    }

    // endregion

    // region detectOnchainDone (REVEALED)

    static Stream<Arguments> provideReplicateStatusAndCallback() {
        return Stream.of(
                Arguments.of(ReplicateStatus.COMPUTED, ""),
                Arguments.of(ReplicateStatus.COMPUTED, CALLBACK),
                Arguments.of(ReplicateStatus.CONTRIBUTE_AND_FINALIZE_ONGOING, ""),
                Arguments.of(ReplicateStatus.CONTRIBUTE_AND_FINALIZE_ONGOING, CALLBACK)
        );
    }

    @ParameterizedTest
    @MethodSource("provideReplicateStatusAndCallback")
    void shouldDetectMissedUpdateSinceOnChainDoneNotOffChainDone(final ReplicateStatus replicateStatus, final String callback) {
        mockTaskAndTaskDecription(callback);

        final Replicate replicate = getReplicateWithStatus(replicateStatus);
        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(Collections.singletonList(replicate));
        when(iexecHubService.isRevealed(CHAIN_TASK_ID, WALLET_ADDRESS)).thenReturn(true);

        detector.detectOnchainDone();

        final List<ReplicateStatus> missingStatuses = getMissingStatuses(replicateStatus, CONTRIBUTE_AND_FINALIZE_DONE);
        Mockito.verify(replicatesService, Mockito.times(missingStatuses.size())) // Missed CONTRIBUTE_AND_FINALIZE_DONE
                .updateReplicateStatus(
                        eq(CHAIN_TASK_ID),
                        eq(WALLET_ADDRESS),
                        statusUpdate.capture()
                );
        assertThat(statusUpdate.getAllValues()).hasSize(missingStatuses.size());
    }

    @Test
    void shouldNotDetectMissedUpdateSinceOnChainDoneAndOffChainDone() {
        final Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingContributionStatuses())).thenReturn(Collections.singletonList(task));

        final Replicate replicate = getReplicateWithStatus(CONTRIBUTE_AND_FINALIZE_DONE);
        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(Collections.singletonList(replicate));
        detector.detectOnchainDone();

        Mockito.verify(replicatesService, never())
                .updateReplicateStatus(any(), any(), any(ReplicateStatusUpdate.class));
    }

    @Test
    void shouldNotDetectMissedUpdateSinceOnChainDoneAndNotEligibleToContributeAndFinalize() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(
                TaskDescription.builder().trust(BigInteger.ONE).isTeeTask(true).callback("0x2").build());
        final Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        when(taskService.findByCurrentStatus(TaskStatus.getWaitingContributionStatuses())).thenReturn(Collections.singletonList(task));

        final Replicate replicate = getReplicateWithStatus(CONTRIBUTING);
        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate));

        detector.detectOnchainDone();

        Mockito.verify(replicatesService, never())
                .updateReplicateStatus(any(), any(), any(ReplicateStatusUpdate.class));
    }

    // endregion

}
