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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class WorkerServiceTests {

    @Mock
    private WorkerRepository workerRepository;

    @Mock
    private WorkerConfiguration workerConfiguration;

    @InjectMocks
    private WorkerService workerService;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    // getWorker

    @Test
    public void shouldGetWorker() {
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .lastAliveDate(new Date())
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        Optional<Worker> foundWorker = workerService.getWorker(walletAddress);
        assertThat(foundWorker.isPresent()).isTrue();
        assertThat(foundWorker.get()).isEqualTo(existingWorker);
    }

    // addWorker

    @Test
    public void shouldNotAddNewWorker() {
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .lastAliveDate(new Date())
                .build();

        Worker newWorker = Worker.builder()
                .name(workerName)
                .walletAddress(walletAddress)
                .os("otherOS")
                .cpu("otherCpu")
                .cpuNb(8)
                .lastAliveDate(new Date())
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(Mockito.any())).thenReturn(newWorker);

        Worker addedWorker = workerService.addWorker(newWorker);

        assertThat(addedWorker).isNotEqualTo(existingWorker);
        assertThat(addedWorker.getId()).isEqualTo(existingWorker.getId());
    }

    @Test
    public void shouldAddNewWorker() {
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        Worker worker = Worker.builder()
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .lastAliveDate(new Date())
                .build();
        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.empty());
        when(workerRepository.save(Mockito.any())).thenReturn(worker);

        Worker addedWorker = workerService.addWorker(worker);
        // check that the save method was called once
        Mockito.verify(workerRepository, Mockito.times(1)).save(Mockito.any());
        assertThat(addedWorker.getName()).isEqualTo(worker.getName());
        assertThat(addedWorker.getLastAliveDate()).isEqualTo(worker.getLastAliveDate());
    }

    // isAllowedToJoin

    @Test
    public void shouldBeAllowed() {
        List<String> whiteList = List.of("w1", "w2");
        when(workerConfiguration.getWhitelist()).thenReturn(whiteList);
        assertThat(workerService.isAllowedToJoin("w1")).isTrue();
    }

    @Test
    public void shouldBeAllowedWhenNoWhiteList() {
        when(workerConfiguration.getWhitelist()).thenReturn(List.of());
        assertThat(workerService.isAllowedToJoin("w1")).isTrue();
    }

    @Test
    public void shouldNotBeAllowed() {
        List<String> whiteList = List.of("w1", "w2");
        when(workerConfiguration.getWhitelist()).thenReturn(whiteList);
        assertThat(workerService.isAllowedToJoin("w3")).isFalse();
    }

    // updateLasAlive

    @Test
    public void shouldUpdateLastAlive() throws ParseException {
        // init
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        Date oldLastAlive = new SimpleDateFormat("yyyy-MM-dd").parse("2018-01-01");
        Worker worker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .lastAliveDate(oldLastAlive)
                .build();
        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(worker));

        // call
        Optional<Worker> updatedWorker = workerService.updateLastAlive(walletAddress);

        // check argument passed to the save method
        ArgumentCaptor<Worker> argument = ArgumentCaptor.forClass(Worker.class);
        Mockito.verify(workerRepository).save(argument.capture());
        assertThat(argument.getValue().getId()).isEqualTo(worker.getId());
        assertThat(argument.getValue().getName()).isEqualTo(worker.getName());
        // check that the save method was called with a lastAlive parameter updated less than a second ago
        Date now = new Date();
        long duration = now.getTime() - argument.getValue().getLastAliveDate().getTime();
        long diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
        assertThat(diffInSeconds).isEqualTo(0);

        // check object returned by the method
        assertThat(updatedWorker.isPresent()).isTrue();
        assertThat(updatedWorker.get().getId()).isEqualTo(worker.getId());
        assertThat(updatedWorker.get().getName()).isEqualTo(worker.getName());
        duration = now.getTime() - updatedWorker.get().getLastAliveDate().getTime();
        diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
        assertThat(diffInSeconds).isEqualTo(0);
    }

    @Test
    public void shouldNotFindWorkerForUpdateLastAlive() {
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.empty());

        Optional<Worker> optional = workerService.updateLastAlive(walletAddress);
        assertThat(optional.isPresent()).isFalse();
    }

    // isWorkerAllowedToAskReplicate

    @Test
    public void shouldWorkerBeAllowedToAskReplicate() {
        String wallet = "wallet";
        Worker worker = Worker.builder()
                .lastReplicateDemandDate(Date.from(Instant.now().minusSeconds(10)))
                .build();
        when(workerRepository.findByWalletAddress(wallet)).thenReturn(Optional.of(worker));
        when(workerConfiguration.getAskForReplicatePeriod()).thenReturn(5000l);

        assertThat(workerService.isWorkerAllowedToAskReplicate(wallet)).isTrue();
    }

    @Test
    public void shouldWorkerBeAllowedToAskReplicateSinceFirstTime() {
        String wallet = "wallet";
        Worker worker = new Worker();
        worker.setLastReplicateDemandDate(null);
        when(workerRepository.findByWalletAddress(wallet)).thenReturn(Optional.of(worker));

        assertThat(workerService.isWorkerAllowedToAskReplicate(wallet)).isTrue();
    }

    @Test
    public void shouldWorkerNotBeAllowedToAskReplicateSinceTooSoon() {
        String wallet = "wallet";
        Worker worker = Worker.builder()
                .lastReplicateDemandDate(Date.from(Instant.now().minusSeconds(1)))
                .build();
        when(workerRepository.findByWalletAddress(wallet)).thenReturn(Optional.of(worker));
        when(workerConfiguration.getAskForReplicatePeriod()).thenReturn(5000l);

        assertThat(workerService.isWorkerAllowedToAskReplicate(wallet)).isFalse();
    }

    // updateLastReplicateDemandDate

    @Test
    public void shouldUpdateLastReplicateDemand() {
        String wallet = "wallet";
        Date lastDate = Date.from(Instant.now().minusSeconds(1));
        Worker worker = Worker.builder()
                .lastReplicateDemandDate(lastDate)
                .build();
        when(workerRepository.findByWalletAddress(wallet)).thenReturn(Optional.of(worker));

        assertThat(workerService.updateLastReplicateDemandDate(wallet)
                .get()
                .getLastReplicateDemandDate())
                .isAfter(lastDate);
    }

    // addChainTaskIdToWorker

    @Test
    public void shouldAddTaskIdToWorker(){
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .lastAliveDate(new Date())
                .participatingChainTaskIds(new ArrayList<>(Arrays.asList("task1", "task2")))
                .computingChainTaskIds(new ArrayList<>(Arrays.asList("task1", "task2")))
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(existingWorker)).thenReturn(existingWorker);

        Optional<Worker> addedWorker = workerService.addChainTaskIdToWorker("task3", walletAddress);
        assertThat(addedWorker.isPresent()).isTrue();
        Worker worker = addedWorker.get();
        assertThat(worker.getParticipatingChainTaskIds().size()).isEqualTo(3);
        assertThat(worker.getParticipatingChainTaskIds().get(2)).isEqualTo("task3");
        assertThat(worker.getComputingChainTaskIds().size()).isEqualTo(3);
        assertThat(worker.getComputingChainTaskIds().get(2)).isEqualTo("task3");
    }

    @Test
    public void shouldNotAddTaskIdToWorker(){
        when(workerRepository.findByWalletAddress(Mockito.anyString())).thenReturn(Optional.empty());
        Optional<Worker> addedWorker = workerService.addChainTaskIdToWorker("task1", "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248");
        assertThat(addedWorker.isPresent()).isFalse();
    }

    // getChainTaskIds

    @Test
    public void shouldGetChainTaskIds() {
        String wallet = "wallet";
        List<String> list = List.of("t1", "t1");
        Worker worker = Worker.builder()
                .participatingChainTaskIds(list)
                .build();
        when(workerRepository.findByWalletAddress(wallet)).thenReturn(Optional.of(worker));

        assertThat(workerService.getChainTaskIds(wallet)).isEqualTo(list);
    }

    @Test
    public void shouldGetEmptyChainTaskIdListSinceWorkerNotFound() {
        String wallet = "wallet";
        when(workerRepository.findByWalletAddress(wallet)).thenReturn(Optional.empty());
        assertThat(workerService.getChainTaskIds(wallet)).isEmpty();
    }

    // Computing task IDs

    @Test
    public void shouldGetComputingTaskIds() {
        String wallet = "wallet";
        List<String> list = List.of("t1", "t1");
        Worker worker = Worker.builder()
                .computingChainTaskIds(list)
                .build();
        when(workerRepository.findByWalletAddress(wallet)).thenReturn(Optional.of(worker));

        assertThat(workerService.getComputingTaskIds(wallet)).isEqualTo(list);
    }

    @Test
    public void shouldNotGetComputingTaskIdsSinceNoWorker() {
        String wallet = "wallet";
        when(workerRepository.findByWalletAddress(wallet)).thenReturn(Optional.empty());

        assertThat(workerService.getComputingTaskIds(wallet)).isEmpty();
    }
    
    // removeChainTaskIdFromWorker

    @Test
    public void shouldRemoveTaskIdFromWorker(){
        String workerName = "worker1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .walletAddress(walletAddress)
                .os("Linux")
                .cpu("x86")
                .cpuNb(8)
                .lastAliveDate(new Date())
                .participatingChainTaskIds(new ArrayList<>(Arrays.asList("task1", "task2")))
                .computingChainTaskIds(new ArrayList<>(Arrays.asList("task1", "task2")))
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(existingWorker)).thenReturn(existingWorker);

        Optional<Worker> removedWorker = workerService.removeChainTaskIdFromWorker("task2", walletAddress);
        assertThat(removedWorker.isPresent()).isTrue();
        Worker worker = removedWorker.get();
        assertThat(worker.getParticipatingChainTaskIds().size()).isEqualTo(1);
        assertThat(worker.getParticipatingChainTaskIds().get(0)).isEqualTo("task1");
        assertThat(worker.getComputingChainTaskIds().size()).isEqualTo(1);
        assertThat(worker.getComputingChainTaskIds().get(0)).isEqualTo("task1");
    }

    @Test
    public void shouldNotRemoveTaskIdWorkerNotFound(){
        when(workerRepository.findByWalletAddress(Mockito.anyString())).thenReturn(Optional.empty());
        Optional<Worker> addedWorker = workerService.removeChainTaskIdFromWorker("task1", "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248");
        assertThat(addedWorker.isPresent()).isFalse();
    }

    @Test
    public void shouldNotRemoveAnythingSinceTaskIdNotFound(){
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
                .lastAliveDate(new Date())
                .participatingChainTaskIds(participatingIds)
                .computingChainTaskIds(computingIds)
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(existingWorker)).thenReturn(existingWorker);

        Optional<Worker> removedWorker = workerService.removeChainTaskIdFromWorker("dummyTaskId", walletAddress);
        assertThat(removedWorker.isPresent()).isTrue();
        Worker worker = removedWorker.get();
        assertThat(worker.getParticipatingChainTaskIds().size()).isEqualTo(2);
        assertThat(worker.getParticipatingChainTaskIds()).isEqualTo(participatingIds);

        assertThat(worker.getComputingChainTaskIds().size()).isEqualTo(2);
        assertThat(worker.getComputingChainTaskIds()).isEqualTo(computingIds);
    }

    @Test
    public void shouldRemoveComputedChainTaskIdFromWorker(){
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
                .lastAliveDate(new Date())
                .participatingChainTaskIds(participatingIds)
                .computingChainTaskIds(computingIds)
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(existingWorker)).thenReturn(existingWorker);

        Optional<Worker> removedWorker = workerService.removeComputedChainTaskIdFromWorker("task1", walletAddress);
        assertThat(removedWorker.isPresent()).isTrue();
        Worker worker = removedWorker.get();
        assertThat(worker.getParticipatingChainTaskIds().size()).isEqualTo(2);
        assertThat(worker.getParticipatingChainTaskIds()).isEqualTo(participatingIds);

        assertThat(worker.getComputingChainTaskIds().size()).isEqualTo(1);
        assertThat(worker.getComputingChainTaskIds().get(0)).isEqualTo("task2");
    }

    @Test
    public void shouldNotRemoveComputedChainTaskIdFromWorkerSinceWorkerNotFound(){
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
                .lastAliveDate(new Date())
                .participatingChainTaskIds(participatingIds)
                .computingChainTaskIds(computingIds)
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.empty());
        when(workerRepository.save(existingWorker)).thenReturn(existingWorker);

        Optional<Worker> removedWorker = workerService.removeComputedChainTaskIdFromWorker("task1", walletAddress);
        assertThat(removedWorker.isPresent()).isFalse();
    }

    @Test
    public void shouldNotRemoveComputedChainTaskIdFromWorkerSinceChainTaskIdNotFound(){
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
                .lastAliveDate(new Date())
                .participatingChainTaskIds(participatingIds)
                .computingChainTaskIds(computingIds)
                .build();

        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(workerRepository.save(existingWorker)).thenReturn(existingWorker);

        Optional<Worker> removedWorker = workerService.removeComputedChainTaskIdFromWorker("dummyTaskId", walletAddress);
        assertThat(removedWorker.isPresent()).isTrue();
        Worker worker = removedWorker.get();
        assertThat(worker.getParticipatingChainTaskIds().size()).isEqualTo(2);
        assertThat(worker.getParticipatingChainTaskIds()).isEqualTo(participatingIds);

        assertThat(worker.getComputingChainTaskIds().size()).isEqualTo(2);
        assertThat(worker.getComputingChainTaskIds()).isEqualTo(computingIds);
    }

    @Test
    public void shouldGetLostWorkers() {

        List<Worker> allWorkers = getDummyWorkers(3);
        List<Worker> lostWorkers = allWorkers.subList(1, 3);
        when(workerRepository.findByLastAliveDateBefore(Mockito.any())).thenReturn(lostWorkers);

        List<Worker> claimedLostWorkers = workerService.getLostWorkers();

        // check findByLastAliveDateBefore was called with a date of one minute ago
        ArgumentCaptor<Date> argument = ArgumentCaptor.forClass(Date.class);
        Mockito.verify(workerRepository).findByLastAliveDateBefore(argument.capture());
        long diff = (new Date()).getTime() - argument.getValue().getTime();
        long diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(diff);
        assertThat(diffInMinutes).isEqualTo(1);

        // check the claimedLostWorkers are actually the lostWorkers
        assertThat(claimedLostWorkers.size()).isEqualTo(2);
        assertThat(claimedLostWorkers).isEqualTo(lostWorkers);
    }

    @Test
    public void shouldNotFindLostWorkers() {

        when(workerRepository.findByLastAliveDateBefore(Mockito.any())).thenReturn(Collections.emptyList());

        assertThat(workerService.getLostWorkers()).isEmpty();
    }

    @Test
    public void shouldGetAliveWorkers() {

        List<Worker> allWorkers = getDummyWorkers(3);
        List<Worker> aliveWorkers = allWorkers.subList(0, 1);
        when(workerRepository.findByLastAliveDateAfter(Mockito.any())).thenReturn(aliveWorkers);

        List<Worker> claimedAliveWorkers = workerService.getAliveWorkers();

        // check findByLastAliveDateAfter was called with a date of one minute ago
        ArgumentCaptor<Date> argument = ArgumentCaptor.forClass(Date.class);
        Mockito.verify(workerRepository).findByLastAliveDateAfter(argument.capture());
        long diff = (new Date()).getTime() - argument.getValue().getTime();
        long diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(diff);
        assertThat(diffInMinutes).isEqualTo(1);

        // check the claimedAliveWorkers are actually the aliveWorkers
        assertThat(claimedAliveWorkers.size()).isEqualTo(1);
        assertThat(claimedAliveWorkers).isEqualTo(aliveWorkers);
    }

    @Test
    public void shouldNotFindAliveWorkers() {

        when(workerRepository.findByLastAliveDateAfter(Mockito.any())).thenReturn(Collections.emptyList());

        assertThat(workerService.getAliveWorkers()).isEmpty();
    }

    @Test
    public void shouldAcceptMoreWorks() {
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";

        Worker worker = getDummyWorker(walletAddress,
                3,
                Arrays.asList("task1", "task2", "task3", "task4", "task5"),
                Arrays.asList("task1", "task3"));
        when(workerRepository.findByWalletAddress(walletAddress)).thenReturn(Optional.of(worker));

        assertThat(workerService.canAcceptMoreWorks(walletAddress)).isTrue();
    }

    @Test
    public void shouldNotAcceptMoreWorksSinceWorkerNotFound() {
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";

        when(workerRepository.findByWalletAddress(Mockito.any())).thenReturn(Optional.empty());

        boolean canAccept = workerService.canAcceptMoreWorks(walletAddress);
        assertThat(canAccept).isFalse();
    }

    @Test
    public void shouldNotAcceptMoreWorksSinceSaturatedCpus() {
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";

        Worker worker = getDummyWorker(walletAddress,
                2,
                Arrays.asList("task1", "task2", "task3", "task4"),
                Arrays.asList("task1", "task3"));
        when(workerRepository.findByWalletAddress(Mockito.anyString())).thenReturn(Optional.of(worker));

        assertThat(workerService.canAcceptMoreWorks(walletAddress)).isFalse();
    }

    List<Worker> getDummyWorkers(int n) {

        List<Worker> dummyWorkers = new ArrayList<>();

        for (int i=0; i<n; i++) {
            dummyWorkers.add(Worker.builder().id(Integer.toString(i)).build());
        }
        return dummyWorkers;
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
    public void shouldGetSomeAvailableCpu() {

        Worker worker1 = getDummyWorker("0x1",
                4,
                Arrays.asList("task1", "task2", "task3", "task4"),
                Arrays.asList("task1", "task3"));//2 CPUs available

        Worker worker2 = getDummyWorker("0x2",
                4,
                Arrays.asList("task1", "task2", "task3", "task4"),
                Arrays.asList("task1"));//3 CPUs available
        when(workerRepository.findByLastAliveDateAfter(any())).thenReturn(Arrays.asList(worker1, worker2));

        assertThat(workerService.getAliveAvailableCpu()).isEqualTo(5);
    }


    @Test
    public void shouldGetZeroAvailableCpuIfWorkerAlreadyFull() {

        Worker worker1 = getDummyWorker("0x1",
                4,
                Arrays.asList("task1", "task2", "task3", "task4"),
                Arrays.asList("task1", "task2", "task3", "task4"));

        Worker worker2 = getDummyWorker("0x2",
                4,
                Arrays.asList("task1", "task2", "task3", "task4"),
                Arrays.asList("task1", "task2", "task3", "task4"));
        when(workerRepository.findByLastAliveDateAfter(any())).thenReturn(Arrays.asList(worker1, worker2));

        assertThat(workerService.getAliveAvailableCpu()).isEqualTo(0);
    }

    @Test
    public void shouldGetZeroAvailableCpuIfNoWorkerAlive() {
        when(workerRepository.findByLastAliveDateAfter(any())).thenReturn(Collections.emptyList());
        assertThat(workerService.getAliveAvailableCpu()).isEqualTo(0);
    }

    // getAliveTotalCpu

    @Test
    public void shouldGetTotalAliveCpu() {
        Worker worker1 = Worker.builder()
                .cpuNb(4)
                .build();
        Worker worker2 = Worker.builder()
                .cpuNb(2)
                .build();
        List<Worker> list = List.of(worker1, worker2);
        when(workerRepository.findByLastAliveDateAfter(any())).thenReturn(list);

        assertThat(workerService.getAliveTotalCpu())
                .isEqualTo(worker1.getCpuNb() + worker2.getCpuNb());
    }

    // getAliveTotalGpu

    @Test
    public void shouldGetTotalAliveGpu() {
        Worker worker1 = Worker.builder()
                .gpuEnabled(true)
                .build();
        Worker worker2 = Worker.builder()
                .gpuEnabled(false)
                .build();
        List<Worker> list = List.of(worker1, worker2);
        when(workerRepository.findByLastAliveDateAfter(any())).thenReturn(list);

        assertThat(workerService.getAliveTotalGpu()).isEqualTo(1);
    }

    // getAliveAvailableGpu

    @Test
    public void shouldGetAliveAvailableGpu() {
        Worker worker1 = Worker.builder()
                .gpuEnabled(true)
                .computingChainTaskIds(List.of())
                .build();
        Worker worker2 = Worker.builder()
                .gpuEnabled(true)
                .computingChainTaskIds(List.of("t1"))
                .build();
        List<Worker> list = List.of(worker1, worker2);
        when(workerRepository.findByLastAliveDateAfter(any())).thenReturn(list);

        assertThat(workerService.getAliveAvailableGpu()).isEqualTo(1);
    }
}
