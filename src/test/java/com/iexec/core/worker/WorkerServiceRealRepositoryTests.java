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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.iexec.commons.poco.utils.TestUtils.WALLET_WORKER_1;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DataMongoTest
@Testcontainers
class WorkerServiceRealRepositoryTests {

    private static final String WORKER_NAME = "worker1";
    private static final String WALLET_ADDRESS = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.4"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.host", mongoDBContainer::getHost);
        registry.add("spring.data.mongodb.port", () -> mongoDBContainer.getMappedPort(27017));
    }

    @SpyBean
    private MongoTemplate mongoTemplate;
    @SpyBean
    private WorkerRepository workerRepository;
    @Mock
    private WorkerConfiguration workerConfiguration;
    private WorkerService workerService;

    private final Worker existingWorker = Worker.builder()
            .id("1")
            .name(WORKER_NAME)
            .walletAddress(WALLET_ADDRESS)
            .os("Linux")
            .cpu("x86")
            .cpuNb(8)
            .participatingChainTaskIds(new ArrayList<>(Arrays.asList("task1", "task2")))
            .computingChainTaskIds(new ArrayList<>(Arrays.asList("task1", "task2")))
            .build();

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        workerService = new WorkerService(mongoTemplate, workerRepository, workerConfiguration);
        workerRepository.deleteAll();
    }

    /**
     * Try and add N tasks to a single worker at the same time.
     * If everything goes right, the Worker should finally have been assigned N tasks.
     */
    @Test
    void addMultipleTaskIds() {
        final int nThreads = 10;
        workerService.addWorker(
                Worker.builder()
                        .walletAddress(WALLET_WORKER_1)
                        .maxNbTasks(nThreads)
                        .build()
        );

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

    // region removeChainTaskIdFromWorker
    @Test
    void shouldRemoveTaskIdFromWorker() {
        workerRepository.save(existingWorker);

        Optional<Worker> removedWorker = workerService.removeChainTaskIdFromWorker("task2", WALLET_ADDRESS);
        assertThat(removedWorker).isPresent();
        Worker worker = removedWorker.get();
        assertThat(worker.getParticipatingChainTaskIds()).hasSize(1);
        assertThat(worker.getParticipatingChainTaskIds().get(0)).isEqualTo("task1");
        assertThat(worker.getComputingChainTaskIds()).hasSize(1);
        assertThat(worker.getComputingChainTaskIds().get(0)).isEqualTo("task1");
    }

    @Test
    void shouldNotRemoveTaskIdWorkerNotFound() {
        Optional<Worker> addedWorker = workerService.removeChainTaskIdFromWorker("task1", "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248");
        assertThat(addedWorker).isEmpty();
    }

    @Test
    void shouldNotRemoveAnythingSinceTaskIdNotFound() {
        workerRepository.save(existingWorker);

        Optional<Worker> removedWorker = workerService.removeChainTaskIdFromWorker("dummyTaskId", WALLET_ADDRESS);
        assertThat(removedWorker).isPresent();
        Worker worker = removedWorker.get();
        assertThat(worker.getParticipatingChainTaskIds()).hasSize(2);
        assertThat(worker.getParticipatingChainTaskIds()).isEqualTo(List.of("task1", "task2"));

        assertThat(worker.getComputingChainTaskIds()).hasSize(2);
        assertThat(worker.getComputingChainTaskIds()).isEqualTo(List.of("task1", "task2"));
    }
    // endregion

    // region removeComputedChainTaskIdFromWorker
    @Test
    void shouldRemoveComputedChainTaskIdFromWorker() {
        workerRepository.save(existingWorker);

        Optional<Worker> removedWorker = workerService.removeComputedChainTaskIdFromWorker("task1", WALLET_ADDRESS);
        assertThat(removedWorker).isPresent();
        Worker worker = removedWorker.get();
        assertThat(worker.getParticipatingChainTaskIds()).hasSize(2);
        assertThat(worker.getParticipatingChainTaskIds()).isEqualTo(List.of("task1", "task2"));

        assertThat(worker.getComputingChainTaskIds()).hasSize(1);
        assertThat(worker.getComputingChainTaskIds().get(0)).isEqualTo("task2");
    }

    @Test
    void shouldNotRemoveComputedChainTaskIdFromWorkerSinceWorkerNotFound() {
        Optional<Worker> removedWorker = workerService.removeComputedChainTaskIdFromWorker("task1", WALLET_ADDRESS);
        assertThat(removedWorker).isEmpty();
    }

    @Test
    void shouldNotRemoveComputedChainTaskIdFromWorkerSinceChainTaskIdNotFound() {
        List<String> participatingIds = List.of("task1", "task2");
        List<String> computingIds = List.of("task1", "task2");

        workerRepository.save(existingWorker);

        Optional<Worker> removedWorker = workerService.removeComputedChainTaskIdFromWorker("dummyTaskId", WALLET_ADDRESS);
        assertThat(removedWorker).isPresent();
        Worker worker = removedWorker.get();
        assertThat(worker.getParticipatingChainTaskIds()).hasSize(2);
        assertThat(worker.getParticipatingChainTaskIds()).isEqualTo(participatingIds);

        assertThat(worker.getComputingChainTaskIds()).hasSize(2);
        assertThat(worker.getComputingChainTaskIds()).isEqualTo(computingIds);
    }
    // endregion
}
