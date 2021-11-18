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

package com.iexec.core.task.listener;

import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.task.TaskAbortCause;
import com.iexec.core.pubsub.NotificationService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskUpdateManager;
import com.iexec.core.task.event.*;
import com.iexec.core.worker.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class TaskListenerTest {

    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final String WALLET1 = "wallet1";
    private static final String WALLET2 = "wallet2";

    @Captor
    private ArgumentCaptor<TaskNotification> notificationCaptor;
    @Mock
    private TaskUpdateManager taskUpdateManager;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ReplicatesService replicatesService;
    @Mock
    private WorkerService workerService;

    @InjectMocks
    private TaskListeners taskListeners;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldUpdateTaskOnTasCreatedEvent() {
        TaskCreatedEvent event = new TaskCreatedEvent();
        event.setChainTaskId(CHAIN_TASK_ID);
        taskListeners.onTaskCreatedEvent(event);
        verify(taskUpdateManager).publishUpdateTaskRequest(anyString());
    }

    @Test
    public void shouldProcessContributionTimeoutEvent() {
        ContributionTimeoutEvent event = new ContributionTimeoutEvent();
        event.setChainTaskId(CHAIN_TASK_ID);
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
    public void shouldNotifyWinnersAndLosersOnTaskConsensusReached() {
        String winningHash = "hash";
        String badHash = "bad";
        ConsensusReachedEvent event = new ConsensusReachedEvent();
        event.setChainTaskId(CHAIN_TASK_ID);
        event.setConsensus(winningHash);
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
    public void shouldSendTaskNotificationOnPleaseUploadEvent() {
        PleaseUploadEvent event = new PleaseUploadEvent(CHAIN_TASK_ID, WALLET1);
        taskListeners.onPleaseUploadEvent(event);
        verify(notificationService).sendTaskNotification(any());
        // TODO capture args
    }

    @Test
    public void onResultUploadTimeoutEvent() {
        taskListeners.onResultUploadTimeoutEvent(new ResultUploadTimeoutEvent());
    }

    /**
     * should:
     * Remove task executor
     * Send notification
     * remove chainTaskId from worker
     */
    @Test
    public void onTaskCompletedEvent() {
        Replicate replicate = new Replicate(WALLET1, CHAIN_TASK_ID);
        Task task = Task.builder().chainTaskId(CHAIN_TASK_ID).build();
        TaskCompletedEvent event = new TaskCompletedEvent(task);
        when(replicatesService.getReplicates(CHAIN_TASK_ID))
                .thenReturn(List.of(replicate));

        taskListeners.onTaskCompletedEvent(event);
        verify(notificationService).sendTaskNotification(any());
        verify(workerService).removeChainTaskIdFromWorker(CHAIN_TASK_ID, WALLET1);
        // TODO capture args
    }

    @Test
    public void onTaskFailedEvent() {
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
    }

    @Test
    public void onTaskRunningFailedEvent() {
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
    }
}
