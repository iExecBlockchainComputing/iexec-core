/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.task;

import com.iexec.commons.poco.chain.ChainTask;
import com.iexec.commons.poco.chain.ChainTaskStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.replicate.ReplicatesList;
import com.iexec.core.replicate.ReplicatesService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.iexec.core.task.TaskStatus.*;
import static com.iexec.core.task.TaskTestsUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskServiceTests {
    private final long maxExecutionTime = 60000;
    private final Date contributionDeadline = new Date();
    private final Date finalDeadline = new Date();

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private IexecHubService iexecHubService;

    @InjectMocks
    private TaskService taskService;

    @BeforeAll
    static void initRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
    }

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        taskService.init();
    }

    @AfterEach
    void afterEach() {
        Metrics.globalRegistry.clear();
    }

    @Test
    void shouldNotGetTaskWithTrust() {
        when(taskRepository.findByChainTaskId("dummyId")).thenReturn(Optional.empty());
        Optional<Task> task = taskService.getTaskByChainTaskId("dummyId");
        assertThat(task).isEmpty();
    }

    @Test
    void shouldGetOneTask() {
        Task task = getStubTask(maxExecutionTime);
        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        Optional<Task> optional = taskService.getTaskByChainTaskId(CHAIN_TASK_ID);

        assertThat(optional).contains(task);
    }

    @Test
    void shouldAddTask() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(TaskStatus.INITIALIZED);

        when(taskRepository.save(any())).thenReturn(task);
        Optional<Task> saved = taskService.addTask(CHAIN_DEAL_ID, 0, 0, DAPP_NAME, COMMAND_LINE,
                2, maxExecutionTime, "0x0", contributionDeadline, finalDeadline);
        assertThat(saved).contains(task);
    }

    @Test
    void shouldNotAddTask() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(TaskStatus.INITIALIZED);
        when(taskRepository.save(any())).thenThrow(DuplicateKeyException.class);

        Optional<Task> saved = taskService.addTask(CHAIN_DEAL_ID, 0, 0, DAPP_NAME, COMMAND_LINE,
                2, maxExecutionTime, "0x0", contributionDeadline, finalDeadline);
        assertThat(saved).isEmpty();
    }

    @Test
    void shouldFindByCurrentStatus() {
        TaskStatus status = TaskStatus.INITIALIZED;

        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(status);

        List<Task> taskList = new ArrayList<>();
        taskList.add(task);

        when(taskRepository.findByCurrentStatus(status)).thenReturn(taskList);

        List<Task> foundTasks = taskService.findByCurrentStatus(status);

        assertThat(foundTasks).isEqualTo(taskList);
        assertThat(foundTasks.get(0).getCurrentStatus()).isEqualTo(status);
    }

    @Test
    void shouldNotFindByCurrentStatus() {
        TaskStatus status = TaskStatus.INITIALIZED;
        when(taskRepository.findByCurrentStatus(status)).thenReturn(Collections.emptyList());

        List<Task> foundTasks = taskService.findByCurrentStatus(status);

        assertThat(foundTasks).isEmpty();
    }

    @Test
    void shouldFindByCurrentStatusList() {
        List<TaskStatus> statusList = Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.COMPLETED);

        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(TaskStatus.INITIALIZED);

        List<Task> taskList = new ArrayList<>();
        taskList.add(task);

        when(taskRepository.findByCurrentStatus(statusList)).thenReturn(taskList);

        List<Task> foundTasks = taskService.findByCurrentStatus(statusList);

        assertThat(foundTasks).isEqualTo(taskList);
        assertThat(foundTasks.get(0).getCurrentStatus()).isIn(statusList);
    }

    @Test
    void shouldNotFindByCurrentStatusList() {
        List<TaskStatus> statusList = Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.COMPLETED);
        when(taskRepository.findByCurrentStatus(statusList)).thenReturn(Collections.emptyList());

        List<Task> foundTasks = taskService.findByCurrentStatus(statusList);

        assertThat(foundTasks).isEmpty();
    }


    @Test
    void shouldGetInitializedOrRunningTasks() {
        Task task = mock(Task.class);
        when(taskRepository.findFirstByCurrentStatusInAndTagNotInAndChainTaskIdNotIn(
                eq(Arrays.asList(INITIALIZED, RUNNING)),
                any(),
                eq(Collections.emptyList()),
                eq(Sort.by(Sort.Order.desc(Task.CURRENT_STATUS_FIELD_NAME),
                        Sort.Order.asc(Task.CONTRIBUTION_DEADLINE_FIELD_NAME)))))
                .thenReturn(Optional.of(task));
        Assertions.assertThat(taskService.getPrioritizedInitializedOrRunningTask(false, Collections.emptyList()))
                .get()
                .isEqualTo(task);
    }

    @Test
    void shouldGetTasksInNonFinalStatuses() {
        List<Task> tasks = Collections.singletonList(mock(Task.class));
        when(taskRepository.findByCurrentStatusNotIn(TaskStatus.getFinalStatuses()))
                .thenReturn(tasks);
        Assertions.assertThat(taskService.getTasksInNonFinalStatuses())
                .isEqualTo(tasks);
    }

    @Test
    void shouldGetTasksWhereFinalDeadlineIsPossible() {
        List<Task> tasks = Collections.singletonList(mock(Task.class));
        when(taskRepository.findByCurrentStatusNotIn(TaskStatus.getStatusesWhereFinalDeadlineIsImpossible()))
                .thenReturn(tasks);
        Assertions.assertThat(taskService.getTasksWhereFinalDeadlineIsPossible())
                .isEqualTo(tasks);
    }

    @Test
    void shouldGetChainTaskIdsOfTasksExpiredBefore() {
        Date date = new Date();
        Task task = mock(Task.class);
        when(task.getChainTaskId()).thenReturn(CHAIN_TASK_ID);
        List<Task> tasks = Collections.singletonList(task);
        when(taskRepository.findChainTaskIdsByFinalDeadlineBefore(date))
                .thenReturn(tasks);
        Assertions.assertThat(taskService.getChainTaskIdsOfTasksExpiredBefore(date))
                .isEqualTo(Collections.singletonList(CHAIN_TASK_ID));
    }

    // isExpired

    @Test
    void shouldFindTaskExpired() {
        Task task = getStubTask(maxExecutionTime);
        task.setFinalDeadline(Date.from(Instant.now().minus(5, ChronoUnit.MINUTES)));
        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID))
                .thenReturn(Optional.of(task));

        assertThat(taskService.isExpired(CHAIN_TASK_ID)).isTrue();
    }

    // region updateTask()
    @Test
    void shouldUpdateTaskAndMetrics() {
        Counter counter = Metrics.globalRegistry.find(TaskService.METRIC_TASKS_COMPLETED_COUNT).counter();
        Assertions.assertThat(counter).isNotNull();
        Assertions.assertThat(counter.count()).isZero();

        Task task = getStubTask(maxExecutionTime);
        task.setCurrentStatus(TaskStatus.COMPLETED);
        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        Optional<Task> optional = taskService.updateTask(task);

        assertThat(optional)
                .isPresent()
                .isEqualTo(Optional.of(task));
        counter = Metrics.globalRegistry.find(TaskService.METRIC_TASKS_COMPLETED_COUNT).counter();
        Assertions.assertThat(counter).isNotNull();
        Assertions.assertThat(counter.count()).isOne();
    }

    @Test
    void shouldUpdateTaskButNotMetricsWhenTaskIsNotCompleted() {
        Counter counter = Metrics.globalRegistry.find(TaskService.METRIC_TASKS_COMPLETED_COUNT).counter();
        Assertions.assertThat(counter).isNotNull();
        Assertions.assertThat(counter.count()).isZero();

        Task task = getStubTask(maxExecutionTime);
        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        Optional<Task> optional = taskService.updateTask(task);

        assertThat(optional)
                .isPresent()
                .isEqualTo(Optional.of(task));
        counter = Metrics.globalRegistry.find(TaskService.METRIC_TASKS_COMPLETED_COUNT).counter();
        Assertions.assertThat(counter).isNotNull();
        Assertions.assertThat(counter.count()).isZero();
    }

    @Test
    void shouldNotUpdateTaskSinceUnknownTask() {
        Task task = getStubTask(maxExecutionTime);
        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        Optional<Task> optional = taskService.updateTask(task);

        assertThat(optional)
                .isEmpty();
    }
    // endregion

    // region isConsensusReached()
    @Test
    void shouldConsensusNotBeReachedAsUnknownTask() {
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        assertThat(taskService.isConsensusReached(new ReplicatesList(CHAIN_TASK_ID)))
                .isFalse();

        Mockito.verify(iexecHubService).getChainTask(any());
        Mockito.verifyNoInteractions(replicatesService);
    }

    @Test
    void shouldConsensusNotBeReachedAsNotRevealing() {
        Task task = getStubTask(maxExecutionTime);

        final ChainTask chainTask = ChainTask
                .builder()
                .chainTaskId(task.getChainTaskId())
                .status(ChainTaskStatus.COMPLETED)
                .build();
        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(chainTask));

        assertThat(taskService.isConsensusReached(new ReplicatesList(task.getChainTaskId())))
                .isFalse();

        Mockito.verify(iexecHubService).getChainTask(any());
        Mockito.verifyNoInteractions(replicatesService);
    }

    @Test
    void shouldConsensusNotBeReachedAsOnChainWinnersHigherThanOffchainWinners() {
        final Task task = getStubTask(maxExecutionTime);
        final ReplicatesList replicatesList = Mockito.spy(new ReplicatesList(task.getChainTaskId()));
        final ChainTask chainTask = ChainTask
                .builder()
                .chainTaskId(task.getChainTaskId())
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(10)
                .consensusValue("dummyValue")
                .build();

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(chainTask));
        when(replicatesList.getNbValidContributedWinners(chainTask.getConsensusValue())).thenReturn(0);
        assertThat(taskService.isConsensusReached(replicatesList))
                .isFalse();

        Mockito.verify(iexecHubService).getChainTask(any());
    }

    @Test
    void shouldConsensusBeReached() {
        final Task task = getStubTask(maxExecutionTime);
        final ReplicatesList replicatesList = Mockito.spy(new ReplicatesList(task.getChainTaskId()));
        final ChainTask chainTask = ChainTask
                .builder()
                .chainTaskId(task.getChainTaskId())
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(1)
                .consensusValue("dummyValue")
                .build();

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(chainTask));
        when(replicatesList.getNbValidContributedWinners(chainTask.getConsensusValue())).thenReturn(1);
        assertThat(taskService.isConsensusReached(replicatesList))
                .isTrue();

        Mockito.verify(iexecHubService).getChainTask(any());
    }
    // endregion

    // region getCompletedTasksCount
    @Test
    void shouldGet0CompletedTasksCountWhenNoTaskCompleted() {
        final long completedTasksCount = taskService.getCompletedTasksCount();
        assertThat(completedTasksCount).isZero();
    }

    @Test
    void shouldGet3CompletedTasksCount() {
        final TaskService taskService = new TaskService(mongoTemplate, taskRepository, iexecHubService);
        final Task task = Task.builder().currentStatus(COMPLETED).build();
        when(taskRepository.findByCurrentStatus(COMPLETED))
                .thenReturn(List.of(task, task, task));
        taskService.init();

        final long completedTasksCount = taskService.getCompletedTasksCount();
        assertThat(completedTasksCount).isEqualTo(3);
    }
    // endregion
}
