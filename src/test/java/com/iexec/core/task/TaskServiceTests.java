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

package com.iexec.core.task;

import com.iexec.core.task.update.TaskUpdateRequestManager;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.iexec.core.task.TaskStatus.INITIALIZED;
import static com.iexec.core.task.TaskStatus.RUNNING;
import static com.iexec.core.task.TaskTestsUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// TODO
public class TaskServiceTests {
    private final long maxExecutionTime = 60000;
    private final Date contributionDeadline = new Date();
    private final Date finalDeadline = new Date();

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskUpdateRequestManager updateRequestManager;

    @InjectMocks
    private TaskService taskService;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotGetTaskWithTrust() {
        when(taskRepository.findByChainTaskId("dummyId")).thenReturn(Optional.empty());
        Optional<Task> task = taskService.getTaskByChainTaskId("dummyId");
        assertThat(task.isPresent()).isFalse();
    }

    @Test
    public void shouldGetOneTask() {
        Task task = getStubTask(maxExecutionTime);
        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        Optional<Task> optional = taskService.getTaskByChainTaskId(CHAIN_TASK_ID);

        assertThat(optional.isPresent()).isTrue();
        assertThat(optional).isEqualTo(Optional.of(task));
    }

    @Test
    public void shouldAddTask() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(TaskStatus.INITIALIZED);

        when(taskRepository.save(any())).thenReturn(task);
        Optional<Task> saved = taskService.addTask(CHAIN_DEAL_ID, 0, 0, DAPP_NAME, COMMAND_LINE,
                2, maxExecutionTime, "0x0", contributionDeadline, finalDeadline);
        assertThat(saved).isPresent();
        assertThat(saved).isEqualTo(Optional.of(task));
    }

    @Test
    public void shouldNotAddTask() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(TaskStatus.INITIALIZED);
        when(taskRepository.findByChainDealIdAndTaskIndex(CHAIN_DEAL_ID, 0)).thenReturn(Optional.of(task));
        Optional<Task> saved = taskService.addTask(CHAIN_DEAL_ID, 0, 0, DAPP_NAME, COMMAND_LINE,
                2, maxExecutionTime, "0x0", contributionDeadline, finalDeadline);
        assertThat(saved).isEqualTo(Optional.empty());
    }

    @Test
    public void shouldFindByCurrentStatus() {
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
    public void shouldNotFindByCurrentStatus() {
        TaskStatus status = TaskStatus.INITIALIZED;
        when(taskRepository.findByCurrentStatus(status)).thenReturn(Collections.emptyList());

        List<Task> foundTasks = taskService.findByCurrentStatus(status);

        assertThat(foundTasks).isEmpty();
    }

    @Test
    public void shouldFindByCurrentStatusList() {
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
    public void shouldNotFindByCurrentStatusList() {
        List<TaskStatus> statusList = Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.COMPLETED);
        when(taskRepository.findByCurrentStatus(statusList)).thenReturn(Collections.emptyList());

        List<Task> foundTasks = taskService.findByCurrentStatus(statusList);

        assertThat(foundTasks).isEmpty();
    }


    @Test
    public void shouldGetInitializedOrRunningTasks() {
        List<Task> tasks = Collections.singletonList(mock(Task.class));
        when(taskRepository.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(tasks);
        Assertions.assertThat(taskService.getInitializedOrRunningTasks())
                .isEqualTo(tasks);
    }

    @Test
    public void shouldGetTasksInNonFinalStatuses() {
        List<Task> tasks = Collections.singletonList(mock(Task.class));
        when(taskRepository.findByCurrentStatusNotIn(TaskStatus.getFinalStatuses()))
                .thenReturn(tasks);
        Assertions.assertThat(taskService.getTasksInNonFinalStatuses())
                .isEqualTo(tasks);
    }

    @Test
    public void shouldGetTasksWhereFinalDeadlineIsPossible() {
        List<Task> tasks = Collections.singletonList(mock(Task.class));
        when(taskRepository.findByCurrentStatusNotIn(TaskStatus.getStatusesWhereFinalDeadlineIsImpossible()))
                .thenReturn(tasks);
        Assertions.assertThat(taskService.getTasksWhereFinalDeadlineIsPossible())
                .isEqualTo(tasks);
    }

    @Test
    public void shouldGetChainTaskIdsOfTasksExpiredBefore() {
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
    public void shouldFindTaskExpired() {
        Task task = getStubTask(maxExecutionTime);
        task.setFinalDeadline(Date.from(Instant.now().minus(5, ChronoUnit.MINUTES)));
        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID))
                .thenReturn(Optional.of(task));

        assertThat(taskService.isExpired(CHAIN_TASK_ID)).isTrue();
    }
}
