/*
 * Copyright 2023 IEXEC BLOCKCHAIN TECH
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

import com.iexec.core.chain.IexecHubService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.iexec.core.task.TaskTestsUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@DataMongoTest
@Testcontainers
class TaskServiceRealRepositoryTest {
    private final long maxExecutionTime = 60000;
    private final Date contributionDeadline = new Date();
    private final Date finalDeadline = new Date();

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.2"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.host", mongoDBContainer::getContainerIpAddress);
        registry.add("spring.data.mongodb.port", () -> mongoDBContainer.getMappedPort(27017));
    }

    @Autowired
    private TaskRepository taskRepository;

    @Mock
    private IexecHubService iexecHubService;

    private TaskService taskService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        taskService = new TaskService(taskRepository, iexecHubService);
    }

    @Test
    void shouldAddTaskASingleTime() {
        final Task task = getStubTask(maxExecutionTime);
        task.changeStatus(TaskStatus.INITIALIZED);

        // Let's start 2 `taskService.addTask` at the same time.
        // Without any sync mechanism, this should fail
        // as it'll try to add twice the same task - with the same key - to the DB.
        final ExecutorService executorService = Executors.newFixedThreadPool(2);
        final List<Future<Optional<Task>>> executions = new ArrayList<>(2);
        for (int i = 0; i < 2; i++) {
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
        }).collect(Collectors.toList());

        // Check one execution has added the task,
        // while the other one has failed.
        final Optional<Task> task1 = results.get(0);
        final Optional<Task> task2 = results.get(1);
        assertThat(task1.isEmpty() || task2.isEmpty()).isTrue();
        assertThat(isExpectedTask(task, task1) || isExpectedTask(task, task2)).isTrue();

        // Finally, let's simply check the task has effectively been added.
        assertThat(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).isPresent();
    }

    private static boolean isExpectedTask(Task expectedTask, Optional<Task> resultTask) {
        return resultTask.isPresent() && Objects.equals(resultTask.get().getChainTaskId(), expectedTask.getChainTaskId());
    }
}
