/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.iexec.common.utils.DateTimeUtils.addMinutesToDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class WorkerServiceTests {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private WorkerConfiguration workerConfiguration;

    @InjectMocks
    private WorkerService workerService;


    @BeforeAll
    static void initRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
    }

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
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

        Assertions.assertThat(aliveWorkersGauge).isNotNull();
        Assertions.assertThat(aliveTotalCpuGauge).isNotNull();
        Assertions.assertThat(aliveAvailableCpuGauge).isNotNull();

        Assertions.assertThat(aliveWorkersGauge.value()).isZero();
        Assertions.assertThat(aliveTotalCpuGauge.value()).isZero();
        Assertions.assertThat(aliveAvailableCpuGauge.value()).isZero();

    }

    @Test
    void shouldGetWorker() {
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

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        Optional<Worker> foundWorker = workerService.getWorker(walletAddress);
        assertThat(foundWorker).contains(existingWorker);
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

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(Mockito.any())).thenReturn(newWorker);

        Worker addedWorker = workerService.addWorker(newWorker);
        assertThat(addedWorker).isNotEqualTo(existingWorker);
        assertThat(addedWorker.getId()).isEqualTo(existingWorker.getId());
    }

    @Test
    void shouldAddNewWorker() {
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        Worker worker = Worker.builder()
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .build();
        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.empty());
        when(workerRepository.save(Mockito.any())).thenReturn(worker);

        Worker addedWorker = workerService.addWorker(worker);
        // check that the save method was called once
        Mockito.verify(workerRepository, Mockito.times(1)).save(Mockito.any());
        assertThat(addedWorker.getName()).isEqualTo(worker.getName());
        assertThat(workerService.getWorkerStatsMap().get(walletAddress).getLastAliveDate()).isBefore(new Date());
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
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        Date oldLastAlive = new SimpleDateFormat("yyyy-MM-dd").parse("2018-01-01");
        Worker worker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .build();
        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(worker));

        // call
        workerService.updateLastAlive(walletAddress);

        // check object returned by the method
        Date now = new Date();
        long duration = now.getTime() - workerService.getWorkerStatsMap().get(walletAddress).getLastAliveDate().getTime();
        long diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
        assertThat(diffInSeconds).isZero();
    }

    // isWorkerAllowedToAskReplicate

    @Test
    void shouldWorkerBeAllowedToAskReplicate() {
        String wallet = "wallet";
        Worker worker = Worker.builder()
                .build();
        workerService.getWorkerStatsMap().computeIfAbsent(wallet, WorkerService.WorkerStats::new);
        when(workerRepository.findByWalletAddress(wallet)).thenReturn(Optional.of(worker));
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

    // addChainTaskIdToWorker

    @Test
    void shouldAddTaskIdToWorker() {
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .maxNbTasks(7)
                .participatingChainTaskIds(new ArrayList<>(Arrays.asList("task1", "task2")))
                .computingChainTaskIds(new ArrayList<>(Arrays.asList("task1", "task2")))
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(existingWorker)).thenReturn(existingWorker);

        Optional<Worker> addedWorker = workerService.addChainTaskIdToWorker("task3", walletAddress);
        assertThat(addedWorker).isPresent();
        Worker worker = addedWorker.get();
        assertThat(worker.getParticipatingChainTaskIds()).hasSize(3);
        assertThat(worker.getParticipatingChainTaskIds().get(2)).isEqualTo("task3");
        assertThat(worker.getComputingChainTaskIds()).hasSize(3);
        assertThat(worker.getComputingChainTaskIds().get(2)).isEqualTo("task3");
    }

    @Test
    void shouldNotAddTaskIdToWorkerSinceUnknownWorker() {
        when(workerRepository.findByWalletAddress(Mockito.anyString())).thenReturn(Optional.empty());
        Optional<Worker> addedWorker = workerService.addChainTaskIdToWorker("task1", "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248");
        assertThat(addedWorker).isEmpty();
    }

    @Test
    void shouldNotAddTaskIdToWorkerSinceCantAcceptMoreWorker() {
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(3)
                .maxNbTasks(2)
                .participatingChainTaskIds(new ArrayList<>(Arrays.asList("task1", "task2")))
                .computingChainTaskIds(new ArrayList<>(Arrays.asList("task1", "task2")))
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(existingWorker)).thenReturn(existingWorker);

        Optional<Worker> addedWorker = workerService.addChainTaskIdToWorker("task3", walletAddress);
        assertThat(addedWorker).isEmpty();
    }

    // getChainTaskIds

    @Test
    void shouldGetChainTaskIds() {
        String wallet = "wallet";
        List<String> list = List.of("t1", "t1");
        Worker worker = Worker.builder()
                .participatingChainTaskIds(list)
                .build();
        when(workerRepository.findByWalletAddress(wallet)).thenReturn(Optional.of(worker));

        assertThat(workerService.getChainTaskIds(wallet)).isEqualTo(list);
    }

    @Test
    void shouldGetEmptyChainTaskIdListSinceWorkerNotFound() {
        String wallet = "wallet";
        when(workerRepository.findByWalletAddress(wallet)).thenReturn(Optional.empty());
        assertThat(workerService.getChainTaskIds(wallet)).isEmpty();
    }

    // Computing task IDs

    @Test
    void shouldGetComputingTaskIds() {
        String wallet = "wallet";
        List<String> list = List.of("t1", "t1");
        Worker worker = Worker.builder()
                .computingChainTaskIds(list)
                .build();
        when(workerRepository.findByWalletAddress(wallet)).thenReturn(Optional.of(worker));

        assertThat(workerService.getComputingTaskIds(wallet)).isEqualTo(list);
    }

    @Test
    void shouldNotGetComputingTaskIdsSinceNoWorker() {
        String wallet = "wallet";
        when(workerRepository.findByWalletAddress(wallet)).thenReturn(Optional.empty());

        assertThat(workerService.getComputingTaskIds(wallet)).isEmpty();
    }

    // removeChainTaskIdFromWorker

    @Test
    void shouldRemoveTaskIdFromWorker() {
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .participatingChainTaskIds(new ArrayList<>(Arrays.asList("task1", "task2")))
                .computingChainTaskIds(new ArrayList<>(Arrays.asList("task1", "task2")))
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(existingWorker)).thenReturn(existingWorker);

        Optional<Worker> removedWorker = workerService.removeChainTaskIdFromWorker("task2", walletAddress);
        assertThat(removedWorker).isPresent();
        Worker worker = removedWorker.get();
        assertThat(worker.getParticipatingChainTaskIds()).hasSize(1);
        assertThat(worker.getParticipatingChainTaskIds().get(0)).isEqualTo("task1");
        assertThat(worker.getComputingChainTaskIds()).hasSize(1);
        assertThat(worker.getComputingChainTaskIds().get(0)).isEqualTo("task1");
    }

    @Test
    void shouldNotRemoveTaskIdWorkerNotFound() {
        when(workerRepository.findByWalletAddress(Mockito.anyString())).thenReturn(Optional.empty());
        Optional<Worker> addedWorker = workerService.removeChainTaskIdFromWorker("task1", "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248");
        assertThat(addedWorker).isEmpty();
    }

    @Test
    void shouldNotRemoveAnythingSinceTaskIdNotFound() {
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        List<String> participatingIds = new ArrayList<>(Arrays.asList("task1", "task2"));
        List<String> computingIds = new ArrayList<>(Arrays.asList("task1", "task2"));
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .participatingChainTaskIds(participatingIds)
                .computingChainTaskIds(computingIds)
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(existingWorker)).thenReturn(existingWorker);

        Optional<Worker> removedWorker = workerService.removeChainTaskIdFromWorker("dummyTaskId", walletAddress);
        assertThat(removedWorker).isPresent();
        Worker worker = removedWorker.get();
        assertThat(worker.getParticipatingChainTaskIds()).hasSize(2);
        assertThat(worker.getParticipatingChainTaskIds()).isEqualTo(participatingIds);

        assertThat(worker.getComputingChainTaskIds()).hasSize(2);
        assertThat(worker.getComputingChainTaskIds()).isEqualTo(computingIds);
    }

    @Test
    void shouldRemoveComputedChainTaskIdFromWorker() {
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        List<String> participatingIds = new ArrayList<>(Arrays.asList("task1", "task2"));
        List<String> computingIds = new ArrayList<>(Arrays.asList("task1", "task2"));
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .participatingChainTaskIds(participatingIds)
                .computingChainTaskIds(computingIds)
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(existingWorker)).thenReturn(existingWorker);

        Optional<Worker> removedWorker = workerService.removeComputedChainTaskIdFromWorker("task1", walletAddress);
        assertThat(removedWorker).isPresent();
        Worker worker = removedWorker.get();
        assertThat(worker.getParticipatingChainTaskIds()).hasSize(2);
        assertThat(worker.getParticipatingChainTaskIds()).isEqualTo(participatingIds);

        assertThat(worker.getComputingChainTaskIds()).hasSize(1);
        assertThat(worker.getComputingChainTaskIds().get(0)).isEqualTo("task2");
    }

    @Test
    void shouldNotRemoveComputedChainTaskIdFromWorkerSinceWorkerNotFound() {
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        List<String> participatingIds = new ArrayList<>(Arrays.asList("task1", "task2"));
        List<String> computingIds = new ArrayList<>(Arrays.asList("task1", "task2"));
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .participatingChainTaskIds(participatingIds)
                .computingChainTaskIds(computingIds)
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.empty());
        when(workerRepository.save(existingWorker)).thenReturn(existingWorker);

        Optional<Worker> removedWorker = workerService.removeComputedChainTaskIdFromWorker("task1", walletAddress);
        assertThat(removedWorker).isEmpty();
    }

    @Test
    void shouldNotRemoveComputedChainTaskIdFromWorkerSinceChainTaskIdNotFound() {
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        List<String> participatingIds = new ArrayList<>(Arrays.asList("task1", "task2"));
        List<String> computingIds = new ArrayList<>(Arrays.asList("task1", "task2"));
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .participatingChainTaskIds(participatingIds)
                .computingChainTaskIds(computingIds)
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(existingWorker)).thenReturn(existingWorker);

        Optional<Worker> removedWorker = workerService.removeComputedChainTaskIdFromWorker("dummyTaskId", walletAddress);
        assertThat(removedWorker).isPresent();
        Worker worker = removedWorker.get();
        assertThat(worker.getParticipatingChainTaskIds()).hasSize(2);
        assertThat(worker.getParticipatingChainTaskIds()).isEqualTo(participatingIds);

        assertThat(worker.getComputingChainTaskIds()).hasSize(2);
        assertThat(worker.getComputingChainTaskIds()).isEqualTo(computingIds);
    }

    @Test
    void shouldGetLostWorkers() {

        List<Worker> allWorkers = getDummyWorkers();

        List<Worker> lostWorkers = allWorkers.subList(1, 3);
        when(workerRepository.findByWalletAddressIn(Mockito.any())).thenReturn(lostWorkers);

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
        when(workerRepository.findByWalletAddressIn(Mockito.any())).thenReturn(aliveWorkers);

        List<Worker> claimedAliveWorkers = workerService.getAliveWorkers();

        // check the claimedAliveWorkers are actually the aliveWorkers
        assertThat(claimedAliveWorkers)
                .hasSize(1)
                .isEqualTo(aliveWorkers);
    }

    @Test
    void shouldNotFindAliveWorkers() {
        when(workerRepository.findByWalletAddressIn(Mockito.any())).thenReturn(Collections.emptyList());
        assertThat(workerService.getAliveWorkers()).isEmpty();
    }

    @Test
    void shouldAcceptMoreWorks() {
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";

        Worker worker = getDummyWorker(walletAddress,
                3,
                Arrays.asList("task1", "task2", "task3", "task4", "task5"),
                Arrays.asList("task1", "task3"));

        assertThat(workerService.canAcceptMoreWorks(worker)).isTrue();
    }

    @Test
    void shouldNotAcceptMoreWorksSinceSaturatedCpus() {
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";

        Worker worker = getDummyWorker(walletAddress,
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

        Worker worker1 = getDummyWorker("0x1",
                4,
                Arrays.asList("task1", "task2", "task3", "task4"),
                Arrays.asList("task1", "task3"));//2 CPUs available

        Worker worker2 = getDummyWorker("0x2",
                4,
                Arrays.asList("task1", "task2", "task3", "task4"),
                List.of("task1"));//3 CPUs available
        when(workerRepository.findByWalletAddressIn(any())).thenReturn(Arrays.asList(worker1, worker2));

        assertThat(workerService.getAliveAvailableCpu()).isEqualTo(5);
    }


    @Test
    void shouldGetZeroAvailableCpuIfWorkerAlreadyFull() {

        Worker worker1 = getDummyWorker("0x1",
                4,
                Arrays.asList("task1", "task2", "task3", "task4"),
                Arrays.asList("task1", "task2", "task3", "task4"));

        Worker worker2 = getDummyWorker("0x2",
                4,
                Arrays.asList("task1", "task2", "task3", "task4"),
                Arrays.asList("task1", "task2", "task3", "task4"));
        when(workerRepository.findByWalletAddressIn(any())).thenReturn(Arrays.asList(worker1, worker2));

        assertThat(workerService.getAliveAvailableCpu()).isZero();
    }

    @Test
    void shouldGetZeroAvailableCpuIfNoWorkerAlive() {
        when(workerRepository.findByWalletAddressIn(any())).thenReturn(Collections.emptyList());
        assertThat(workerService.getAliveAvailableCpu()).isZero();
    }

    // getAliveTotalCpu

    @Test
    void shouldGetTotalAliveCpu() {
        Worker worker1 = Worker.builder()
                .cpuNb(4)
                .computingChainTaskIds(List.of("T1", "T2", "T3"))
                .build();
        Worker worker2 = Worker.builder()
                .cpuNb(2)
                .computingChainTaskIds(List.of("T4"))
                .build();
        List<Worker> list = List.of(worker1, worker2);
        when(workerRepository.findByWalletAddressIn(any())).thenReturn(list);
        workerService.init();
        workerService.updateMetrics();

        assertThat(workerService.getAliveTotalCpu())
                .isEqualTo(worker1.getCpuNb() + worker2.getCpuNb());

        Gauge aliveWorkersGauge = Metrics.globalRegistry.find(WorkerService.METRIC_WORKERS_GAUGE).gauge();
        Gauge aliveTotalCpuGauge = Metrics.globalRegistry.find(WorkerService.METRIC_CPU_TOTAL_GAUGE).gauge();
        Gauge aliveAvailableCpuGauge = Metrics.globalRegistry.find(WorkerService.METRIC_CPU_AVAILABLE_GAUGE).gauge();

        Assertions.assertThat(aliveWorkersGauge).isNotNull();
        Assertions.assertThat(aliveTotalCpuGauge).isNotNull();
        Assertions.assertThat(aliveAvailableCpuGauge).isNotNull();

        Assertions.assertThat(aliveWorkersGauge.value()).isEqualTo(list.size());
        Assertions.assertThat(aliveTotalCpuGauge.value()).isEqualTo(worker1.getCpuNb() + worker2.getCpuNb());
        Assertions.assertThat(aliveAvailableCpuGauge.value()).isEqualTo(2);
    }

    // getAliveTotalGpu

    @Test
    void shouldGetTotalAliveGpu() {
        Worker worker1 = Worker.builder()
                .gpuEnabled(true)
                .build();
        Worker worker2 = Worker.builder()
                .gpuEnabled(false)
                .build();
        List<Worker> list = List.of(worker1, worker2);
        when(workerRepository.findByWalletAddressIn(any())).thenReturn(list);

        assertThat(workerService.getAliveTotalGpu()).isEqualTo(1);
    }

    // getAliveAvailableGpu

    @Test
    void shouldGetAliveAvailableGpu() {
        Worker worker1 = Worker.builder()
                .gpuEnabled(true)
                .computingChainTaskIds(List.of())
                .build();
        Worker worker2 = Worker.builder()
                .gpuEnabled(true)
                .computingChainTaskIds(List.of("t1"))
                .build();
        List<Worker> list = List.of(worker1, worker2);
        when(workerRepository.findByWalletAddressIn(any())).thenReturn(list);

        assertThat(workerService.getAliveAvailableGpu()).isEqualTo(1);
    }
}
