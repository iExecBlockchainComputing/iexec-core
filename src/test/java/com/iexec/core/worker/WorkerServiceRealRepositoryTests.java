/*
 * Copyright 2023-2023 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.worker;

import com.iexec.core.configuration.WorkerConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.iexec.commons.poco.utils.TestUtils.WALLET_WORKER_1;

@Slf4j
@DataMongoTest
@Testcontainers
class WorkerServiceRealRepositoryTests {
    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.4"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.host", mongoDBContainer::getHost);
        registry.add("spring.data.mongodb.port", () -> mongoDBContainer.getMappedPort(27017));
    }

    @SpyBean
    private WorkerRepository workerRepository;
    @Mock
    private WorkerConfiguration workerConfiguration;
    private WorkerService workerService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        workerService = new WorkerService(workerRepository, workerConfiguration);
    }

    /**
     * Try and add N tasks to a single worker at the same time.
     * If everything goes right, the Worker should finally have been assigned N tasks.
     */
    @Test
    void addMultipleTaskIds() {
        workerService.addWorker(
                Worker.builder()
                        .walletAddress(WALLET_WORKER_1)
                        .build()
        );

        final int nThreads = 10;
        final ExecutorService executor = Executors.newFixedThreadPool(nThreads);

        final List<Future<Optional<Worker>>> futures = IntStream.range(0, nThreads)
                .mapToObj(i -> executor.submit(() -> workerService.addChainTaskIdToWorker(new Date().getTime() + "", WALLET_WORKER_1)))
                .collect(Collectors.toList());

        Awaitility.await()
                .atMost(Duration.ofMinutes(1))
                .until(() -> futures.stream().map(Future::isDone).reduce(Boolean::logicalAnd).orElse(false));

        Assertions.assertThat(workerService.getWorker(WALLET_WORKER_1).get().getComputingChainTaskIds())
                .hasSize(nThreads);
    }
}
