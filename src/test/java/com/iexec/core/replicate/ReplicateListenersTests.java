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

package com.iexec.core.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.core.detector.replicate.ContributionUnnotifiedDetector;
import com.iexec.core.task.listener.ReplicateListeners;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import com.iexec.core.worker.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusCause.TASK_NOT_ACTIVE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.*;

class ReplicateListenersTests {

    private final static String CHAIN_TASK_ID = "chainTaskId";
    private final static String WORKER_WALLET = "0xwallet1";

    @Mock
    private WorkerService workerService;
    @Mock
    private ContributionUnnotifiedDetector contributionUnnotifiedDetector;
    @Mock
    private ReplicatesService replicatesService;
    @Mock
    private TaskUpdateRequestManager taskUpdateRequestManager;

    @InjectMocks
    private ReplicateListeners replicateListeners;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldUpdateTaskOnReplicateUpdate() {
        List<ReplicateStatus> someStatuses = ReplicateStatus.getSuccessStatuses(); //not exhaustive

        for (ReplicateStatus randomStatus : someStatuses) {
            ReplicateUpdatedEvent replicateUpdatedEvent = getMockReplicate(randomStatus);

            replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);
        }

        Mockito.verify(taskUpdateRequestManager, Mockito.times(someStatuses.size())).publishRequest(any());
    }

    @Test
    void shouldRemoveFromComputedTasksSinceStartFailed() {
        assertIsRemovedFromComputedTasks(START_FAILED);
    }

    @Test
    void shouldRemoveFromComputedTasksSinceAppDownloadFailed() {
        assertIsRemovedFromComputedTasks(APP_DOWNLOAD_FAILED);
    }

    @Test
    void shouldRemoveFromComputedTasksSinceDataDownloadFailed() {
        assertIsRemovedFromComputedTasks(DATA_DOWNLOAD_FAILED);
    }

    @Test
    void shouldRemoveFromComputedTasksSinceComputedFailed() {
        assertIsRemovedFromComputedTasks(COMPUTED);
    }

    @Test
    void shouldRemoveFromComputedTasksSinceComputeFailed() {
        assertIsRemovedFromComputedTasks(COMPUTE_FAILED);
    }

    private void assertIsRemovedFromComputedTasks(ReplicateStatus computed) {
        ReplicateUpdatedEvent replicateUpdatedEvent = getMockReplicate(computed);
        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);
        Mockito.verify(workerService, Mockito.times(1))
                .removeComputedChainTaskIdFromWorker(CHAIN_TASK_ID, WORKER_WALLET);
    }

    @Test
    void shouldTriggerDetectOnchainContributedSinceTaskNotActive() {
        ReplicateUpdatedEvent replicateUpdatedEvent = ReplicateUpdatedEvent.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .walletAddress(WORKER_WALLET)
                .replicateStatusUpdate(new ReplicateStatusUpdate(CONTRIBUTING, TASK_NOT_ACTIVE))
                .build();

        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);

        Mockito.verify(contributionUnnotifiedDetector, Mockito.times(1)).detectOnchainDone();
    }

    @Test
    void shouldNotTriggerDetectOnchain() {
        List<ReplicateStatus> someStatuses = ReplicateStatus.getSuccessStatuses(); //not exhaustive
        someStatuses.remove(CONTRIBUTING);

        for (ReplicateStatus randomStatus : someStatuses) {
            ReplicateUpdatedEvent replicateUpdatedEvent = getMockReplicate(randomStatus);

            replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);
        }

        Mockito.verify(contributionUnnotifiedDetector, Mockito.times(0)).detectOnchainDone();
    }

    @Test
    void shouldNotTriggerDetectOnchainContributedSinceCauseIsNull() {
        ReplicateUpdatedEvent replicateUpdatedEvent = ReplicateUpdatedEvent.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .walletAddress(WORKER_WALLET)
                .replicateStatusUpdate(new ReplicateStatusUpdate(CONTRIBUTING))
                .build();

        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);

        Mockito.verify(contributionUnnotifiedDetector, Mockito.times(0)).detectOnchainDone();
    }

    static Stream<ReplicateStatus> getUncompletableStatuses() {
        return ReplicateStatus.getUncompletableStatuses().stream();
    }

    @ParameterizedTest
    @MethodSource("getUncompletableStatuses")
    void shouldAddFailedStatusSinceUncompletableReplicateStatus(ReplicateStatus uncompletableStatus) {
        ReplicateUpdatedEvent replicateUpdatedEvent = getMockReplicate(uncompletableStatus);
        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);

        final ArgumentCaptor<ReplicateStatusUpdate> statusUpdate = ArgumentCaptor.forClass(ReplicateStatusUpdate.class);
        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(eq(CHAIN_TASK_ID), eq(WORKER_WALLET), statusUpdate.capture());
        assertThat(statusUpdate.getValue().getStatus()).isEqualTo(FAILED);
    }

    static Stream<ReplicateStatus> getCompletableStatuses() {
        return Arrays.stream(values())
                .filter(Predicate.not(ReplicateStatus.getUncompletableStatuses()::contains));
    }

    @ParameterizedTest
    @MethodSource("getCompletableStatuses")
    void shouldNotAddFailedStatusSinceCompletableReplicateStatus(ReplicateStatus completableStatus) {
        ReplicateUpdatedEvent replicateUpdatedEvent = getMockReplicate(completableStatus);
        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class));
    }

    @Test
    void shouldRemoveChainTaskIdFromWorkerSinceCompleted() {
        ReplicateUpdatedEvent replicateUpdatedEvent = getMockReplicate(COMPLETED);

        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);

        Mockito.verify(workerService, Mockito.times(1))
                .removeChainTaskIdFromWorker(CHAIN_TASK_ID, WORKER_WALLET);
    }

    @Test
    void shouldRemoveChainTaskIdFromWorkerSinceFailed() {
        ReplicateUpdatedEvent replicateUpdatedEvent = getMockReplicate(FAILED);

        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);

        Mockito.verify(workerService, Mockito.times(1))
                .removeChainTaskIdFromWorker(CHAIN_TASK_ID, WORKER_WALLET);
    }

    @Test
    void shouldNotRemoveChainTaskIdFromWorker() {
        List<ReplicateStatus> someStatuses = ReplicateStatus.getSuccessStatuses(); //not exhaustive
        someStatuses.remove(COMPLETED);
        someStatuses.remove(FAILED);

        for (ReplicateStatus randomStatus : someStatuses) {
            ReplicateUpdatedEvent replicateUpdatedEvent = getMockReplicate(randomStatus);

            replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);
        }

        Mockito.verify(workerService, Mockito.times(0))
                .removeChainTaskIdFromWorker(CHAIN_TASK_ID, WORKER_WALLET);
    }

    private ReplicateUpdatedEvent getMockReplicate(ReplicateStatus computed) {
        return ReplicateUpdatedEvent.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .walletAddress(WORKER_WALLET)
                .replicateStatusUpdate(new ReplicateStatusUpdate(computed))
                .build();
    }
}