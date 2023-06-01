package com.iexec.core.detector.task;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.commons.poco.chain.ChainTask;
import com.iexec.commons.poco.chain.ChainTaskStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.List;
import java.util.Optional;

import static com.iexec.core.task.TaskTestsUtils.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    // region detect
    @Test
    void shouldDetectTasks() {
//        final String completedChainTaskId = "0x65bc5e94ed1486b940bd6cc0013c418efad58a0a52a3d08cee89faaa21970426";
//        final Task completedTask = getOnchainCompletedTask(completedChainTaskId).build();
//        when(taskService.findByCurrentStatus(TaskStatus.FINALIZING)).thenReturn(List.of(completedTask));

        final String contributedAndFinalizedChainTaskId = "0x75bc5e94ed1486b940bd6cc0013c418efad58a0a52a3d08cee89faaa21970426";
        final Task contributeAndFinalizeTask = getContributeAndFinalizeDoneTask(contributedAndFinalizedChainTaskId).build();
        when(taskService.findByCurrentStatus(TaskStatus.RUNNING)).thenReturn(List.of(contributeAndFinalizeTask));

        detector.detect();

//        verify(taskUpdateRequestManager).publishRequest(completedChainTaskId);
        verify(taskUpdateRequestManager).publishRequest(contributedAndFinalizedChainTaskId);
    }
    // endregion

    // region detectFinalizedTasks
    @Test
    void shouldDetectFinalizedTask() {
        final Task task = getOnchainCompletedTask(CHAIN_TASK_ID).build();

        when(taskService.findByCurrentStatus(TaskStatus.FINALIZING)).thenReturn(List.of(task));

        detector.detectFinalizedTasks();

        verify(taskUpdateRequestManager).publishRequest(CHAIN_TASK_ID);
    }

    @Test
    void shouldDetectNoFinalizedTassAsTaskIsRevealing() {
        final Task task = getOnchainRevealingTask(CHAIN_TASK_ID).build();

        when(taskService.findByCurrentStatus(TaskStatus.FINALIZING)).thenReturn(List.of(task));

        detector.detectFinalizedTasks();

        verify(taskUpdateRequestManager, Mockito.never()).publishRequest(CHAIN_TASK_ID);
    }
    // endregion

    // region detectContributeAndFinalizeDoneTasks
    @Test
    void shouldDetectContributeAndFinalizeDoneTask() {
        final Task task = getContributeAndFinalizeDoneTask(CHAIN_TASK_ID).build();

        when(taskService.findByCurrentStatus(TaskStatus.RUNNING)).thenReturn(List.of(task));

        detector.detectContributeAndFinalizeDoneTasks();

        verify(taskUpdateRequestManager).publishRequest(CHAIN_TASK_ID);
    }

    @Test
    void shouldDetectNoContributeAndFinalizeDoneTaskAsTaskIsRevealing() {
        final Task task = getOnchainRevealingTask(CHAIN_DEAL_ID).build();

        when(taskService.findByCurrentStatus(TaskStatus.RUNNING)).thenReturn(List.of(task));

        detector.detectContributeAndFinalizeDoneTasks();

        verify(taskUpdateRequestManager, Mockito.never()).publishRequest(CHAIN_TASK_ID);
    }
    // endregion

    // region isChainTaskCompleted
    @Test
    void shouldChainTaskBeCompleted() {
        final Task task = getOnchainCompletedTask(CHAIN_TASK_ID).build();

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
        final Task task = getOnchainRevealingTask(CHAIN_DEAL_ID).build();

        final boolean chainTaskCompleted = detector.isChainTaskCompleted(task);

        Assertions.assertThat(chainTaskCompleted).isFalse();
    }
    // endregion

    // region isTaskContributeAndFinalizeDone
    @Test
    void shouldTaskBeContributeAndFinalizeDone() {
        final Task task = getContributeAndFinalizeDoneTask(CHAIN_TASK_ID).build();

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

        final boolean taskContributeAndFinalizeDone = detector.isTaskContributeAndFinalizeDone(task);

        Assertions.assertThat(taskContributeAndFinalizeDone).isFalse();
    }

    @Test
    void shouldTaskNotBeContributeAndFinalizeDoneAsMultipleReplicates() {
        final Task task = Task.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .currentStatus(TaskStatus.FINALIZING)
                .tag(TEE_TAG)
                .build();
        final Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        final Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        final ChainTask chainTask = ChainTask.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .status(ChainTaskStatus.COMPLETED)
                .build();

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(List.of(replicate1, replicate2));
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));

        final boolean taskContributeAndFinalizeDone = detector.isTaskContributeAndFinalizeDone(task);

        Assertions.assertThat(taskContributeAndFinalizeDone).isFalse();
    }

    @Test
    void shouldTaskNotBeContributeAndFinalizeDoneAsReplicateNotDone() {
        final Task task = Task.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .currentStatus(TaskStatus.FINALIZING)
                .tag(TEE_TAG)
                .build();
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.COMPUTING, ReplicateStatusModifier.WORKER);

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(List.of(replicate));

        final boolean taskContributeAndFinalizeDone = detector.isTaskContributeAndFinalizeDone(task);

        Assertions.assertThat(taskContributeAndFinalizeDone).isFalse();
    }

    @Test
    void shouldTaskNotBeContributeAndFinalizeDoneAsChainTaskNotCompleted() {
        final Task task = Task.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .currentStatus(TaskStatus.FINALIZING)
                .tag(TEE_TAG)
                .build();
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.COMPUTING, ReplicateStatusModifier.WORKER);
        final ChainTask chainTask = ChainTask.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .status(ChainTaskStatus.REVEALING)
                .build();

        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(List.of(replicate));
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));

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
    private Task.TaskBuilder getOnchainCompletedTask(String chainTaskId) {
        final ChainTask chainTask = ChainTask.builder()
                .chainTaskId(chainTaskId)
                .status(ChainTaskStatus.COMPLETED)
                .build();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));

        return Task.builder()
                .chainTaskId(chainTaskId)
                .currentStatus(TaskStatus.FINALIZING);
    }

    private Task.TaskBuilder getOnchainRevealingTask(String chainTaskId) {
        final ChainTask chainTask = ChainTask.builder()
                .chainTaskId(chainTaskId)
                .status(ChainTaskStatus.REVEALING)
                .build();

        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(chainTask));

        return Task.builder()
                .chainTaskId(chainTaskId)
                .currentStatus(TaskStatus.FINALIZING);
    }

    private Task.TaskBuilder getContributeAndFinalizeDoneTask(String chainTaskId) {
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTE_AND_FINALIZE_DONE, ReplicateStatusModifier.WORKER);

        when(replicatesService.getReplicates(chainTaskId)).thenReturn(List.of(replicate));

        return getOnchainCompletedTask(chainTaskId)
                .tag(TEE_TAG);
    }
    // endregion
}
