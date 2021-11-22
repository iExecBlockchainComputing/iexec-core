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

package com.iexec.core.detector.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.utils.DateTimeUtils.addMinutesToDate;
import static org.mockito.Mockito.when;

public class ReplicateResultUploadTimeoutDetectorTests {

    private final static String WALLET_WORKER_1 = "0x748e091bf16048cb5103E0E10F9D5a8b7fBDd860";
    private final static String WALLET_WORKER_2 = "0x748e091bf16048cb5103E0E10F9D5a8b7fBDd861";
    private final static String CHAIN_TASK_ID = "CHAIN_TASK_ID";

    @Mock
    private TaskService taskService;
    
    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private TaskUpdateManager taskUpdateManager;

    @InjectMocks
    private ReplicateResultUploadTimeoutDetector timeoutDetector;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotDetectAnythingNoTimeout() {
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
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, ReplicateStatus.WORKER_LOST);
        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_2, ReplicateStatus.WORKER_LOST);
    }

    @Test
    public void shouldDetectOneReplicateWithResultUploadingLongAgo() {
        // the latest status change from the replicate is very new so it is not timed out.
        Date twoMinutesAgo = addMinutesToDate(new Date(), -3);
        Date threeMinutesAgo = addMinutesToDate(new Date(), -4);
        Date fourMinutesAgo = addMinutesToDate(new Date(), -5);

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        replicate1.updateStatus(ReplicateStatus.RESULT_UPLOADING, ReplicateStatusModifier.POOL_MANAGER);

        TaskStatusChange change1 = new TaskStatusChange(fourMinutesAgo, TaskStatus.INITIALIZED);
        TaskStatusChange change2 = new TaskStatusChange(threeMinutesAgo, TaskStatus.RUNNING);
        TaskStatusChange change3 = new TaskStatusChange(twoMinutesAgo, TaskStatus.RESULT_UPLOADING);

        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);
        task.setDateStatusList(Arrays.asList(change1, change2, change3));
        task.setCurrentStatus(TaskStatus.RESULT_UPLOADING);

        when(taskService.findByCurrentStatus(TaskStatus.RESULT_UPLOADING))
                .thenReturn(Collections.singletonList(task));
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(replicate1));

        // trying to detect any timeout
        timeoutDetector.detect();
        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, RESULT_UPLOAD_FAILED);

        Mockito.verify(taskUpdateManager, Mockito.times(1)).publishUpdateTaskRequest(CHAIN_TASK_ID);
    }

    @Test
    public void shouldNotDetectReplicatePreviouslyDetected() {
        // the latest status change from the replicate is very new so it is not timed out.
        Date twoMinutesAgo = addMinutesToDate(new Date(), -3);
        Date threeMinutesAgo = addMinutesToDate(new Date(), -4);
        Date fourMinutesAgo = addMinutesToDate(new Date(), -5);

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.STARTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);

        // we suppose that the status has already been set in a previous detect
        replicate.updateStatus(ReplicateStatus.RESULT_UPLOAD_REQUEST_FAILED, ReplicateStatusModifier.POOL_MANAGER);

        TaskStatusChange change1 = new TaskStatusChange(fourMinutesAgo, TaskStatus.INITIALIZED);
        TaskStatusChange change2 = new TaskStatusChange(threeMinutesAgo, TaskStatus.RUNNING);
        TaskStatusChange change3 = new TaskStatusChange(twoMinutesAgo, TaskStatus.RESULT_UPLOADING);

        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);
        task.setDateStatusList(Arrays.asList(change1, change2, change3));

        when(taskService.findByCurrentStatus(TaskStatus.RESULT_UPLOADING))
                .thenReturn(Collections.singletonList(task));
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(replicate));

        // trying to detect any timeout
        timeoutDetector.detect();
        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, RESULT_UPLOAD_REQUEST_FAILED);
    }
}
