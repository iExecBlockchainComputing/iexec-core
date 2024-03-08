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
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.TaskStatusChange;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatus.RESULT_UPLOAD_FAILED;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

class ReplicateResultUploadTimeoutDetectorTests {

    private final static String WALLET_WORKER_1 = "0x748e091bf16048cb5103E0E10F9D5a8b7fBDd860";
    private final static String WALLET_WORKER_2 = "0x748e091bf16048cb5103E0E10F9D5a8b7fBDd861";
    private final static String CHAIN_TASK_ID = "CHAIN_TASK_ID";

    @Mock
    private TaskService taskService;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private TaskUpdateRequestManager taskUpdateRequestManager;

    @InjectMocks
    private ReplicateResultUploadTimeoutDetector timeoutDetector;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldNotDetectAnythingNoTimeout() {
        // the latest status change from the replicate is very new so it is not timed out.

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.WORKER);
        replicate2.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);

        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);
        task.changeStatus(TaskStatus.RESULT_UPLOADING);

        when(taskService.findByCurrentStatus(TaskStatus.RESULT_UPLOADING)).thenReturn(Collections.singletonList(task));
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(replicate1));


        // trying to detect any timeout
        timeoutDetector.detect();
        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class));
    }

    @Test
    void shouldDetectOneReplicateWithResultUploadingLongAgo() {
        // the latest status change from the replicate is very new so it is not timed out.
        Date twoMinutesAgo = Date.from(Instant.now().minus(3L, ChronoUnit.MINUTES));
        Date threeMinutesAgo = Date.from(Instant.now().minus(4L, ChronoUnit.MINUTES));
        Date fourMinutesAgo = Date.from(Instant.now().minus(5L, ChronoUnit.MINUTES));

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.RESULT_UPLOADING, ReplicateStatusModifier.POOL_MANAGER);

        TaskStatusChange change1 = TaskStatusChange.builder().date(fourMinutesAgo).status(TaskStatus.INITIALIZED).build();
        TaskStatusChange change2 = TaskStatusChange.builder().date(threeMinutesAgo).status(TaskStatus.RUNNING).build();
        TaskStatusChange change3 = TaskStatusChange.builder().date(twoMinutesAgo).status(TaskStatus.RESULT_UPLOADING).build();

        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);
        task.setDateStatusList(Arrays.asList(change1, change2, change3));
        task.setCurrentStatus(TaskStatus.RESULT_UPLOADING);

        when(taskService.findByCurrentStatus(TaskStatus.RESULT_UPLOADING))
                .thenReturn(Collections.singletonList(task));
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(replicate1));

        // trying to detect any timeout
        timeoutDetector.detect();
        ArgumentCaptor<ReplicateStatusUpdate> statusUpdate = ArgumentCaptor.forClass(ReplicateStatusUpdate.class);
        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(eq(CHAIN_TASK_ID), eq(WALLET_WORKER_1), statusUpdate.capture());
        assertThat(statusUpdate.getValue().getStatus()).isEqualTo(RESULT_UPLOAD_FAILED);

        Mockito.verify(taskUpdateRequestManager, Mockito.times(1)).publishRequest(CHAIN_TASK_ID);
    }

    @Test
    void shouldNotDetectReplicatePreviouslyDetected() {
        // the latest status change from the replicate is very new so it is not timed out.
        Date twoMinutesAgo = Date.from(Instant.now().minus(3L, ChronoUnit.MINUTES));
        Date threeMinutesAgo = Date.from(Instant.now().minus(4L, ChronoUnit.MINUTES));
        Date fourMinutesAgo = Date.from(Instant.now().minus(5L, ChronoUnit.MINUTES));

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);

        // we suppose that the status has already been set in a previous detect
        replicate.updateStatus(RESULT_UPLOAD_FAILED, ReplicateStatusModifier.POOL_MANAGER);

        TaskStatusChange change1 = TaskStatusChange.builder().date(fourMinutesAgo).status(TaskStatus.INITIALIZED).build();
        TaskStatusChange change2 = TaskStatusChange.builder().date(threeMinutesAgo).status(TaskStatus.RUNNING).build();
        TaskStatusChange change3 = TaskStatusChange.builder().date(twoMinutesAgo).status(TaskStatus.RESULT_UPLOADING).build();

        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);
        task.setDateStatusList(Arrays.asList(change1, change2, change3));

        when(taskService.findByCurrentStatus(TaskStatus.RESULT_UPLOADING))
                .thenReturn(Collections.singletonList(task));
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(replicate));

        // trying to detect any timeout
        timeoutDetector.detect();
        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class));
    }
}
