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

package com.iexec.core.detector.task;

import com.iexec.core.chain.IexecHubService;
import com.iexec.core.task.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static com.iexec.core.task.TaskStatus.*;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@DataMongoTest
@Testcontainers
class ContributionTimeoutTaskDetectorTests {

    private final static String CHAIN_TASK_ID = "chainTaskId";

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
    private IexecHubService iexecHubService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private ContributionTimeoutTaskDetector contributionTimeoutTaskDetector;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        taskRepository.deleteAll();
        final TaskService taskService = new TaskService(mongoTemplate, taskRepository, iexecHubService, applicationEventPublisher);
        contributionTimeoutTaskDetector = new ContributionTimeoutTaskDetector(taskService, applicationEventPublisher);
    }

    @Test
    void shouldNotDetectTaskAfterContributionDeadlineIfNotInitializedOrRunning() {
        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(AT_LEAST_ONE_REVEALED);
        task.setContributionDeadline(Date.from(Instant.now().minus(1L, ChronoUnit.MINUTES)));
        taskRepository.save(task);

        contributionTimeoutTaskDetector.detect();

        Task finalTask = taskRepository.findByChainTaskId(CHAIN_TASK_ID).orElse(null);
        assertThat(finalTask).isNotNull();
        assertThat(finalTask.getCurrentStatus()).isEqualTo(AT_LEAST_ONE_REVEALED);
        assertThat(finalTask.getDateStatusList()).isNotNull();
        assertThat(finalTask.getDateStatusList().stream().map(TaskStatusChange::getStatus))
                .doesNotContain(CONTRIBUTION_TIMEOUT, FAILED);
    }

    @Test
    void shouldNotDetectTaskIfBeforeTimeout() {
        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.RUNNING);
        task.setContributionDeadline(Date.from(Instant.now().plus(1L, ChronoUnit.MINUTES)));
        taskRepository.save(task);

        contributionTimeoutTaskDetector.detect();

        Task finalTask = taskRepository.findByChainTaskId(CHAIN_TASK_ID).orElse(null);
        assertThat(finalTask).isNotNull();
        assertThat(finalTask.getCurrentStatus()).isEqualTo(RUNNING);
        assertThat(finalTask.getDateStatusList().stream().map(TaskStatusChange::getStatus))
                .doesNotContain(CONTRIBUTION_TIMEOUT, FAILED);
    }

    @Test
    void shouldDetectTaskIfBetweenContributionAndFinalDeadline() {
        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.RUNNING);
        task.setContributionDeadline(Date.from(Instant.now().minus(1L, ChronoUnit.MINUTES)));
        task.setFinalDeadline(Date.from(Instant.now().plus(1L, ChronoUnit.MINUTES)));
        taskRepository.save(task);

        contributionTimeoutTaskDetector.detect();

        Task finalTask = taskRepository.findByChainTaskId(CHAIN_TASK_ID).orElse(null);
        assertThat(finalTask).isNotNull();
        assertThat(finalTask.getCurrentStatus()).isEqualTo(FAILED);
        assertThat(finalTask.getDateStatusList().stream().map(TaskStatusChange::getStatus))
                .contains(CONTRIBUTION_TIMEOUT, FAILED);
    }

    @Test
    void shouldNotDetectTaskIfAfterFinalDeadline() {
        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.RUNNING);
        task.setContributionDeadline(Date.from(Instant.now().minus(2L, ChronoUnit.MINUTES)));
        task.setFinalDeadline(Date.from(Instant.now().minus(1L, ChronoUnit.MINUTES)));
        taskRepository.save(task);

        contributionTimeoutTaskDetector.detect();

        Task finalTask = taskRepository.findByChainTaskId(CHAIN_TASK_ID).orElse(null);
        assertThat(finalTask).isNotNull();
        assertThat(finalTask.getCurrentStatus()).isEqualTo(RUNNING);
        assertThat(finalTask.getDateStatusList().stream().map(TaskStatusChange::getStatus))
                .doesNotContain(CONTRIBUTION_TIMEOUT, FAILED);
    }
}
