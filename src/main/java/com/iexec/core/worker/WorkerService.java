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

import com.iexec.common.utils.ContextualLockRunner;
import com.iexec.core.configuration.WorkerConfiguration;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.iexec.common.utils.DateTimeUtils.addMinutesToDate;

/**
 * Manage {@link Worker} objects.
 * <p>
 * /!\ Private read-and-write methods are not thread-safe.
 * They can sometime lead to race conditions.
 * Please use the public, thread-safe, versions of these methods instead.
 */
@Slf4j
@Service
public class WorkerService {

    private final WorkerRepository workerRepository;
    private final WorkerConfiguration workerConfiguration;
    private final ContextualLockRunner<String> contextualLockRunner;
    @Getter
    private final ConcurrentHashMap<String, WorkerStats> workerStatsMap = new ConcurrentHashMap<>();

    @Data
    public static class WorkerStats {
        private String walletAddress;
        private Date lastAliveDate;
        private Date lastReplicateDemandDate;

        public WorkerStats(String walletAddress) {
            this.walletAddress = walletAddress;
        }
    }

    public WorkerService(WorkerRepository workerRepository,
                         WorkerConfiguration workerConfiguration) {
        this.workerRepository = workerRepository;
        this.workerConfiguration = workerConfiguration;
        this.contextualLockRunner = new ContextualLockRunner<>();
    }

    // region Read methods
    public Optional<Worker> getWorker(String walletAddress) {
        return workerRepository.findByWalletAddress(walletAddress);
    }

    public boolean isAllowedToJoin(String workerAddress){
        List<String> whitelist = workerConfiguration.getWhitelist();
        // if the whitelist is empty, there is no restriction on the workers
        if (whitelist.isEmpty()){
            return true;
        }
        return whitelist.contains(workerAddress);
    }

    public boolean isWorkerAllowedToAskReplicate(String walletAddress) {
        Date lastReplicateDemandDate = workerStatsMap.get(walletAddress).getLastReplicateDemandDate();
        if (lastReplicateDemandDate == null) {
            return true;
        }

        // the difference between now and the last time the worker asked for work should be less than the period allowed
        // in the configuration (500ms since (now - lastAsk) can still be slightly too small even if the worker behave nicely)
        long now = new Date().getTime();
        long lastAsk = lastReplicateDemandDate.getTime();

        return (now - lastAsk) + 500 > workerConfiguration.getAskForReplicatePeriod();
    }

    public List<String> getChainTaskIds(String walletAddress) {
        Optional<Worker> optional = workerRepository.findByWalletAddress(walletAddress);
        if (optional.isPresent()) {
            Worker worker = optional.get();
            return worker.getParticipatingChainTaskIds();
        }
        return Collections.emptyList();
    }

    public List<String> getComputingTaskIds(String walletAddress) {
        Optional<Worker> optional = workerRepository.findByWalletAddress(walletAddress);
        if (optional.isPresent()) {
            Worker worker = optional.get();
            return worker.getComputingChainTaskIds();
        }
        return Collections.emptyList();
    }


    // worker is considered lost if it didn't ping for 1 minute
    public List<Worker> getLostWorkers() {
        Date oneMinuteAgo = addMinutesToDate(new Date(), -1);
        List<String> lostWorkers = workerStatsMap.entrySet()
                .stream()
                .filter(entry -> entry.getValue().getLastAliveDate().getTime() < oneMinuteAgo.getTime())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        return workerRepository.findAllByWalletAddress(lostWorkers);
    }

    // worker is considered alive if it received a ping during the last minute
    public List<Worker> getAliveWorkers() {
        Date oneMinuteAgo = addMinutesToDate(new Date(), -1);
        List<String> aliveWorkers = workerStatsMap.entrySet()
                .stream()
                .filter(entry -> entry.getValue().getLastAliveDate().getTime() > oneMinuteAgo.getTime())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        return workerRepository.findAllByWalletAddress(aliveWorkers);
    }

    public boolean canAcceptMoreWorks(String walletAddress) {
        Optional<Worker> optionalWorker = getWorker(walletAddress);
        if (optionalWorker.isEmpty()){
            return false;
        }

        Worker worker = optionalWorker.get();
        int workerMaxNbTasks = worker.getMaxNbTasks();
        int runningReplicateNb = worker.getComputingChainTaskIds().size();

        if (runningReplicateNb >= workerMaxNbTasks) {
            log.debug("Worker asking for too many replicates [walletAddress:{}, runningReplicateNb:{}, workerMaxNbTasks:{}]",
                    walletAddress, runningReplicateNb, workerMaxNbTasks);
            return false;
        }

        return true;
    }

    public int getAliveAvailableCpu() {
        int availableCpus = 0;
        for (Worker worker: getAliveWorkers()) {
            if (worker.isGpuEnabled()) {
                continue;
            }

            int workerCpuNb = worker.getCpuNb();
            int computingReplicateNb = worker.getComputingChainTaskIds().size();
            int availableCpu = workerCpuNb - computingReplicateNb;
            availableCpus+= availableCpu;
        }
        return availableCpus;
    }

    public int getAliveTotalCpu() {
        int totalCpus = 0;
        for (Worker worker: getAliveWorkers()){
            if(worker.isGpuEnabled()) {
                continue;
            }
            totalCpus+= worker.getCpuNb();
        }
        return totalCpus;
    }

    // We suppose for now that 1 Gpu enabled worker has only one GPU
    public int getAliveTotalGpu() {
        int totalGpus = 0;
        for(Worker worker: getAliveWorkers()) {
            if (worker.isGpuEnabled()) {
                totalGpus++;
            }
        }
        return totalGpus;
    }

    public int getAliveAvailableGpu () {
        int availableGpus = getAliveTotalGpu();
        for (Worker worker: getAliveWorkers()) {
            if (worker.isGpuEnabled()) {
                boolean isWorking = !worker.getComputingChainTaskIds().isEmpty();
                if (isWorking) {
                    availableGpus = availableGpus - 1;
                }
            }
        }
        return availableGpus;
    }
    // endregion

    // region Read-and-write methods
    public Worker addWorker(Worker worker) {
        updateLastAlive(worker.getWalletAddress());
        return contextualLockRunner.applyWithLock(
                worker.getWalletAddress(),
                address -> addWorkerWithoutThreadSafety(worker)
        );
    }

    private Worker addWorkerWithoutThreadSafety(Worker worker) {
        Optional<Worker> oWorker = workerRepository.findByWalletAddress(worker.getWalletAddress());

        if (oWorker.isPresent()) {
            Worker existingWorker = oWorker.get();
            log.info("The worker is already registered [workerId:{}]", existingWorker.getId());
            worker.setId(existingWorker.getId());
            worker.setParticipatingChainTaskIds(existingWorker.getParticipatingChainTaskIds());
            worker.setComputingChainTaskIds(existingWorker.getComputingChainTaskIds());
        } else {
            log.info("Registering new worker");
        }

        return workerRepository.save(worker);
    }

    public void updateLastAlive(String walletAddress) {
        workerStatsMap.computeIfAbsent(walletAddress, WorkerStats::new)
                .setLastAliveDate(new Date());
    }

    public void updateLastReplicateDemandDate(String walletAddress) {
        workerStatsMap.computeIfAbsent(walletAddress, WorkerStats::new)
                .setLastReplicateDemandDate(new Date());
    }

    public Optional<Worker> addChainTaskIdToWorker(String chainTaskId, String walletAddress) {
        return contextualLockRunner.applyWithLock(
                walletAddress,
                address -> addChainTaskIdToWorkerWithoutThreadSafety(chainTaskId, address)
        );
    }

    private Optional<Worker> addChainTaskIdToWorkerWithoutThreadSafety(String chainTaskId, String walletAddress) {
        Optional<Worker> optional = workerRepository.findByWalletAddress(walletAddress);
        if (optional.isPresent()) {
            Worker worker = optional.get();
            worker.addChainTaskId(chainTaskId);
            log.info("Added chainTaskId to worker [chainTaskId:{}, workerName:{}]", chainTaskId, walletAddress);
            return Optional.of(workerRepository.save(worker));
        }
        return Optional.empty();
    }

    public Optional<Worker> removeChainTaskIdFromWorker(String chainTaskId, String walletAddress) {
        return contextualLockRunner.applyWithLock(
                walletAddress,
                address -> removeChainTaskIdFromWorkerWithoutThreadSafety(chainTaskId, address)
        );
    }

    private Optional<Worker> removeChainTaskIdFromWorkerWithoutThreadSafety(String chainTaskId, String walletAddress) {
        Optional<Worker> optional = workerRepository.findByWalletAddress(walletAddress);
        if (optional.isPresent()) {
            Worker worker = optional.get();
            worker.removeChainTaskId(chainTaskId);
            log.info("Removed chainTaskId from worker [chainTaskId:{}, walletAddress:{}]", chainTaskId, walletAddress);
            return Optional.of(workerRepository.save(worker));
        }
        return Optional.empty();
    }

    public Optional<Worker> removeComputedChainTaskIdFromWorker(String chainTaskId, String walletAddress) {
        return contextualLockRunner.applyWithLock(
                walletAddress,
                address -> removeComputedChainTaskIdFromWorkerWithoutThreadSafety(chainTaskId, address)
        );
    }

    private Optional<Worker> removeComputedChainTaskIdFromWorkerWithoutThreadSafety(String chainTaskId, String walletAddress) {
        Optional<Worker> optional = workerRepository.findByWalletAddress(walletAddress);
        if (optional.isPresent()) {
            Worker worker = optional.get();
            worker.removeComputedChainTaskId(chainTaskId);
            log.info("Removed computed chainTaskId from worker [chainTaskId:{}, walletAddress:{}]", chainTaskId, walletAddress);
            return Optional.of(workerRepository.save(worker));
        }
        return Optional.empty();
    }
    // endregion
}
