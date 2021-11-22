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

package com.iexec.core.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.core.detector.replicate.ContributionUnnotifiedDetector;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskUpdateManager;
import com.iexec.core.task.listener.ReplicateListeners;
import com.iexec.core.worker.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusCause.TASK_NOT_ACTIVE;
import static org.mockito.ArgumentMatchers.any;

public class ReplicateListenersTests {

    private final static String CHAIN_TASK_ID = "chainTaskId";
    private final static String WORKER_WALLET = "0xwallet1";

    @Mock
    private TaskService taskService;
    @Mock
    private WorkerService workerService;
    @Mock
    private ContributionUnnotifiedDetector contributionUnnotifiedDetector;
    @Mock
    private ReplicatesService replicatesService;
    @Mock
    private TaskUpdateManager taskUpdateManager;

    @InjectMocks
    private ReplicateListeners replicateListeners;


    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldUpdateTaskOnReplicateUpdate() {
        List<ReplicateStatus> someStatuses = ReplicateStatus.getSuccessStatuses(); //not exhaustive

        for (ReplicateStatus randomStatus: someStatuses){
            ReplicateUpdatedEvent replicateUpdatedEvent = getMockReplicate(randomStatus);

            replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);
        }

        Mockito.verify(taskUpdateManager, Mockito.times(someStatuses.size())).publishUpdateTaskRequest(any());
    }

    @Test
    public void shouldRemoveFromComputedTasksSinceStartFailed() {
        assertIsRemovedFromComputedTasks(START_FAILED);
    }

    @Test
    public void shouldRemoveFromComputedTasksSinceAppDownloadFailed() {
        assertIsRemovedFromComputedTasks(APP_DOWNLOAD_FAILED);
    }

    @Test
    public void shouldRemoveFromComputedTasksSinceDataDownloadFailed() {
        assertIsRemovedFromComputedTasks(DATA_DOWNLOAD_FAILED);
    }

    @Test
    public void shouldRemoveFromComputedTasksSinceComputedFailed() {
        assertIsRemovedFromComputedTasks(COMPUTED);
    }

    @Test
    public void shouldRemoveFromComputedTasksSinceComputeFailed() {
        assertIsRemovedFromComputedTasks(COMPUTE_FAILED);
    }

    private void assertIsRemovedFromComputedTasks(ReplicateStatus computed) {
        ReplicateUpdatedEvent replicateUpdatedEvent = getMockReplicate(computed);
        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);
        Mockito.verify(workerService, Mockito.times(1))
                .removeComputedChainTaskIdFromWorker(CHAIN_TASK_ID, WORKER_WALLET);
    }

    @Test
    public void shouldTriggerDetectOnchainContributedSinceTaskNotActive() {
        ReplicateUpdatedEvent replicateUpdatedEvent = ReplicateUpdatedEvent.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .walletAddress(WORKER_WALLET)
                .replicateStatusUpdate(new ReplicateStatusUpdate(CONTRIBUTING, TASK_NOT_ACTIVE))
                .build();

        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);

        Mockito.verify(contributionUnnotifiedDetector, Mockito.times(1)).detectOnchainContributed();
    }

    @Test
    public void shouldNotTriggerDetectOnchain() {
        List<ReplicateStatus> someStatuses = ReplicateStatus.getSuccessStatuses(); //not exhaustive
        someStatuses.remove(CONTRIBUTING);

        for (ReplicateStatus randomStatus: someStatuses){
            ReplicateUpdatedEvent replicateUpdatedEvent = getMockReplicate(randomStatus);

            replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);
        }

        Mockito.verify(contributionUnnotifiedDetector, Mockito.times(0)).detectOnchainContributed();
    }

    @Test
    public void shouldNotTriggerDetectOnchainContributedSinceCauseIsNull() {
        ReplicateUpdatedEvent replicateUpdatedEvent = ReplicateUpdatedEvent.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .walletAddress(WORKER_WALLET)
                .replicateStatusUpdate(new ReplicateStatusUpdate(CONTRIBUTING))
                .build();

        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);

        Mockito.verify(contributionUnnotifiedDetector, Mockito.times(0)).detectOnchainContributed();
    }

    @Test
    public void shouldAddFailedStatusSinceUncompletableReplicateStatus() {
        List<ReplicateStatus> uncompletableStatuses = ReplicateStatus.getUncompletableStatuses();

        for (ReplicateStatus uncompletableStatus: uncompletableStatuses){
            ReplicateUpdatedEvent replicateUpdatedEvent = getMockReplicate(uncompletableStatus);

            replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);
        }

        Mockito.verify(replicatesService, Mockito.times(uncompletableStatuses.size()))
                .updateReplicateStatus(CHAIN_TASK_ID, WORKER_WALLET, FAILED);
    }

    @Test
    public void shouldNotAddFailedStatusSinceCompletableReplicateStatus() {
        List<ReplicateStatus> completableStatuses = ReplicateStatus.getCompletableStatuses();

        for (ReplicateStatus completableStatus: completableStatuses){
            ReplicateUpdatedEvent replicateUpdatedEvent = getMockReplicate(completableStatus);

            replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);
        }

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(CHAIN_TASK_ID, WORKER_WALLET, FAILED);
    }

    @Test
    public void shouldRemoveChainTaskIdFromWorkerSinceCompleted() {
        ReplicateUpdatedEvent replicateUpdatedEvent = getMockReplicate(COMPLETED);

        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);

        Mockito.verify(workerService, Mockito.times(1))
                .removeChainTaskIdFromWorker(CHAIN_TASK_ID, WORKER_WALLET);
    }

    @Test
    public void shouldRemoveChainTaskIdFromWorkerSinceFailed() {
        ReplicateUpdatedEvent replicateUpdatedEvent = getMockReplicate(FAILED);

        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);

        Mockito.verify(workerService, Mockito.times(1))
            .removeChainTaskIdFromWorker(CHAIN_TASK_ID, WORKER_WALLET);
    }

    @Test
    public void shouldNotRemoveChainTaskIdFromWorker() {
        List<ReplicateStatus> someStatuses = ReplicateStatus.getSuccessStatuses(); //not exhaustive
        someStatuses.remove(COMPLETED);
        someStatuses.remove(FAILED);

        for (ReplicateStatus randomStatus: someStatuses){
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