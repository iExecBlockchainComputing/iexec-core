/*
 * Copyright 2022-2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.logs;

import com.iexec.common.replicate.ComputeLogs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.iexec.core.TestUtils.CHAIN_TASK_ID;
import static com.iexec.core.TestUtils.WORKER_ADDRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

@DataMongoTest
@TestPropertySource(properties = {"mongock.enabled=false"})
@Testcontainers
class TaskLogsServiceTests {

    private static final String STDOUT = "This is an stdout string";
    private static final String STDERR = "This is an stderr string";
    private static final ComputeLogs COMPUTE_LOGS = new ComputeLogs(WORKER_ADDRESS, STDOUT, STDERR);

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse(System.getProperty("mongo.image")));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.host", mongoDBContainer::getHost);
        registry.add("spring.data.mongodb.port", () -> mongoDBContainer.getMappedPort(27017));
    }

    private final TaskLogsRepository taskLogsRepository;
    private final TaskLogsService taskLogsService;

    @Autowired
    public TaskLogsServiceTests(TaskLogsRepository taskLogsRepository) {
        this.taskLogsRepository = taskLogsRepository;
        this.taskLogsService = new TaskLogsService(taskLogsRepository);
        spy(taskLogsRepository);
    }

    @BeforeEach
    void init() {
        taskLogsRepository.deleteAll();
    }

    //region addComputeLogs
    @Test
    void shouldAddComputeLogs() {
        taskLogsService.addComputeLogs(CHAIN_TASK_ID, COMPUTE_LOGS);
        assertThat(taskLogsRepository.count()).isOne();
        TaskLogs capturedEvent = taskLogsRepository.findOneByChainTaskId(CHAIN_TASK_ID).orElseThrow();
        assertThat(capturedEvent.getComputeLogsList().get(0).getStdout()).isEqualTo(STDOUT);
        assertThat(capturedEvent.getComputeLogsList().get(0).getStderr()).isEqualTo(STDERR);
        assertThat(capturedEvent.getComputeLogsList().get(0).getWalletAddress()).isEqualTo(WORKER_ADDRESS);
    }

    @Test
    void shouldNotAddComputeLogsSinceNull() {
        taskLogsService.addComputeLogs(CHAIN_TASK_ID, null);
        assertThat(taskLogsRepository.count()).isZero();
    }

    @Test
    void shouldNotAddComputeLogsSinceLogsAlreadyKnown() {
        final TaskLogs taskLogs = TaskLogs.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .computeLogsList(Collections.singletonList(COMPUTE_LOGS))
                .build();
        taskLogsRepository.save(taskLogs);
        assertThat(taskLogs.containsWalletAddress(WORKER_ADDRESS)).isTrue();
        taskLogsService.addComputeLogs(CHAIN_TASK_ID, COMPUTE_LOGS);
        assertThat(taskLogsRepository.count()).isOne();
        assertThat(taskLogsRepository.findOneByChainTaskId(CHAIN_TASK_ID)).contains(taskLogs);
    }
    //endregion

    //region delete
    @Test
    void shouldDeleteKnownTask() {
        final TaskLogs taskLogs = TaskLogs.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .computeLogsList(List.of(COMPUTE_LOGS))
                .build();
        taskLogsRepository.save(taskLogs);
        assertThat(taskLogsRepository.count()).isOne();
        taskLogsService.delete(List.of(CHAIN_TASK_ID));
        assertThat(taskLogsRepository.count()).isZero();
    }

    @Test
    void shouldNotDeleteUnknownTask() {
        final TaskLogs taskLogs = TaskLogs.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .computeLogsList(List.of(COMPUTE_LOGS))
                .build();
        taskLogsRepository.save(taskLogs);
        assertThat(taskLogsRepository.count()).isOne();
        taskLogsService.delete(List.of("0x00"));
        assertThat(taskLogsRepository.count()).isOne();
    }
    //endregion

    //region getComputeLogs
    @Test
    void shouldGetComputeLogs() {
        final TaskLogs taskLogs = TaskLogs.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .computeLogsList(List.of(COMPUTE_LOGS))
                .build();
        taskLogsRepository.save(taskLogs);
        Optional<ComputeLogs> optional = taskLogsService.getComputeLogs(CHAIN_TASK_ID, WORKER_ADDRESS);
        assertThat(optional).isPresent();
        final ComputeLogs actualLogs = optional.get();
        assertThat(actualLogs.getStdout()).isEqualTo(STDOUT);
        assertThat(actualLogs.getStderr()).isEqualTo(STDERR);
    }
    //endregion
}
