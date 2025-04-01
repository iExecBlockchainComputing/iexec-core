/*
 * Copyright 2021-2025 IEXEC BLOCKCHAIN TECH
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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.List;

import static com.iexec.core.TestUtils.getStubTask;

@DataMongoTest
@TestPropertySource(properties = {"mongock.enabled=false"})
@Testcontainers
class TaskRepositoryTest {

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse(System.getProperty("mongo.image")));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.host", mongoDBContainer::getHost);
        registry.add("spring.data.mongodb.port", () -> mongoDBContainer.getMappedPort(27017));
    }

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void init() {
        taskRepository.deleteAll();
    }

    @Test
    void shouldFailWithDuplicateUniqueDealIdx() {
        final Task task1 = getStubTask();
        final Task task2 = getStubTask();
        final List<Task> tasks = Arrays.asList(task1, task2);
        Assertions.assertThatThrownBy(() -> taskRepository.saveAll(tasks))
                .isInstanceOf(DuplicateKeyException.class)
                .hasCauseExactlyInstanceOf(com.mongodb.MongoBulkWriteException.class)
                .hasMessageContainingAll("E11000", "duplicate key error collection", "unique_deal_idx dup key");
    }

    @Test
    void shouldFailWithDuplicateChainTaskId() {
        final Task task1 = getStubTask();
        task1.setTaskIndex(0);
        final Task task2 = getStubTask();
        task2.setTaskIndex(1);
        final List<Task> tasks = Arrays.asList(task1, task2);
        Assertions.assertThatThrownBy(() -> taskRepository.saveAll(tasks))
                .isInstanceOf(DuplicateKeyException.class)
                .hasCauseExactlyInstanceOf(com.mongodb.MongoBulkWriteException.class)
                .hasMessageContainingAll("E11000", "duplicate key error collection", "chainTaskId dup key");
    }

}
