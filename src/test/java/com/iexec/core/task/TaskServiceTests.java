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

package com.iexec.core.task;

import com.iexec.commons.poco.chain.ChainTask;
import com.iexec.commons.poco.chain.ChainTaskStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.replicate.ReplicatesList;
import com.iexec.core.replicate.ReplicatesService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.iexec.core.task.TaskStatus.COMPLETED;
import static com.iexec.core.task.TaskStatus.INITIALIZED;
import static com.iexec.core.task.TaskTestsUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@DataMongoTest
@Testcontainers
class TaskServiceTests {
    private final long maxExecutionTime = 60000;
    private final Date contributionDeadline = new Date();
    private final Date finalDeadline = new Date();

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse(System.getProperty("mongo.image")));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.host", mongoDBContainer::getHost);
        registry.add("spring.data.mongodb.port", () -> mongoDBContainer.getMappedPort(27017));
    }

    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private TaskRepository taskRepository;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private IexecHubService iexecHubService;

    private TaskService taskService;

    @BeforeAll
    static void initRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
    }

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        taskService = new TaskService(mongoTemplate, taskRepository, iexecHubService);
        taskService.init();
        taskRepository.deleteAll();
    }

    @AfterEach
    void afterEach() {
        Metrics.globalRegistry.clear();
    }

    @Test
    void shouldNotGetTaskWithTrust() {
        Optional<Task> task = taskService.getTaskByChainTaskId("dummyId");
        assertThat(task).isEmpty();
    }

    @Test
    void shouldGetOneTask() {
        Task task = getStubTask(maxExecutionTime);
        taskRepository.save(task);
        Optional<Task> optional = taskService.getTaskByChainTaskId(CHAIN_TASK_ID);
        assertThat(optional).usingRecursiveComparison().isEqualTo(Optional.of(task));
    }

    @Test
    void shouldAddTask() {
        Task task = getStubTask(maxExecutionTime);
        task.setTrust(2);
        task.setContributionDeadline(contributionDeadline);
        task.setFinalDeadline(finalDeadline);

        Optional<Task> saved = taskService.addTask(CHAIN_DEAL_ID, 0, 0, DAPP_NAME, COMMAND_LINE,
                2, maxExecutionTime, NO_TEE_TAG, contributionDeadline, finalDeadline);
        assertThat(saved)
                .usingRecursiveComparison()
                .ignoringFields("value.id", "value.version")
                .isEqualTo(Optional.of(task));
    }

    @Test
    void shouldNotAddTask() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(TaskStatus.INITIALIZED);
        taskRepository.save(task);

        Optional<Task> saved = taskService.addTask(CHAIN_DEAL_ID, 0, 0, DAPP_NAME, COMMAND_LINE,
                2, maxExecutionTime, "0x0", contributionDeadline, finalDeadline);
        assertThat(saved).isEmpty();
    }

    @Test
    void shouldFindByCurrentStatus() {
        TaskStatus status = TaskStatus.INITIALIZED;

        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(status);
        taskRepository.save(task);

        List<Task> taskList = List.of(task);

        List<Task> foundTasks = taskService.findByCurrentStatus(status);

        assertThat(foundTasks).usingRecursiveComparison().isEqualTo(taskList);
        assertThat(foundTasks.get(0).getCurrentStatus()).isEqualTo(status);
    }

    @Test
    void shouldNotFindByCurrentStatus() {
        List<Task> foundTasks = taskService.findByCurrentStatus(INITIALIZED);
        assertThat(foundTasks).isEmpty();
    }

    @Test
    void shouldFindByCurrentStatusList() {
        List<TaskStatus> statusList = Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.COMPLETED);

        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(TaskStatus.INITIALIZED);
        taskRepository.save(task);

        List<Task> taskList = new ArrayList<>();
        taskList.add(task);

        List<Task> foundTasks = taskService.findByCurrentStatus(statusList);

        assertThat(foundTasks).usingRecursiveComparison().isEqualTo(taskList);
        assertThat(foundTasks.get(0).getCurrentStatus()).isIn(statusList);
    }

    @Test
    void shouldNotFindByCurrentStatusList() {
        List<TaskStatus> statusList = Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.COMPLETED);
        List<Task> foundTasks = taskService.findByCurrentStatus(statusList);
        assertThat(foundTasks).isEmpty();
    }


    @Test
    void shouldGetInitializedOrRunningTasks() {
        Task task = getStubTask(maxExecutionTime);
        task.setCurrentStatus(INITIALIZED);
        taskRepository.save(task);
        assertThat(taskService.getPrioritizedInitializedOrRunningTask(false, Collections.emptyList()))
                .usingRecursiveComparison()
                .isEqualTo(Optional.of(task));
    }

    @Test
    void shouldGetTasksInNonFinalStatuses() {
        Task task = getStubTask(maxExecutionTime);
        taskRepository.save(task);
        assertThat(taskService.getTasksInNonFinalStatuses())
                .usingRecursiveComparison()
                .isEqualTo(List.of(task));
    }

    @Test
    void shouldGetTasksWhereFinalDeadlineIsPossible() {
        Task task = getStubTask(maxExecutionTime);
        taskRepository.save(task);
        assertThat(taskService.getTasksWhereFinalDeadlineIsPossible())
                .usingRecursiveComparison()
                .isEqualTo(List.of(task));
    }

    @Test
    void shouldGetChainTaskIdsOfTasksExpiredBefore() {
        Date date = new Date();
        Task task = getStubTask(maxExecutionTime);
        task.setFinalDeadline(Date.from(Instant.now().minus(5, ChronoUnit.MINUTES)));
        taskRepository.save(task);
        assertThat(taskService.getChainTaskIdsOfTasksExpiredBefore(date))
                .isEqualTo(List.of(CHAIN_TASK_ID));
    }

    // isExpired

    @Test
    void shouldFindTaskExpired() {
        Task task = getStubTask(maxExecutionTime);
        task.setFinalDeadline(Date.from(Instant.now().minus(5, ChronoUnit.MINUTES)));
        taskRepository.save(task);
        assertThat(taskService.isExpired(CHAIN_TASK_ID)).isTrue();
    }

    // region updateTask()
    @Test
    void shouldUpdateTaskAndMetrics() {
        Counter counter = Metrics.globalRegistry.find(TaskService.METRIC_TASKS_COMPLETED_COUNT).counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isZero();

        Task task = getStubTask(maxExecutionTime);
        task.setCurrentStatus(TaskStatus.COMPLETED);
        taskRepository.save(task);

        Optional<Task> optional = taskService.updateTask(task);

        assertThat(optional)
                .isPresent()
                .isEqualTo(Optional.of(task));
        counter = Metrics.globalRegistry.find(TaskService.METRIC_TASKS_COMPLETED_COUNT).counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isOne();
    }

    @Test
    void shouldUpdateTaskButNotMetricsWhenTaskIsNotCompleted() {
        Counter counter = Metrics.globalRegistry.find(TaskService.METRIC_TASKS_COMPLETED_COUNT).counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isZero();

        Task task = getStubTask(maxExecutionTime);
        taskRepository.save(task);

        Optional<Task> optional = taskService.updateTask(task);

        assertThat(optional)
                .isPresent()
                .isEqualTo(Optional.of(task));
        counter = Metrics.globalRegistry.find(TaskService.METRIC_TASKS_COMPLETED_COUNT).counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isZero();
    }

    @Test
    void shouldNotUpdateTaskSinceUnknownTask() {
        Task task = getStubTask(maxExecutionTime);

        Optional<Task> optional = taskService.updateTask(task);

        assertThat(optional).isEmpty();
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
        taskRepository.saveAll(List.of(
                Task.builder().taskIndex(1).chainTaskId("0x1").currentStatus(COMPLETED).build(),
                Task.builder().taskIndex(2).chainTaskId("0x2").currentStatus(COMPLETED).build(),
                Task.builder().taskIndex(3).chainTaskId("0x3").currentStatus(COMPLETED).build()
        ));
        taskService.init();

        final long completedTasksCount = taskService.getCompletedTasksCount();
        assertThat(completedTasksCount).isEqualTo(3);
    }
    // endregion
}
