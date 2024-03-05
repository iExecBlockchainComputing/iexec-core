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

package com.iexec.core.detector;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.TaskService;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.Collections;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatus.WORKER_LOST;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

class WorkerLostDetectorTests {

    private static final String WALLET_WORKER = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private static final String CHAIN_TASK_ID = "chainTaskId";

    @Mock
    private WorkerService workerService;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private TaskService taskService;

    @InjectMocks
    private WorkerLostDetector workerLostDetector;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldNotDetectAnyWorkerLost() {
        when(workerService.getLostWorkers()).thenReturn(Collections.emptyList());
        workerLostDetector.detect();
        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusUpdate.class));
    }

    @Test
    void shouldUpdateOneReplicateToWorkerLost() {
        Worker worker = Worker.builder()
                .walletAddress(WALLET_WORKER)
                .participatingChainTaskIds(Collections.singletonList(CHAIN_TASK_ID))
                .build();

        Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.WORKER);

        when(workerService.getLostWorkers()).thenReturn(Collections.singletonList(worker));
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER)).thenReturn(Optional.of(replicate));
        when(taskService.isExpired(anyString())).thenReturn(false);

        workerLostDetector.detect();
        // verify that the call on the update is correct
        final ArgumentCaptor<ReplicateStatusUpdate> statusUpdate = ArgumentCaptor.forClass(ReplicateStatusUpdate.class);
        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(eq(CHAIN_TASK_ID), eq(WALLET_WORKER), statusUpdate.capture());
        assertThat(statusUpdate.getValue().getStatus()).isEqualTo(WORKER_LOST);
    }

    // similar test with previous except that the Replicate is already is WORKER_LOST status.
    @Test
    void shouldNotUpdateToWorkerLostSinceAlreadyUpdated() {
        Worker worker = Worker.builder()
                .walletAddress(WALLET_WORKER)
                .participatingChainTaskIds(Collections.singletonList(CHAIN_TASK_ID))
                .build();

        Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(WORKER_LOST, ReplicateStatusModifier.POOL_MANAGER);

        when(workerService.getLostWorkers()).thenReturn(Collections.singletonList(worker));
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER)).thenReturn(Optional.of(replicate));
        when(taskService.isExpired(anyString())).thenReturn(false);

        workerLostDetector.detect();
        Mockito.verify(replicatesService, never())
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class));
    }

    @Test
    void shouldNotUpdateToWorkerLostSinceFailed() {
        Worker worker = Worker.builder()
                .walletAddress(WALLET_WORKER)
                .participatingChainTaskIds(Collections.singletonList(CHAIN_TASK_ID))
                .build();

        Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.FAILED, ReplicateStatusModifier.POOL_MANAGER);

        when(workerService.getLostWorkers()).thenReturn(Collections.singletonList(worker));
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER)).thenReturn(Optional.of(replicate));
        when(taskService.isExpired(anyString())).thenReturn(false);

        workerLostDetector.detect();
        Mockito.verify(replicatesService, never())
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class));
    }

    @Test
    void shouldNotUpdateToWorkerLostSinceCompleted() {
        Worker worker = Worker.builder()
                .walletAddress(WALLET_WORKER)
                .participatingChainTaskIds(Collections.singletonList(CHAIN_TASK_ID))
                .build();

        Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.COMPLETED, ReplicateStatusModifier.POOL_MANAGER);

        when(workerService.getLostWorkers()).thenReturn(Collections.singletonList(worker));
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER)).thenReturn(Optional.of(replicate));
        when(taskService.isExpired(anyString())).thenReturn(false);

        workerLostDetector.detect();
        Mockito.verify(replicatesService, never())
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class));
    }

    @Test
    void shouldNotUpdateOneReplicateToWorkerLostTaskIsExpired() {
        Worker worker = Worker.builder()
                .walletAddress(WALLET_WORKER)
                .participatingChainTaskIds(Collections.singletonList(CHAIN_TASK_ID))
                .build();

        Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(WORKER_LOST, ReplicateStatusModifier.POOL_MANAGER);

        when(workerService.getLostWorkers()).thenReturn(Collections.singletonList(worker));
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER)).thenReturn(Optional.of(replicate));
        when(taskService.isExpired(anyString())).thenReturn(true);

        workerLostDetector.detect();
        // verify that the call on the update is correct
        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class));

        // verify that the worker should remove the taskId from its current tasks
        Mockito.verify(workerService, Mockito.times(0))
                .removeChainTaskIdFromWorker(CHAIN_TASK_ID, WALLET_WORKER);
    }
}
