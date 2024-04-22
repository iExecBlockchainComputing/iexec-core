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

package com.iexec.core.task.listener;

import com.iexec.common.lifecycle.purge.PurgeService;
import com.iexec.commons.poco.task.TaskAbortCause;
import com.iexec.core.notification.TaskNotification;
import com.iexec.core.notification.TaskNotificationType;
import com.iexec.core.pubsub.NotificationService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.event.*;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import com.iexec.core.worker.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class TaskListenerTest {

    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final String WALLET1 = "wallet1";
    private static final String WALLET2 = "wallet2";

    @Captor
    private ArgumentCaptor<TaskNotification> notificationCaptor;
    @Mock
    private TaskUpdateRequestManager taskUpdateRequestManager;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ReplicatesService replicatesService;
    @Mock
    private WorkerService workerService;
    @Mock
    private PurgeService purgeService;

    @InjectMocks
    private TaskListeners taskListeners;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldUpdateTaskOnTasCreatedEvent() {
        TaskCreatedEvent event = new TaskCreatedEvent(CHAIN_TASK_ID);
        taskListeners.onTaskCreatedEvent(event);
        verify(taskUpdateRequestManager).publishRequest(anyString());
    }

    @Test
    void shouldProcessContributionTimeoutEvent() {
        ContributionTimeoutEvent event = new ContributionTimeoutEvent(CHAIN_TASK_ID);
        Replicate replicate1 = new Replicate(WALLET1, CHAIN_TASK_ID);
        Replicate replicate2 = new Replicate(WALLET2, CHAIN_TASK_ID);
        List<Replicate> replicates = List.of(replicate1, replicate2);
        when(replicatesService.getReplicates(event.getChainTaskId()))
                .thenReturn(replicates);

        taskListeners.onTaskContributionTimeout(event);
        // Should remove taskId from workers
        verify(workerService).removeChainTaskIdFromWorker(CHAIN_TASK_ID, WALLET1);
        verify(workerService).removeChainTaskIdFromWorker(CHAIN_TASK_ID, WALLET2);
        // Should send abort notification with cause CONTRIBUTION_TIMEOUT
        verify(notificationService).sendTaskNotification(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getTaskNotificationType())
                .isEqualTo(TaskNotificationType.PLEASE_ABORT);
        assertThat(notificationCaptor.getValue().getTaskAbortCause())
                .isEqualTo(TaskAbortCause.CONTRIBUTION_TIMEOUT);
    }

    @Test
    void shouldNotifyWinnersAndLosersOnTaskConsensusReached() {
        String winningHash = "hash";
        String badHash = "bad";
        ConsensusReachedEvent event = ConsensusReachedEvent.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .consensus(winningHash)
                .blockNumber(0L)
                .build();
        Replicate replicate1 = new Replicate(WALLET1, CHAIN_TASK_ID);
        replicate1.setContributionHash(winningHash);
        Replicate replicate2 = new Replicate(WALLET2, CHAIN_TASK_ID);
        replicate2.setContributionHash(badHash);
        List<Replicate> replicates = List.of(replicate1, replicate2);
        when(replicatesService.getReplicates(event.getChainTaskId()))
                .thenReturn(replicates);

        taskListeners.onTaskConsensusReached(event);
        // Should send 2 notifications
        verify(notificationService, times(2)).sendTaskNotification(notificationCaptor.capture());
        TaskNotification capturedRevealNotification = notificationCaptor.getAllValues().get(0);
        TaskNotification capturedAbortNotification = notificationCaptor.getAllValues().get(1);
        // Should ask winners to reveal
        assertThat(capturedRevealNotification.getTaskNotificationType())
                .isEqualTo(TaskNotificationType.PLEASE_REVEAL);
        // Should ask losers to abort
        assertThat(capturedAbortNotification.getTaskNotificationType())
                .isEqualTo(TaskNotificationType.PLEASE_ABORT);
        assertThat(notificationCaptor.getValue().getTaskAbortCause())
                .isEqualTo(TaskAbortCause.CONSENSUS_REACHED);
    }

    @Test
    void shouldSendTaskNotificationOnPleaseUploadEvent() {
        PleaseUploadEvent event = new PleaseUploadEvent(CHAIN_TASK_ID, WALLET1);
        taskListeners.onPleaseUploadEvent(event);
        verify(notificationService).sendTaskNotification(any());
        // TODO capture args
    }

    @Test
    void onResultUploadTimeoutEvent(CapturedOutput output) {
        taskListeners.onResultUploadTimeoutEvent(new ResultUploadTimeoutEvent(CHAIN_TASK_ID));
        assertThat(output.getOut())
                .contains("Received ResultUploadTimeoutEvent [chainTaskId:" + CHAIN_TASK_ID + "]");
    }

    /**
     * should:
     * Remove task executor
     * Send notification
     * remove chainTaskId from worker
     */
    @Test
    void onTaskCompletedEvent() {
        Replicate replicate = new Replicate(WALLET1, CHAIN_TASK_ID);
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        TaskCompletedEvent event = new TaskCompletedEvent(task);
        when(replicatesService.getReplicates(CHAIN_TASK_ID))
                .thenReturn(List.of(replicate));

        taskListeners.onTaskCompletedEvent(event);
        verify(notificationService).sendTaskNotification(any());
        verify(workerService).removeChainTaskIdFromWorker(CHAIN_TASK_ID, WALLET1);
        verify(purgeService).purgeAllServices(CHAIN_TASK_ID);
        // TODO capture args
    }

    @Test
    void onTaskFailedEvent() {
        when(replicatesService.getReplicates(CHAIN_TASK_ID))
                .thenReturn(List.of(new Replicate(WALLET1, CHAIN_TASK_ID)));

        taskListeners.onTaskFailedEvent(new TaskFailedEvent(CHAIN_TASK_ID));
        verify(notificationService).sendTaskNotification(
                TaskNotification.builder()
                        .chainTaskId(CHAIN_TASK_ID)
                        .taskNotificationType(TaskNotificationType.PLEASE_ABORT)
                        .workersAddress(Collections.emptyList())
                        .build()
        );
        verify(workerService).removeChainTaskIdFromWorker(CHAIN_TASK_ID, WALLET1);
        verify(purgeService).purgeAllServices(CHAIN_TASK_ID);
    }

    @Test
    void onTaskRunningFailedEvent() {
        when(replicatesService.getReplicates(CHAIN_TASK_ID))
                .thenReturn(List.of(new Replicate(WALLET1, CHAIN_TASK_ID)));

        taskListeners.onTaskRunningFailedEvent(new TaskRunningFailedEvent(CHAIN_TASK_ID));
        verify(notificationService).sendTaskNotification(
                TaskNotification.builder()
                        .chainTaskId(CHAIN_TASK_ID)
                        .taskNotificationType(TaskNotificationType.PLEASE_ABORT)
                        .workersAddress(Collections.emptyList())
                        .build()
        );
        verify(workerService).removeChainTaskIdFromWorker(CHAIN_TASK_ID, WALLET1);
        verify(purgeService).purgeAllServices(CHAIN_TASK_ID);
    }
}
