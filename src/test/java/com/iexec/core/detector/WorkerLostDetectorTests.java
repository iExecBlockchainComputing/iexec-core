/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static com.iexec.common.utils.DateTimeUtils.addMinutesToDate;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class WorkerLostDetectorTests {

    private final static String WALLET_WORKER = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private final static String CHAIN_TASK_ID = "chainTaskId";

    @Mock
    private WorkerService workerService;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private TaskService taskService;

    @InjectMocks
    private WorkerLostDetector workerLostDetector;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotDetectAnyWorkerLost(){
        when(workerService.getLostWorkers()).thenReturn(Collections.emptyList());
        workerLostDetector.detect();
        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusUpdate.class));

    }

    @Test
    public void shouldUpdateOneReplicateToWorkerLost(){
        Date twoMinutesAgo = addMinutesToDate(new Date(), -2);

        Worker worker = Worker.builder()
                .walletAddress(WALLET_WORKER)
                .lastAliveDate(twoMinutesAgo)
                .participatingChainTaskIds(Collections.singletonList(CHAIN_TASK_ID))
                .build();


        Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.WORKER);

        when(workerService.getLostWorkers()).thenReturn(Collections.singletonList(worker));
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER)).thenReturn(Optional.of(replicate));
        when(taskService.isExpired(anyString())).thenReturn(false);

        workerLostDetector.detect();
        // verify that the call on the update is correct
        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER, ReplicateStatus.WORKER_LOST);
    }

    // similar test with previous except that the Replicate is already is WORKER_LOST status.
    @Test
    public void shouldNotUpdateToWorkerLostSinceAlreadyUpdated(){
        Worker worker = Worker.builder()
                .walletAddress(WALLET_WORKER)
                .participatingChainTaskIds(Collections.singletonList(CHAIN_TASK_ID))
                .build();

        Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.WORKER_LOST, ReplicateStatusModifier.POOL_MANAGER);

        when(workerService.getLostWorkers()).thenReturn(Collections.singletonList(worker));
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER)).thenReturn(Optional.of(replicate));
        when(taskService.isExpired(anyString())).thenReturn(false);

        workerLostDetector.detect();
        Mockito.verify(replicatesService, never())
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER, ReplicateStatus.WORKER_LOST);
    }

    @Test
    public void shouldNotUpdateToWorkerLostSinceFailed(){
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
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER, ReplicateStatus.WORKER_LOST);
    }

    @Test
    public void shouldNotUpdateToWorkerLostSinceCompleted(){
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
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER, ReplicateStatus.WORKER_LOST);
    }

    @Test
    public void shouldNotUpdateOneReplicateToWorkerLostTaskIsExpired(){
        Date twoMinutesAgo = addMinutesToDate(new Date(), -2);

        Worker worker = Worker.builder()
                .walletAddress(WALLET_WORKER)
                .lastAliveDate(twoMinutesAgo)
                .participatingChainTaskIds(Collections.singletonList(CHAIN_TASK_ID))
                .build();

        Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.WORKER_LOST, ReplicateStatusModifier.POOL_MANAGER);

        when(workerService.getLostWorkers()).thenReturn(Collections.singletonList(worker));
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER)).thenReturn(Optional.of(replicate));
        when(taskService.isExpired(anyString())).thenReturn(true);

        workerLostDetector.detect();
        // verify that the call on the update is correct
        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER, ReplicateStatus.WORKER_LOST);

        // verify that the worker should remove the taskId from its current tasks
        Mockito.verify(workerService, Mockito.times(0))
                .removeChainTaskIdFromWorker(CHAIN_TASK_ID, WALLET_WORKER);
    }
}
