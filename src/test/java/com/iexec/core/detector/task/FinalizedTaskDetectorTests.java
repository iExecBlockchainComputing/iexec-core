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

package com.iexec.core.detector.task;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.commons.poco.chain.ChainTask;
import com.iexec.commons.poco.chain.ChainTaskStatus;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.tee.TeeUtils;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static com.iexec.core.TestUtils.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinalizedTaskDetectorTests {
    @Mock
    private TaskService taskService;
    @Mock
    private TaskUpdateRequestManager taskUpdateRequestManager;
    @Mock
    private IexecHubService iexecHubService;
    @Mock
    private ReplicatesService replicatesService;

    @Spy
    @InjectMocks
    private FinalizedTaskDetector detector;

    // region detect
    @Test
    void shouldDetectTasks() {
        final String completedChainTaskId = "0x65bc5e94ed1486b940bd6cc0013c418efad58a0a52a3d08cee89faaa21970426";
        final Task completedTask = mockOnchainTask(completedChainTaskId, ChainTaskStatus.COMPLETED).build();
        when(taskService.findByCurrentStatus(TaskStatus.FINALIZING)).thenReturn(List.of(completedTask));

        final String contributedAndFinalizedChainTaskId = "0x75bc5e94ed1486b940bd6cc0013c418efad58a0a52a3d08cee89faaa21970426";
        final Task contributeAndFinalizeTask = getContributeAndFinalizeDoneTask(
                contributedAndFinalizedChainTaskId, ChainTaskStatus.COMPLETED, ReplicateStatus.CONTRIBUTE_AND_FINALIZE_DONE).build();
        when(taskService.findByCurrentStatus(TaskStatus.RUNNING)).thenReturn(List.of(contributeAndFinalizeTask));
        mockTaskDescriptionFromTask(contributeAndFinalizeTask);

        detector.detect();

        verify(taskUpdateRequestManager).publishRequest(completedChainTaskId);
        verify(taskUpdateRequestManager).publishRequest(contributedAndFinalizedChainTaskId);
    }
    // endregion

    // region detectFinalizedTasks
    @Test
    void shouldDetectFinalizedTask() {
        final Task task = mockOnchainTask(CHAIN_TASK_ID, ChainTaskStatus.COMPLETED).build();

        when(taskService.findByCurrentStatus(TaskStatus.FINALIZING)).thenReturn(List.of(task));

        detector.detectFinalizedTasks();

        verify(taskUpdateRequestManager).publishRequest(CHAIN_TASK_ID);
    }

    @Test
    void shouldNotDetectFinalizedTaskAsTaskIsRevealing() {
        final Task task = mockOnchainTask(CHAIN_TASK_ID, ChainTaskStatus.REVEALING).build();

        when(taskService.findByCurrentStatus(TaskStatus.FINALIZING)).thenReturn(List.of(task));

        detector.detectFinalizedTasks();

        verifyNoInteractions(taskUpdateRequestManager);
    }
    // endregion

    // region detectContributeAndFinalizeDoneTasks
    @Test
    void shouldDetectContributeAndFinalizeDoneTask() {
        final Task task = getContributeAndFinalizeDoneTask(
                CHAIN_TASK_ID, ChainTaskStatus.COMPLETED, ReplicateStatus.CONTRIBUTE_AND_FINALIZE_DONE).build();

        when(taskService.findByCurrentStatus(TaskStatus.RUNNING)).thenReturn(List.of(task));
        mockTaskDescriptionFromTask(task);

        detector.detectContributeAndFinalizeDoneTasks();

        verify(taskUpdateRequestManager).publishRequest(CHAIN_TASK_ID);
    }

    @Test
    void shouldNotDetectContributeAndFinalizeDoneTaskAsTaskIsActive() {
        final Task task = getContributeAndFinalizeDoneTask(
                CHAIN_TASK_ID, ChainTaskStatus.ACTIVE, ReplicateStatus.COMPUTING).build();

        when(taskService.findByCurrentStatus(TaskStatus.RUNNING)).thenReturn(List.of(task));
        mockTaskDescriptionFromTask(task);

        detector.detectContributeAndFinalizeDoneTasks();

        verifyNoInteractions(taskUpdateRequestManager);
    }
    // endregion

    // region isChainTaskCompleted
    @Test
    void shouldChainTaskBeCompleted() {
        final Task task = mockOnchainTask(CHAIN_TASK_ID, ChainTaskStatus.COMPLETED).build();

        final boolean chainTaskCompleted = detector.isChainTaskCompleted(task);

        Assertions.assertThat(chainTaskCompleted).isTrue();
    }

    @Test
    void shouldChainTaskNotBeCompletedAsChainTaskAbsent() {
        final Task task = Task.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .currentStatus(TaskStatus.FINALIZING)
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        final boolean chainTaskCompleted = detector.isChainTaskCompleted(task);

        Assertions.assertThat(chainTaskCompleted).isFalse();
    }

    @Test
    void shouldChainTaskNotBeCompletedAsChainTaskNotCompleted() {
        final Task task = mockOnchainTask(CHAIN_TASK_ID, ChainTaskStatus.REVEALING).build();

        final boolean chainTaskCompleted = detector.isChainTaskCompleted(task);

        Assertions.assertThat(chainTaskCompleted).isFalse();
    }
    // endregion

    // region isTaskContributeAndFinalizeDone
    @Test
    void shouldTaskBeContributeAndFinalizeDone() {
        final Task task = getContributeAndFinalizeDoneTask(
                CHAIN_TASK_ID, ChainTaskStatus.COMPLETED, ReplicateStatus.CONTRIBUTE_AND_FINALIZE_DONE).build();
        mockTaskDescriptionFromTask(task);

        final boolean taskContributeAndFinalizeDone = detector.isTaskContributeAndFinalizeDone(task);

        Assertions.assertThat(taskContributeAndFinalizeDone).isTrue();
    }

    @Test
    void shouldTaskNotBeContributeAndFinalizeDoneAsNotTee() {
        final Task task = Task.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .currentStatus(TaskStatus.FINALIZING)
                .tag(NO_TEE_TAG)
                .build();
        mockTaskDescriptionFromTask(task);

        final boolean taskContributeAndFinalizeDone = detector.isTaskContributeAndFinalizeDone(task);

        Assertions.assertThat(taskContributeAndFinalizeDone).isFalse();
    }

    @Test
    void shouldTaskNotBeContributeAndFinalizeDoneAsMultipleReplicates() {
        final Task task = Task.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .currentStatus(TaskStatus.FINALIZING)
                .trust(1)
                .tag(TEE_TAG)
                .build();
        final Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        final Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(List.of(replicate1, replicate2));
        mockTaskDescriptionFromTask(task);

        final boolean taskContributeAndFinalizeDone = detector.isTaskContributeAndFinalizeDone(task);

        Assertions.assertThat(taskContributeAndFinalizeDone).isFalse();
    }

    @Test
    void shouldTaskNotBeContributeAndFinalizeDoneAsReplicateNotDone() {
        final Task task = Task.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .currentStatus(TaskStatus.FINALIZING)
                .trust(1)
                .tag(TEE_TAG)
                .build();
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.COMPUTING, ReplicateStatusModifier.WORKER);

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(List.of(replicate));
        mockTaskDescriptionFromTask(task);

        final boolean taskContributeAndFinalizeDone = detector.isTaskContributeAndFinalizeDone(task);

        Assertions.assertThat(taskContributeAndFinalizeDone).isFalse();
    }

    @Test
    void shouldTaskNotBeContributeAndFinalizeDoneAsChainTaskNotCompleted() {
        final Task task = mockOnchainTask(CHAIN_TASK_ID, ChainTaskStatus.REVEALING)
                .trust(1)
                .tag(TEE_TAG)
                .build();
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTE_AND_FINALIZE_DONE, ReplicateStatusModifier.WORKER);

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(List.of(replicate));
        mockTaskDescriptionFromTask(task);

        final boolean taskContributeAndFinalizeDone = detector.isTaskContributeAndFinalizeDone(task);

        Assertions.assertThat(taskContributeAndFinalizeDone).isFalse();
    }
    // endregion

    // region publishTaskUpdateRequest
    @Test
    void shouldPublishTaskUpdateRequest() {
        final Task task = Task.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .currentStatus(TaskStatus.FINALIZING)
                .build();

        detector.publishTaskUpdateRequest(task);

        verify(taskUpdateRequestManager).publishRequest(CHAIN_TASK_ID);
    }
    // endregion

    // region Utils
    private Task.TaskBuilder getContributeAndFinalizeDoneTask(final String chainTaskId,
                                                              final ChainTaskStatus chainTaskStatus,
                                                              final ReplicateStatus replicateStatus) {
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(replicateStatus, ReplicateStatusModifier.WORKER);

        when(replicatesService.getReplicates(chainTaskId)).thenReturn(List.of(replicate));

        // iexecHubService.getChainTask will only be called if task has single replicate with CONTRIBUTE_AND_FINALIZE_DONE status
        final Task.TaskBuilder taskBuilder =
                replicateStatus == ReplicateStatus.CONTRIBUTE_AND_FINALIZE_DONE ? mockOnchainTask(chainTaskId, chainTaskStatus) : Task.builder().chainTaskId(chainTaskId);

        return taskBuilder
                .currentStatus(TaskStatus.RUNNING)
                .trust(1)
                .tag(TEE_TAG);
    }

    private Task.TaskBuilder mockOnchainTask(final String chainTaskId, final ChainTaskStatus chainTaskStatus) {
        final ChainTask chainTask = ChainTask.builder()
                .chainTaskId(chainTaskId)
                .status(chainTaskStatus)
                .build();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));

        return Task.builder()
                .chainTaskId(chainTaskId)
                .currentStatus(TaskStatus.FINALIZING);
    }

    private void mockTaskDescriptionFromTask(final Task task) {
        final TaskDescription taskDescription = TaskDescription.builder()
                .chainTaskId(task.getChainTaskId())
                .isTeeTask(task.isTeeTask())
                .teeFramework(TeeUtils.getTeeFramework(task.getTag()))
                .trust(BigInteger.valueOf(task.getTrust()))
                .callback("")
                .build();
        when(iexecHubService.getTaskDescription(task.getChainTaskId())).thenReturn(taskDescription);
    }
    // endregion
}
