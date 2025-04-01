/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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
import com.iexec.commons.poco.chain.ChainUtils;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.replicate.ReplicatesList;
import com.iexec.core.task.event.TaskStatusesCountUpdatedEvent;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static com.iexec.core.TestUtils.*;
import static com.iexec.core.task.TaskService.METRIC_TASKS_STATUSES_COUNT;
import static com.iexec.core.task.TaskStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DataMongoTest
@TestPropertySource(properties = {"mongock.enabled=false"})
@Testcontainers
@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
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
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private IexecHubService iexecHubService;

    private TaskService taskService;

    @BeforeAll
    static void initRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
    }

    @BeforeEach
    void init() {
        taskRepository.deleteAll();
        taskService = new TaskService(mongoTemplate, taskRepository, iexecHubService, applicationEventPublisher);
    }

    @AfterEach
    void afterEach() {
        Metrics.globalRegistry.clear();
    }

    // region getTaskByChainTaskId
    @Test
    void shouldNotGetTaskWithTrust() {
        Optional<Task> task = taskService.getTaskByChainTaskId("dummyId");
        assertThat(task).isEmpty();
    }

    @Test
    void shouldGetOneTask() {
        final Task task = getStubTask();
        taskRepository.save(task);
        Optional<Task> optional = taskService.getTaskByChainTaskId(CHAIN_TASK_ID);
        assertThat(optional).usingRecursiveComparison().isEqualTo(Optional.of(task));
    }
    // endregion

    // region addTask
    @Test
    void shouldAddTask() {
        final Task task = getStubTask();
        task.setTrust(2);
        task.setContributionDeadline(contributionDeadline);
        task.setFinalDeadline(finalDeadline);

        Optional<Task> saved = taskService.addTask(CHAIN_DEAL_ID, 0, 0, DAPP_NAME, COMMAND_LINE,
                2, maxExecutionTime, NO_TEE_TAG, contributionDeadline, finalDeadline);
        assertThat(saved)
                .usingRecursiveComparison()
                .ignoringFields("value.id", "value.version")
                .ignoringFieldsMatchingRegexes("value.dateStatusList.*")
                .isEqualTo(Optional.of(task));
    }

    @Test
    void shouldAddTaskASingleTime() {
        final int concurrentRequests = 5;
        final String expectedChainTaskId = ChainUtils.generateChainTaskId(CHAIN_DEAL_ID, 0);

        // Let's start n `taskService.addTask` at the same time.
        // Without any sync mechanism, this should fail
        // as it'll try to add more than once the same task - with the same key - to the DB.
        final ExecutorService executorService = Executors.newFixedThreadPool(concurrentRequests);
        final List<Future<Optional<Task>>> executions = new ArrayList<>(concurrentRequests);
        for (int i = 0; i < concurrentRequests; i++) {
            executions.add(executorService.submit(() -> taskService.addTask(CHAIN_DEAL_ID, 0, 0, DAPP_NAME, COMMAND_LINE,
                    2, maxExecutionTime, "0x0", contributionDeadline, finalDeadline)));
        }

        // Let's wait for the `taskService.addTask` to complete and retrieve the results.
        List<Optional<Task>> results = executions.stream().map(execution -> {
            try {
                return execution.get(1, TimeUnit.MINUTES);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof DuplicateKeyException) {
                    fail("Task has been added twice. Should not happen!");
                }
                throw new RuntimeException("Something went wrong.", e);
            } catch (InterruptedException | TimeoutException e) {
                throw new RuntimeException(e);
            }
        }).toList();

        // Check one execution has added the task,
        // while the others have failed.
        assertThat(results).hasSize(concurrentRequests);
        final List<Task> nonEmptyResults = results
                .stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        assertThat(nonEmptyResults).hasSize(1);
        assertThat(nonEmptyResults.get(0).getChainTaskId()).isEqualTo(expectedChainTaskId);

        // Finally, let's simply check the task has effectively been added.
        assertThat(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).isPresent();
    }

    @Test
    void shouldNotAddTask() {
        final Task task = getStubTask(INITIALIZED);
        taskRepository.save(task);

        Optional<Task> saved = taskService.addTask(CHAIN_DEAL_ID, 0, 0, DAPP_NAME, COMMAND_LINE,
                2, maxExecutionTime, "0x0", contributionDeadline, finalDeadline);
        assertThat(saved).isEmpty();
    }
    // endregion

    // region updateTask & updateTaskStatus
    @Test
    void shouldNotUpdateTaskStatusAndEmitWarning(CapturedOutput output) {
        final Task task = getStubTask();
        taskRepository.save(task);
        final long modifiedCount = taskService.updateTaskStatus(CHAIN_TASK_ID, INITIALIZING, INITIALIZING,
                List.of(TaskStatusChange.builder().status(INITIALIZING).build()));
        assertThat(modifiedCount).isZero();
        assertThat(output.getOut()).contains("The task was not updated [chainTaskId:" + CHAIN_TASK_ID + "]");
    }

    @Test
    void shouldUpdateTaskStatus() {
        final Task task = getStubTask(INITIALIZING);
        taskRepository.save(task);
        final long modifiedCount = taskService.updateTaskStatus(CHAIN_TASK_ID, INITIALIZING, INITIALIZED,
                List.of(TaskStatusChange.builder().status(INITIALIZED).build()));
        assertThat(modifiedCount).isOne();
    }
    // endregion

    // region findByCurrentStatus
    @Test
    void shouldFindByCurrentStatus() {
        final Task task = getStubTask(INITIALIZED);
        taskRepository.save(task);

        final List<Task> foundTasks = taskService.findByCurrentStatus(INITIALIZED);

        assertThat(foundTasks).usingRecursiveComparison().isEqualTo(List.of(task));
        assertThat(foundTasks.get(0).getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    void shouldNotFindByCurrentStatus() {
        List<Task> foundTasks = taskService.findByCurrentStatus(INITIALIZED);
        assertThat(foundTasks).isEmpty();
    }

    @Test
    void shouldFindByCurrentStatusList() {
        List<TaskStatus> statusList = List.of(TaskStatus.INITIALIZED, TaskStatus.COMPLETED);

        final Task task = getStubTask(INITIALIZED);
        taskRepository.save(task);

        List<Task> foundTasks = taskService.findByCurrentStatus(statusList);

        assertThat(foundTasks).usingRecursiveComparison().isEqualTo(List.of(task));
        assertThat(foundTasks.get(0).getCurrentStatus()).isIn(statusList);
    }

    @Test
    void shouldNotFindByCurrentStatusList() {
        List<TaskStatus> statusList = List.of(TaskStatus.INITIALIZED, TaskStatus.COMPLETED);
        List<Task> foundTasks = taskService.findByCurrentStatus(statusList);
        assertThat(foundTasks).isEmpty();
    }
    // endregion

    // region getPrioritizedInitializedOrRunningTask
    @Test
    void shouldGetInitializedOrRunningTasks() {
        final Task task = getStubTask();
        task.setCurrentStatus(INITIALIZED);
        taskRepository.save(task);
        assertThat(taskService.getPrioritizedInitializedOrRunningTask(false, List.of()))
                .usingRecursiveComparison()
                .isEqualTo(Optional.of(task));
    }

    @Test
    void shouldNotGetTaskPastContributionDeadline() {
        final Task task = getStubTask();
        task.setContributionDeadline(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        taskRepository.save(task);
        assertThat(taskService.getPrioritizedInitializedOrRunningTask(false, List.of()))
                .usingRecursiveComparison()
                .isEqualTo(Optional.empty());
    }
    // endregion

    @Test
    void shouldGetChainTaskIdsOfTasksExpiredBefore() {
        Date date = new Date();
        final Task task = getStubTask();
        task.setFinalDeadline(Date.from(Instant.now().minus(5, ChronoUnit.MINUTES)));
        taskRepository.save(task);
        assertThat(taskService.getChainTaskIdsOfTasksExpiredBefore(date))
                .isEqualTo(List.of(CHAIN_TASK_ID));
    }

    @Test
    void shouldFindTaskExpired() {
        final Task task = getStubTask();
        task.setFinalDeadline(Date.from(Instant.now().minus(5, ChronoUnit.MINUTES)));
        taskRepository.save(task);
        assertThat(taskService.isExpired(CHAIN_TASK_ID)).isTrue();
    }

    // region isConsensusReached()
    @Test
    void shouldConsensusNotBeReachedAsUnknownTask() {
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        assertThat(taskService.isConsensusReached(new ReplicatesList(CHAIN_TASK_ID)))
                .isFalse();

        Mockito.verify(iexecHubService).getChainTask(any());
    }

    @Test
    void shouldConsensusNotBeReachedAsNotRevealing() {
        final Task task = getStubTask();

        final ChainTask chainTask = ChainTask
                .builder()
                .chainTaskId(task.getChainTaskId())
                .status(ChainTaskStatus.COMPLETED)
                .build();
        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(chainTask));

        assertThat(taskService.isConsensusReached(new ReplicatesList(task.getChainTaskId())))
                .isFalse();

        Mockito.verify(iexecHubService).getChainTask(any());
    }

    @Test
    void shouldConsensusNotBeReachedAsOnChainWinnersHigherThanOffchainWinners() {
        final Task task = getStubTask();
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
        final Task task = getStubTask();
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

    // region metrics
    @Test
    void shouldBuildGaugesAndFireEvent() {
        final List<Task> tasks = new ArrayList<>();
        BigInteger taskId = BigInteger.ZERO;
        for (final TaskStatus status : TaskStatus.values()) {
            // Give a unique initial count for each status
            taskId = taskId.add(BigInteger.ONE);
            final Task task = new Task(Numeric.toHexStringWithPrefix(taskId), 0, "", "", 0, 0, "0x0");
            task.setChainTaskId(Numeric.toHexStringWithPrefix(taskId));
            task.setCurrentStatus(status);
            task.getDateStatusList().add(TaskStatusChange.builder().status(status).build());
            tasks.add(task);
        }
        taskRepository.saveAll(tasks);

        taskService.init();

        verify(applicationEventPublisher, timeout(1000L)).publishEvent(any(TaskStatusesCountUpdatedEvent.class));
        for (final TaskStatus status : TaskStatus.values()) {
            final Gauge gauge = getCurrentTasksCountGauge(status);
            assertThat(gauge).isNotNull()
                    // Check the gauge value is equal to the unique count for each status
                    .extracting(Gauge::value)
                    .isEqualTo(1.0);
        }
    }
    // endregion

    // region onTaskCreatedEvent
    @Test
    void shouldIncrementCurrentReceivedGaugeWhenTaskReceived() {
        taskService.init();
        final Gauge currentReceivedTasks = getCurrentTasksCountGauge(RECEIVED);
        assertThat(currentReceivedTasks.value()).isZero();
        taskService.onTaskCreatedEvent();
        assertThat(currentReceivedTasks.value()).isOne();
    }

    @Test
    void shouldUpdateCurrentReceivedCountAndFireEvent() {
        final LinkedHashMap<TaskStatus, AtomicLong> currentTaskStatusesCount = new LinkedHashMap<>();
        currentTaskStatusesCount.put(RECEIVED, new AtomicLong(0L));
        ReflectionTestUtils.setField(taskService, "currentTaskStatusesCount", currentTaskStatusesCount);

        taskService.onTaskCreatedEvent();

        assertThat(currentTaskStatusesCount.get(RECEIVED).get()).isOne();
        verify(applicationEventPublisher).publishEvent(any(TaskStatusesCountUpdatedEvent.class));
    }
    // endregion

    // region updateMetricsAfterStatusUpdate
    @Test
    void shouldUpdateMetricsAfterStatusUpdate() {
        Task receivedTask = new Task("", "", 0);
        taskRepository.save(receivedTask);

        // Init gauges
        taskService.init();
        // Called a first time during init
        verify(applicationEventPublisher, timeout(1000L)).publishEvent(any(TaskStatusesCountUpdatedEvent.class));

        final Gauge currentReceivedTasks = getCurrentTasksCountGauge(RECEIVED);
        final Gauge currentInitializingTasks = getCurrentTasksCountGauge(INITIALIZING);

        assertThat(currentReceivedTasks.value()).isOne();
        assertThat(currentInitializingTasks.value()).isZero();

        taskService.updateMetricsAfterStatusUpdate(RECEIVED, INITIALIZING);

        assertThat(currentReceivedTasks.value()).isZero();
        assertThat(currentInitializingTasks.value()).isOne();
        // Called a second time during update
        verify(applicationEventPublisher, times(2)).publishEvent(any(TaskStatusesCountUpdatedEvent.class));
    }
    // endregion

    // region util
    Gauge getCurrentTasksCountGauge(TaskStatus status) {
        return Metrics.globalRegistry
                .find(METRIC_TASKS_STATUSES_COUNT)
                .tags(
                        "period", "current",
                        "status", status.name()
                ).gauge();
    }
    // endregion

}
