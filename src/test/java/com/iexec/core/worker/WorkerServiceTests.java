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
import org.mockito.Mock;
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

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.iexec.common.utils.DateTimeUtils.addMinutesToDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.when;

@DataMongoTest
@Testcontainers
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

    private final Worker existingWorker = Worker.builder()
            .id("1")
            .name("worker1")
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
        MockitoAnnotations.openMocks(this);
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
        workerService.init();
        Gauge aliveWorkersGauge = Metrics.globalRegistry.find(WorkerService.METRIC_WORKERS_GAUGE).gauge();
        Gauge aliveTotalCpuGauge = Metrics.globalRegistry.find(WorkerService.METRIC_CPU_TOTAL_GAUGE).gauge();
        Gauge aliveAvailableCpuGauge = Metrics.globalRegistry.find(WorkerService.METRIC_CPU_AVAILABLE_GAUGE).gauge();

        assertAll(
                () -> assertThat(aliveWorkersGauge)
                        .isNotNull()
                        .extracting(Gauge::value).isEqualTo(0.0),
                () -> assertThat(aliveTotalCpuGauge)
                        .isNotNull()
                        .extracting(Gauge::value).isEqualTo(0.0),
                () -> assertThat(aliveAvailableCpuGauge)
                        .isNotNull()
                        .extracting(Gauge::value).isEqualTo(0.0)
        );
    }

    @Test
    void shouldGetWorker() {
        String workerName = "worker1";
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(WORKER1)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .build();

        workerRepository.save(existingWorker);
        Optional<Worker> foundWorker = workerService.getWorker(WORKER1);
        assertThat(foundWorker)
                .usingRecursiveComparison()
                .isEqualTo(Optional.of(existingWorker));
    }

    // addWorker

    @Test
    void shouldNotAddNewWorker() {
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .build();

        Worker newWorker = Worker.builder()
                .name(workerName)
                .walletAddress(walletAddress)
                .os("otherOS")
                .cpu("otherCpu")
                .cpuNb(8)
                .build();

        workerRepository.save(existingWorker);

        Worker addedWorker = workerService.addWorker(newWorker);
        assertThat(addedWorker).isNotEqualTo(existingWorker);
        assertThat(addedWorker.getId()).isEqualTo(existingWorker.getId());
    }

    @Test
    void shouldAddNewWorker() {
        String workerName = "worker1";
        Worker worker = Worker.builder()
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
    void shouldUpdateLastAlive() throws ParseException {
        // init
        String workerName = "worker1";
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
        workerService.getWorkerStatsMap().computeIfAbsent(wallet, WorkerService.WorkerStats::new);
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
        String workerName = "worker1";
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(WORKER1)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .maxNbTasks(7)
                .participatingChainTaskIds(new ArrayList<>(Arrays.asList("task1", "task2")))
                .computingChainTaskIds(new ArrayList<>(Arrays.asList("task1", "task2")))
                .build();

        workerRepository.save(existingWorker);

        Optional<Worker> addedWorker = workerService.addChainTaskIdToWorker("task3", WORKER1);
        assertThat(addedWorker).isPresent();
        Worker worker = addedWorker.get();
        assertThat(worker.getParticipatingChainTaskIds()).hasSize(3);
        assertThat(worker.getParticipatingChainTaskIds().get(2)).isEqualTo("task3");
        assertThat(worker.getComputingChainTaskIds()).hasSize(3);
        assertThat(worker.getComputingChainTaskIds().get(2)).isEqualTo("task3");
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
                .collect(Collectors.toList());

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
        String workerName = "worker1";
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(WORKER1)
                .os("Linux")
                .cpu("x86")
                .cpuNb(3)
                .maxNbTasks(2)
                .participatingChainTaskIds(new ArrayList<>(Arrays.asList("task1", "task2")))
                .computingChainTaskIds(new ArrayList<>(Arrays.asList("task1", "task2")))
                .build();

        workerRepository.save(existingWorker);

        Optional<Worker> addedWorker = workerService.addChainTaskIdToWorker("task3", WORKER1);
        assertThat(addedWorker).isEmpty();
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
                Arrays.asList("task1", "task2", "task3", "task4", "task5"),
                Arrays.asList("task1", "task3"));

        assertThat(workerService.canAcceptMoreWorks(worker)).isTrue();
    }

    @Test
    void shouldNotAcceptMoreWorksSinceSaturatedCpus() {
        Worker worker = getDummyWorker(WORKER1,
                2,
                Arrays.asList("task1", "task2", "task3", "task4"),
                Arrays.asList("task1", "task3"));

        assertThat(workerService.canAcceptMoreWorks(worker)).isFalse();
    }

    List<Worker> getDummyWorkers() {
        workerService.getWorkerStatsMap().computeIfAbsent("address1", WorkerService.WorkerStats::new).setLastAliveDate(new Date());
        workerService.getWorkerStatsMap().computeIfAbsent("address2", WorkerService.WorkerStats::new).setLastAliveDate(addMinutesToDate(new Date(), -2));
        workerService.getWorkerStatsMap().computeIfAbsent("address3", WorkerService.WorkerStats::new).setLastAliveDate(addMinutesToDate(new Date(), -3));
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


    @Test
    void shouldGetSomeAvailableCpu() {
        Worker worker1 = getDummyWorker(WORKER1,
                4,
                Arrays.asList("task1", "task2", "task3", "task4"),
                Arrays.asList("task1", "task3"));//2 CPUs available

        Worker worker2 = getDummyWorker(WORKER2,
                4,
                Arrays.asList("task1", "task2", "task3", "task4"),
                List.of("task1"));//3 CPUs available
        workerRepository.saveAll(List.of(worker1, worker2));
        workerService.updateLastAlive(WORKER1);
        workerService.updateLastAlive(WORKER2);

        assertThat(workerService.getAliveAvailableCpu()).isEqualTo(5);
    }


    @Test
    void shouldGetZeroAvailableCpuIfWorkerAlreadyFull() {
        Worker worker1 = getDummyWorker(WORKER1,
                4,
                Arrays.asList("task1", "task2", "task3", "task4"),
                Arrays.asList("task1", "task2", "task3", "task4"));

        Worker worker2 = getDummyWorker(WORKER2,
                4,
                Arrays.asList("task1", "task2", "task3", "task4"),
                Arrays.asList("task1", "task2", "task3", "task4"));
        workerRepository.saveAll(List.of(worker1, worker2));
        workerService.updateLastAlive(WORKER1);
        workerService.updateLastAlive(WORKER2);

        assertThat(workerService.getAliveAvailableCpu()).isZero();
    }

    @Test
    void shouldGetZeroAvailableCpuIfNoWorkerAlive() {
        assertThat(workerService.getAliveAvailableCpu()).isZero();
    }

    // getAliveTotalCpu

    @Test
    void shouldGetTotalAliveCpu() {
        Worker worker1 = Worker.builder()
                .walletAddress(WORKER1)
                .cpuNb(4)
                .computingChainTaskIds(List.of("T1", "T2", "T3"))
                .build();
        Worker worker2 = Worker.builder()
                .walletAddress(WORKER2)
                .cpuNb(2)
                .computingChainTaskIds(List.of("T4"))
                .build();
        List<Worker> list = List.of(worker1, worker2);
        workerRepository.saveAll(list);
        workerService.updateLastAlive(WORKER1);
        workerService.updateLastAlive(WORKER2);
        workerService.init();
        workerService.updateMetrics();

        assertThat(workerService.getAliveTotalCpu())
                .isEqualTo(worker1.getCpuNb() + worker2.getCpuNb());

        Gauge aliveWorkersGauge = Metrics.globalRegistry.find(WorkerService.METRIC_WORKERS_GAUGE).gauge();
        Gauge aliveTotalCpuGauge = Metrics.globalRegistry.find(WorkerService.METRIC_CPU_TOTAL_GAUGE).gauge();
        Gauge aliveAvailableCpuGauge = Metrics.globalRegistry.find(WorkerService.METRIC_CPU_AVAILABLE_GAUGE).gauge();

        assertAll(
                () -> assertThat(aliveWorkersGauge)
                        .isNotNull()
                        .extracting(Gauge::value).isEqualTo((double) list.size()),
                () -> assertThat(aliveTotalCpuGauge)
                        .isNotNull()
                        .extracting(Gauge::value).isEqualTo((double) worker1.getCpuNb() + worker2.getCpuNb()),
                () -> assertThat(aliveAvailableCpuGauge)
                        .isNotNull()
                        .extracting(Gauge::value).isEqualTo(2.0)
        );
    }

    // getAliveTotalGpu

    @Test
    void shouldGetTotalAliveGpu() {
        final Date now = new Date();
        Worker worker1 = Worker.builder()
                .walletAddress(WORKER1)
                .gpuEnabled(true)
                .lastAliveDate(now)
                .build();
        Worker worker2 = Worker.builder()
                .walletAddress(WORKER2)
                .gpuEnabled(false)
                .lastAliveDate(now)
                .build();
        List<Worker> list = List.of(worker1, worker2);
        workerRepository.saveAll(list);
        workerService.updateLastAlive(WORKER1);
        workerService.updateLastAlive(WORKER2);

        assertThat(workerService.getAliveTotalGpu()).isEqualTo(1);
    }

    // getAliveAvailableGpu

    @Test
    void shouldGetAliveAvailableGpu() {
        Worker worker1 = Worker.builder()
                .walletAddress(WORKER1)
                .gpuEnabled(true)
                .computingChainTaskIds(List.of())
                .build();
        Worker worker2 = Worker.builder()
                .walletAddress(WORKER2)
                .gpuEnabled(true)
                .computingChainTaskIds(List.of("t1"))
                .build();
        List<Worker> list = List.of(worker1, worker2);
        workerRepository.saveAll(list);
        workerService.updateLastAlive(WORKER1);
        workerService.updateLastAlive(WORKER2);

        assertThat(workerService.getAliveAvailableGpu()).isEqualTo(1);
    }
}
