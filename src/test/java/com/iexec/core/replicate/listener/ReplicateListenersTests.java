/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.replicate.listener;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.core.detector.replicate.ContributionUnnotifiedDetector;
import com.iexec.core.replicate.ReplicateUpdatedEvent;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import com.iexec.core.worker.WorkerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusCause.TASK_NOT_ACTIVE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReplicateListenersTests {

    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final String WORKER_WALLET = "0xwallet1";

    @Mock
    private WorkerService workerService;
    @Mock
    private ContributionUnnotifiedDetector contributionUnnotifiedDetector;
    @Mock
    private TaskUpdateRequestManager taskUpdateRequestManager;
    @InjectMocks
    private ReplicateListeners replicateListeners;

    private final List<ReplicateStatus> statusesTriggeringTaskUpdate = List.of(
            STARTED, CONTRIBUTE_AND_FINALIZE_DONE, CONTRIBUTED, REVEALED, RESULT_UPLOADED,
            START_FAILED, APP_DOWNLOAD_FAILED, DATA_DOWNLOAD_FAILED, COMPUTE_FAILED);

    @Test
    void shouldUpdateTaskOnReplicateUpdate() {
        statusesTriggeringTaskUpdate.stream()
                .map(this::getMockReplicate)
                .forEach(replicateListeners::onReplicateUpdatedEvent);

        verify(taskUpdateRequestManager, times(statusesTriggeringTaskUpdate.size())).publishRequest(any());
    }

    @Test
    void shouldNotUpdateTaskOnReplicateUpdate() {
        final List<ReplicateStatus> nonTriggeringStatuses = Arrays.stream(ReplicateStatus.values())
                .filter(status -> !statusesTriggeringTaskUpdate.contains(status))
                .toList();

        nonTriggeringStatuses.stream()
                .map(this::getMockReplicate)
                .forEach(replicateListeners::onReplicateUpdatedEvent);

        verifyNoInteractions(taskUpdateRequestManager);
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

    private void assertIsRemovedFromComputedTasks(final ReplicateStatus computed) {
        final ReplicateUpdatedEvent replicateUpdatedEvent = getMockReplicate(computed);
        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);
        verify(workerService).removeComputedChainTaskIdFromWorker(CHAIN_TASK_ID, WORKER_WALLET);
    }

    @Test
    void shouldTriggerDetectOnchainContributedSinceTaskNotActive() {
        final ReplicateUpdatedEvent replicateUpdatedEvent = ReplicateUpdatedEvent.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .walletAddress(WORKER_WALLET)
                .replicateStatusUpdate(new ReplicateStatusUpdate(CONTRIBUTING, TASK_NOT_ACTIVE))
                .build();

        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);

        verify(contributionUnnotifiedDetector).detectOnchainDone();
    }

    @Test
    void shouldNotTriggerDetectOnchain() {
        ReplicateStatus.getSuccessStatuses().stream()
                .filter(status -> status != CONTRIBUTING)
                .map(this::getMockReplicate)
                .forEach(replicateListeners::onReplicateUpdatedEvent);

        verifyNoInteractions(contributionUnnotifiedDetector);
    }

    @Test
    void shouldNotTriggerDetectOnchainContributedSinceCauseIsNull() {
        final ReplicateUpdatedEvent replicateUpdatedEvent = ReplicateUpdatedEvent.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .walletAddress(WORKER_WALLET)
                .replicateStatusUpdate(new ReplicateStatusUpdate(CONTRIBUTING))
                .build();

        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);

        verifyNoInteractions(contributionUnnotifiedDetector);
    }

    @Test
    void shouldRemoveChainTaskIdFromWorkerSinceCompleted() {
        final ReplicateUpdatedEvent replicateUpdatedEvent = getMockReplicate(COMPLETED);

        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);

        verify(workerService).removeChainTaskIdFromWorker(CHAIN_TASK_ID, WORKER_WALLET);
    }

    @Test
    void shouldRemoveChainTaskIdFromWorkerSinceFailed() {
        final ReplicateUpdatedEvent replicateUpdatedEvent = getMockReplicate(FAILED);

        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);

        verify(workerService).removeChainTaskIdFromWorker(CHAIN_TASK_ID, WORKER_WALLET);
    }

    @Test
    void shouldNotRemoveChainTaskIdFromWorker() {
        ReplicateStatus.getSuccessStatuses().stream()
                .filter(status -> status != COMPLETED && status != FAILED)
                .map(this::getMockReplicate)
                .forEach(replicateListeners::onReplicateUpdatedEvent);

        verify(workerService).removeComputedChainTaskIdFromWorker(CHAIN_TASK_ID, WORKER_WALLET);
        verify(workerService, never()).removeChainTaskIdFromWorker(CHAIN_TASK_ID, WORKER_WALLET);
    }

    private ReplicateUpdatedEvent getMockReplicate(final ReplicateStatus computed) {
        return ReplicateUpdatedEvent.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .walletAddress(WORKER_WALLET)
                .replicateStatusUpdate(new ReplicateStatusUpdate(computed))
                .build();
    }
}