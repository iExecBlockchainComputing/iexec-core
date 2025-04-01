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

package com.iexec.core.detector.task;

import com.iexec.core.chain.IexecHubService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskRepository;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatusChange;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static com.iexec.core.task.TaskStatus.*;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@Slf4j
@DataMongoTest
@TestPropertySource(properties = {"mongock.enabled=false"})
@Testcontainers
@ExtendWith(MockitoExtension.class)
class FinalDeadlineTaskDetectorTests {

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

    private FinalDeadlineTaskDetector finalDeadlineTaskDetector;

    @BeforeEach
    void init() {
        taskRepository.deleteAll();
        final TaskService taskService = new TaskService(mongoTemplate, taskRepository, iexecHubService, applicationEventPublisher);
        finalDeadlineTaskDetector = new FinalDeadlineTaskDetector(taskService, applicationEventPublisher);
    }

    private Task getTask() {
        Task task = new Task("", "", 0);
        task.setChainTaskId("0x1");
        task.setCurrentStatus(RUNNING);
        task.getDateStatusList().add(TaskStatusChange.builder().status(RUNNING).build());
        return task;
    }

    @Test
    void shouldDetectTaskAfterFinalDeadline() {
        final Task task = getTask();
        task.setFinalDeadline(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        taskRepository.save(task);
        finalDeadlineTaskDetector.detect();
        final Task finalTask = taskRepository.findByChainTaskId("0x1").orElse(null);
        assertThat(finalTask).isNotNull();
        assertThat(finalTask.getDateStatusList()).isNotNull();
        assertThat(finalTask.getDateStatusList().stream().map(TaskStatusChange::getStatus))
                .contains(FINAL_DEADLINE_REACHED, FAILED);
    }

    @Test
    void shouldNotDetectTaskBeforeFinalDeadline() {
        final Task task = getTask();
        task.setFinalDeadline(Date.from(Instant.now().plus(1, ChronoUnit.MINUTES)));
        taskRepository.save(task);
        finalDeadlineTaskDetector.detect();
        final Task finalTask = taskRepository.findByChainTaskId("0x1").orElse(null);
        assertThat(finalTask).isNotNull();
        assertThat(finalTask.getDateStatusList()).isNotNull();
        assertThat(finalTask.getDateStatusList().stream().map(TaskStatusChange::getStatus))
                .doesNotContain(FINAL_DEADLINE_REACHED, FAILED);
    }
}
