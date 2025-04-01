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

package com.iexec.core.worker;

import com.iexec.core.configuration.WorkerConfiguration;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.when;

@DataMongoTest
@TestPropertySource(properties = {"mongock.enabled=false"})
@Testcontainers
@ExtendWith(MockitoExtension.class)
class WorkerServiceTests {

    private static final String WORKER1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private static final String WORKER2 = "0x2ab2674aa374fe6415d11f0a8fcbd8027fc1e6a9";

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
    private WorkerRepository workerRepository;

    @Mock
    private WorkerConfiguration workerConfiguration;

    private WorkerService workerService;

    private final String workerName = "worker";
    private final Worker existingWorker = Worker.builder()
            .id("1")
            .name(workerName)
            .walletAddress(WORKER1)
            .os("Linux")
            .cpu("x86")
            .cpuNb(8)
            .participatingChainTaskIds(List.of("task1", "task2"))
            .computingChainTaskIds(List.of("task1", "task2"))
            .build();

    @BeforeAll
    static void initRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
    }

    @BeforeEach
    void init() {
        workerService = new WorkerService(mongoTemplate, workerRepository, workerConfiguration);
        workerRepository.deleteAll();
    }

    @AfterEach
    void afterEach() {
        Metrics.globalRegistry.clear();
    }

    // getWorker

    @Test
    void shouldReturnZeroForAllCountersWhereNothingHasAppended() {
        Gauge aliveWorkersGauge = Metrics.globalRegistry.find(WorkerService.METRIC_WORKERS_GAUGE).gauge();
        Gauge aliveComputingCpuGauge = Metrics.globalRegistry.find(WorkerService.METRIC_CPU_COMPUTING_GAUGE).gauge();
        Gauge aliveRegisteredCpuGauge = Metrics.globalRegistry.find(WorkerService.METRIC_CPU_REGISTERED_GAUGE).gauge();

        assertAll(
                () -> assertThat(aliveWorkersGauge)
                        .isNotNull()
                        .extracting(Gauge::value).isEqualTo(0.0),
                () -> assertThat(aliveComputingCpuGauge)
                        .isNotNull()
                        .extracting(Gauge::value).isEqualTo(0.0),
                () -> assertThat(aliveRegisteredCpuGauge)
                        .isNotNull()
                        .extracting(Gauge::value).isEqualTo(0.0)
        );
    }

    @Test
    void shouldGetWorker() {
        final Worker worker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(WORKER1)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .build();

        workerRepository.save(worker);
        Optional<Worker> foundWorker = workerService.getWorker(WORKER1);
        assertThat(foundWorker)
                .usingRecursiveComparison()
                .isEqualTo(Optional.of(worker));
    }

    // addWorker

    @Test
    void shouldNotAddNewWorker() {
        final Worker newWorker = Worker.builder()
                .name(workerName)
                .walletAddress(WORKER1)
                .os("otherOS")
                .cpu("otherCpu")
                .cpuNb(8)
                .build();

        workerRepository.save(existingWorker);

        final Worker addedWorker = workerService.addWorker(newWorker);
        assertThat(addedWorker).isNotEqualTo(existingWorker);
        assertThat(addedWorker.getId()).isEqualTo(existingWorker.getId());
    }

    @Test
    void shouldAddNewWorker() {
        final Worker worker = Worker.builder()
                .name(workerName)
                .walletAddress(WORKER1)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .build();

        assertThat(workerRepository.count()).isZero();
        Worker addedWorker = workerService.addWorker(worker);
        // check that the save method was called once
        assertThat(workerRepository.count()).isOne();
        assertThat(addedWorker.getName()).isEqualTo(worker.getName());
        assertThat(workerService.getWorkerStatsMap().get(WORKER1).getLastAliveDate()).isBefore(new Date());
    }

    // isAllowedToJoin

    @Test
    void shouldBeAllowed() {
        List<String> whiteList = List.of("w1", "w2");
        when(workerConfiguration.getWhitelist()).thenReturn(whiteList);
        assertThat(workerService.isAllowedToJoin("w1")).isTrue();
    }

    @Test
    void shouldBeAllowedWhenNoWhiteList() {
        when(workerConfiguration.getWhitelist()).thenReturn(List.of());
        assertThat(workerService.isAllowedToJoin("w1")).isTrue();
    }

    @Test
    void shouldNotBeAllowed() {
        List<String> whiteList = List.of("w1", "w2");
        when(workerConfiguration.getWhitelist()).thenReturn(whiteList);
        assertThat(workerService.isAllowedToJoin("w3")).isFalse();
    }

    // updateLastAlive

    @Test
    void shouldUpdateLastAlive() {
        // init
        Worker worker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(WORKER1)
                .build();
        workerRepository.save(worker);

        // call
        workerService.updateLastAlive(WORKER1);

        // check object returned by the method
        Date now = new Date();
        long duration = now.getTime() - workerService.getWorkerStatsMap().get(WORKER1).getLastAliveDate().getTime();
        long diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
        assertThat(diffInSeconds).isZero();
    }

    // isWorkerAllowedToAskReplicate

    @Test
    void shouldWorkerBeAllowedToAskReplicate() {
        String wallet = "wallet";
        Worker worker = Worker.builder()
                .walletAddress(wallet)
                .build();
        workerRepository.save(worker);
        workerService.getWorkerStatsMap().computeIfAbsent(wallet, WorkerService.WorkerStats::new)
                .setLastReplicateDemandDate(Date.from(Instant.now().minusSeconds(10L)));
        when(workerConfiguration.getAskForReplicatePeriod()).thenReturn(5000L);

        assertThat(workerService.isWorkerAllowedToAskReplicate(wallet)).isTrue();
    }

    @Test
    void shouldWorkerBeAllowedToAskReplicateSinceFirstTime() {
        String wallet = "wallet";
        workerService.getWorkerStatsMap().computeIfAbsent(wallet, WorkerService.WorkerStats::new);

        assertThat(workerService.isWorkerAllowedToAskReplicate(wallet)).isTrue();
    }

    @Test
    void shouldWorkerNotBeAllowedToAskReplicateSinceTooSoon() {
        String wallet = "wallet";
        workerService.getWorkerStatsMap().computeIfAbsent(wallet, WorkerService.WorkerStats::new)
                .setLastReplicateDemandDate(Date.from(Instant.now().minusSeconds(1)));
        when(workerConfiguration.getAskForReplicatePeriod()).thenReturn(5000L);

        assertThat(workerService.isWorkerAllowedToAskReplicate(wallet)).isFalse();
    }

    // updateLastReplicateDemandDate

    @Test
    void shouldUpdateLastReplicateDemand() {
        String wallet = "wallet";
        Date lastDate = Date.from(Instant.now().minusSeconds(1));
        workerService.getWorkerStatsMap().computeIfAbsent(wallet, WorkerService.WorkerStats::new)
                .setLastReplicateDemandDate(lastDate);

        workerService.updateLastReplicateDemandDate(wallet);
        assertThat(workerService.getWorkerStatsMap()
                .get(wallet)
                .getLastReplicateDemandDate())
                .isAfter(lastDate);
    }

    // region addChainTaskIdToWorker
    @Test
    void shouldAddTaskIdToWorker() {
        final Worker worker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(WORKER1)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .maxNbTasks(7)
                .participatingChainTaskIds(List.of("task1", "task2"))
                .computingChainTaskIds(List.of("task1", "task2"))
                .build();

        workerRepository.save(worker);

        final Worker foundWorker = workerService.addChainTaskIdToWorker("task3", WORKER1).orElse(null);
        assertThat(foundWorker).isNotNull();
        assertThat(foundWorker.getParticipatingChainTaskIds()).hasSize(3);
        assertThat(foundWorker.getParticipatingChainTaskIds().get(2)).isEqualTo("task3");
        assertThat(foundWorker.getComputingChainTaskIds()).hasSize(3);
        assertThat(foundWorker.getComputingChainTaskIds().get(2)).isEqualTo("task3");
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
                        .walletAddress(WORKER1)
                        .maxNbTasks(nThreads)
                        .build()
        );

        final ExecutorService executor = Executors.newFixedThreadPool(nThreads);

        final List<Future<Optional<Worker>>> futures = IntStream.range(0, nThreads)
                .mapToObj(i -> executor.submit(() -> workerService.addChainTaskIdToWorker(new Date().getTime() + "", WORKER1)))
                .toList();

        Awaitility.await()
                .atMost(Duration.ofMinutes(1))
                .until(() -> futures.stream().map(Future::isDone).reduce(Boolean::logicalAnd).orElse(false));

        assertThat(workerService.getWorker(WORKER1).get().getComputingChainTaskIds())
                .hasSize(nThreads);
    }

    @Test
    void shouldNotAddTaskIdToWorkerSinceUnknownWorker() {
        Optional<Worker> addedWorker = workerService.addChainTaskIdToWorker("task1", WORKER1);
        assertThat(addedWorker).isEmpty();
    }

    @Test
    void shouldNotAddTaskIdToWorkerSinceCantAcceptMoreWorker() {
        final Worker worker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(WORKER1)
                .os("Linux")
                .cpu("x86")
                .cpuNb(3)
                .maxNbTasks(2)
                .participatingChainTaskIds(List.of("task1", "task2"))
                .computingChainTaskIds(List.of("task1", "task2"))
                .build();

        workerRepository.save(worker);

        assertThat(workerService.addChainTaskIdToWorker("task3", WORKER1)).isEmpty();
    }
    //

    // getChainTaskIds

    @Test
    void shouldGetChainTaskIds() {
        List<String> list = List.of("t1", "t1");
        Worker worker = Worker.builder()
                .walletAddress(WORKER1)
                .participatingChainTaskIds(list)
                .build();
        workerRepository.save(worker);
        assertThat(workerService.getChainTaskIds(WORKER1)).isEqualTo(list);
    }

    @Test
    void shouldGetEmptyChainTaskIdListSinceWorkerNotFound() {
        String wallet = "wallet";
        assertThat(workerService.getChainTaskIds(wallet)).isEmpty();
    }

    // region getComputingTaskIds
    @Test
    void shouldGetComputingTaskIds() {
        List<String> list = List.of("t1", "t1");
        Worker worker = Worker.builder()
                .walletAddress(WORKER1)
                .computingChainTaskIds(list)
                .build();
        workerRepository.save(worker);
        assertThat(workerService.getComputingTaskIds(WORKER1)).isEqualTo(list);
    }

    @Test
    void shouldNotGetComputingTaskIdsSinceNoWorker() {
        assertThat(workerService.getComputingTaskIds(WORKER2)).isEmpty();
    }
    // endregion

    // region removeChainTaskIdFromWorker
    @Test
    void shouldRemoveTaskIdFromWorker() {
        workerRepository.save(existingWorker);

        final Worker worker = workerService.removeChainTaskIdFromWorker("task2", WORKER1).orElseThrow();
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

        final Worker worker = workerService.removeChainTaskIdFromWorker("dummyTaskId", WORKER1).orElseThrow();
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

        final Worker worker = workerService.removeComputedChainTaskIdFromWorker("task1", WORKER1).orElseThrow();
        assertThat(worker.getParticipatingChainTaskIds()).hasSize(2);
        assertThat(worker.getParticipatingChainTaskIds()).isEqualTo(List.of("task1", "task2"));

        assertThat(worker.getComputingChainTaskIds()).hasSize(1);
        assertThat(worker.getComputingChainTaskIds().get(0)).isEqualTo("task2");
    }

    @Test
    void shouldNotRemoveComputedChainTaskIdFromWorkerSinceWorkerNotFound() {
        Optional<Worker> removedWorker = workerService.removeComputedChainTaskIdFromWorker("task1", WORKER1);
        assertThat(removedWorker).isEmpty();
    }

    @Test
    void shouldNotRemoveComputedChainTaskIdFromWorkerSinceChainTaskIdNotFound() {
        workerRepository.save(existingWorker);

        final Worker worker = workerService.removeComputedChainTaskIdFromWorker("dummyTaskId", WORKER1).orElseThrow();
        assertThat(worker.getParticipatingChainTaskIds()).hasSize(2);
        assertThat(worker.getParticipatingChainTaskIds()).isEqualTo(List.of("task1", "task2"));

        assertThat(worker.getComputingChainTaskIds()).hasSize(2);
        assertThat(worker.getComputingChainTaskIds()).isEqualTo(List.of("task1", "task2"));
    }
    // endregion

    @Test
    void shouldGetLostWorkers() {
        List<Worker> allWorkers = getDummyWorkers();

        List<Worker> lostWorkers = allWorkers.subList(1, 3);
        workerRepository.saveAll(allWorkers);

        List<Worker> claimedLostWorkers = workerService.getLostWorkers();

        // check the claimedLostWorkers are actually the lostWorkers
        assertThat(claimedLostWorkers)
                .hasSize(2)
                .isEqualTo(lostWorkers);
    }

    @Test
    void shouldNotFindLostWorkers() {
        assertThat(workerService.getLostWorkers()).isEmpty();
    }

    @Test
    void shouldGetAliveWorkers() {
        List<Worker> allWorkers = getDummyWorkers();
        List<Worker> aliveWorkers = allWorkers.subList(0, 1);
        workerRepository.saveAll(allWorkers);

        List<Worker> claimedAliveWorkers = workerService.getAliveWorkers();

        // check the claimedAliveWorkers are actually the aliveWorkers
        assertThat(claimedAliveWorkers)
                .hasSize(1)
                .isEqualTo(aliveWorkers);
    }

    @Test
    void shouldNotFindAliveWorkers() {
        assertThat(workerService.getAliveWorkers()).isEmpty();
    }

    @Test
    void shouldAcceptMoreWorks() {
        Worker worker = getDummyWorker(WORKER1,
                3,
                List.of("task1", "task2", "task3", "task4", "task5"),
                List.of("task1", "task3"));

        assertThat(workerService.canAcceptMoreWorks(worker)).isTrue();
    }

    @Test
    void shouldNotAcceptMoreWorksSinceSaturatedCpus() {
        Worker worker = getDummyWorker(WORKER1,
                2,
                List.of("task1", "task2", "task3", "task4"),
                List.of("task1", "task3"));

        assertThat(workerService.canAcceptMoreWorks(worker)).isFalse();
    }

    List<Worker> getDummyWorkers() {
        workerService.getWorkerStatsMap().computeIfAbsent("address1", WorkerService.WorkerStats::new)
                .setLastAliveDate(Date.from(Instant.now()));
        workerService.getWorkerStatsMap().computeIfAbsent("address2", WorkerService.WorkerStats::new)
                .setLastAliveDate(Date.from(Instant.now().minus(2L, ChronoUnit.MINUTES)));
        workerService.getWorkerStatsMap().computeIfAbsent("address3", WorkerService.WorkerStats::new)
                .setLastAliveDate(Date.from(Instant.now().minus(3L, ChronoUnit.MINUTES)));
        return List.of(
                Worker.builder().id("1").walletAddress("address1").build(),
                Worker.builder().id("2").walletAddress("address2").build(),
                Worker.builder().id("3").walletAddress("address3").build()
        );
    }

    Worker getDummyWorker(String walletAddress, int cpuNb, List<String> participatingIds, List<String> computingIds) {
        return Worker.builder()
                .walletAddress(walletAddress)
                .cpuNb(cpuNb)
                .maxNbTasks(cpuNb)
                .gpuEnabled(false)
                .participatingChainTaskIds(participatingIds)
                .computingChainTaskIds(computingIds)
                .build();
    }

    // region updateMetrics
    @Test
    void shouldGetSomeAvailableCpu() {
        final Worker worker1 = getDummyWorker(WORKER1,
                4,
                List.of("task1", "task2", "task3", "task4"),
                List.of("task1", "task3"));//2 CPUs available

        final Worker worker2 = getDummyWorker(WORKER2,
                4,
                List.of("task1", "task2", "task3", "task4"),
                List.of("task1"));//3 CPUs available
        workerRepository.saveAll(List.of(worker1, worker2));
        workerService.updateLastAlive(WORKER1);
        workerService.updateLastAlive(WORKER2);

        workerService.updateMetrics();
        assertThat(workerService.getAliveWorkerMetrics().aliveComputingCpu()).isEqualTo(3);
    }

    @Test
    void shouldGetZeroAvailableCpuIfWorkerAlreadyFull() {
        final Worker worker1 = getDummyWorker(WORKER1,
                4,
                List.of("task1", "task2", "task3", "task4"),
                List.of("task1", "task2", "task3", "task4"));

        final Worker worker2 = getDummyWorker(WORKER2,
                4,
                List.of("task1", "task2", "task3", "task4"),
                List.of("task1", "task2", "task3", "task4"));
        workerRepository.saveAll(List.of(worker1, worker2));
        workerService.updateLastAlive(WORKER1);
        workerService.updateLastAlive(WORKER2);

        workerService.updateMetrics();
        assertThat(workerService.getAliveWorkerMetrics().aliveComputingCpu()).isEqualTo(8);
    }

    @Test
    void shouldGetZeroAvailableCpuIfNoWorkerAlive() {
        assertThat(workerService.getAliveWorkerMetrics().aliveComputingCpu()).isZero();
    }

    @Test
    void shouldGetTotalAliveCpu() {
        final Worker worker1 = Worker.builder()
                .walletAddress(WORKER1)
                .cpuNb(4)
                .computingChainTaskIds(List.of("T1", "T2", "T3"))
                .build();
        final Worker worker2 = Worker.builder()
                .walletAddress(WORKER2)
                .cpuNb(2)
                .computingChainTaskIds(List.of("T4"))
                .build();
        workerRepository.saveAll(List.of(worker1, worker2));
        workerService.updateLastAlive(WORKER1);
        workerService.updateLastAlive(WORKER2);
        workerService.updateMetrics();

        assertThat(workerService.getAliveWorkerMetrics().aliveRegisteredCpu())
                .isEqualTo(worker1.getCpuNb() + worker2.getCpuNb());

        Gauge aliveWorkersGauge = Metrics.globalRegistry.find(WorkerService.METRIC_WORKERS_GAUGE).gauge();
        Gauge aliveComputingCpuGauge = Metrics.globalRegistry.find(WorkerService.METRIC_CPU_COMPUTING_GAUGE).gauge();
        Gauge aliveRegisteredCpuGauge = Metrics.globalRegistry.find(WorkerService.METRIC_CPU_REGISTERED_GAUGE).gauge();

        assertAll(
                () -> assertThat(aliveWorkersGauge)
                        .isNotNull()
                        .extracting(Gauge::value).isEqualTo(2.0),
                () -> assertThat(aliveRegisteredCpuGauge)
                        .isNotNull()
                        .extracting(Gauge::value).isEqualTo((double) worker1.getCpuNb() + worker2.getCpuNb()),
                () -> assertThat(aliveComputingCpuGauge)
                        .isNotNull()
                        .extracting(Gauge::value).isEqualTo(4.0)
        );
    }

    @Test
    void shouldGetTotalAliveGpu() {
        final Date now = new Date();
        final Worker worker1 = Worker.builder()
                .walletAddress(WORKER1)
                .gpuEnabled(true)
                .lastAliveDate(now)
                .build();
        final Worker worker2 = Worker.builder()
                .walletAddress(WORKER2)
                .gpuEnabled(false)
                .lastAliveDate(now)
                .build();
        workerRepository.saveAll(List.of(worker1, worker2));
        workerService.updateLastAlive(WORKER1);
        workerService.updateLastAlive(WORKER2);

        workerService.updateMetrics();
        assertThat(workerService.getAliveWorkerMetrics().aliveComputingGpu()).isZero();
        assertThat(workerService.getAliveWorkerMetrics().aliveRegisteredGpu()).isOne();
    }

    @Test
    void shouldGetAliveAvailableGpu() {
        final Worker worker1 = Worker.builder()
                .walletAddress(WORKER1)
                .gpuEnabled(true)
                .computingChainTaskIds(List.of())
                .build();
        final Worker worker2 = Worker.builder()
                .walletAddress(WORKER2)
                .gpuEnabled(true)
                .computingChainTaskIds(List.of("t1"))
                .build();
        workerRepository.saveAll(List.of(worker1, worker2));
        workerService.updateLastAlive(WORKER1);
        workerService.updateLastAlive(WORKER2);

        workerService.updateMetrics();
        assertThat(workerService.getAliveWorkerMetrics().aliveComputingGpu()).isOne();
        assertThat(workerService.getAliveWorkerMetrics().aliveRegisteredGpu()).isEqualTo(2);
    }
    // endregion
}
