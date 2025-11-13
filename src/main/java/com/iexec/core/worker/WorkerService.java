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

import com.iexec.common.utils.ContextualLockRunner;
import com.iexec.core.configuration.WorkerConfiguration;
import com.mongodb.client.result.UpdateResult;
import io.micrometer.core.instrument.Metrics;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static final String WALLET_ADDRESS_FIELD = "walletAddress";
    public static final String METRIC_WORKERS_GAUGE = "iexec.core.workers";
    public static final String METRIC_CPU_COMPUTING_GAUGE = "iexec.core.cpu.computing";
    public static final String METRIC_CPU_REGISTERED_GAUGE = "iexec.core.cpu.registered";
    public static final String METRIC_GPU_COMPUTING_GAUGE = "iexec.core.gpu.computing";
    public static final String METRIC_GPU_REGISTERED_GAUGE = "iexec.core.gpu.registered";
    private final MongoTemplate mongoTemplate;
    private final WorkerRepository workerRepository;
    private final WorkerConfiguration workerConfiguration;
    private final ContextualLockRunner<String> contextualLockRunner;
    private final AtomicInteger aliveWorkersGauge;
    private final AtomicInteger aliveComputingCpuGauge;
    private final AtomicInteger aliveRegisteredCpuGauge;
    private final AtomicInteger aliveComputingGpuGauge;
    private final AtomicInteger aliveRegisteredGpuGauge;
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

    public WorkerService(MongoTemplate mongoTemplate,
                         WorkerRepository workerRepository,
                         WorkerConfiguration workerConfiguration) {
        this.mongoTemplate = mongoTemplate;
        this.workerRepository = workerRepository;
        this.workerConfiguration = workerConfiguration;
        this.contextualLockRunner = new ContextualLockRunner<>();

        this.aliveWorkersGauge = Metrics.gauge(METRIC_WORKERS_GAUGE, new AtomicInteger(0));
        this.aliveComputingCpuGauge = Metrics.gauge(METRIC_CPU_COMPUTING_GAUGE, new AtomicInteger(0));
        this.aliveRegisteredCpuGauge = Metrics.gauge(METRIC_CPU_REGISTERED_GAUGE, new AtomicInteger(0));
        this.aliveComputingGpuGauge = Metrics.gauge(METRIC_GPU_COMPUTING_GAUGE, new AtomicInteger(0));
        this.aliveRegisteredGpuGauge = Metrics.gauge(METRIC_GPU_REGISTERED_GAUGE, new AtomicInteger(0));
    }

    /**
     * updateMetrics is used to update all workers metrics
     */
    @Scheduled(fixedDelayString = "${cron.metrics.refresh.period}")
    void updateMetrics() {
        // Fusion of methods getAliveTotalCpu and getAliveAvailableCpu to prevent making 3 calls to getAliveWorkers
        int computingCpus = 0;
        int registeredCpus = 0;
        int computingGpus = 0;
        int registeredGpus = 0;
        final List<Worker> workers = getAliveWorkers();
        for (final Worker worker : workers) {
            if (worker.isGpuEnabled()) {
                registeredGpus++;
                if (!worker.getComputingChainTaskIds().isEmpty()) {
                    computingGpus++;
                }
            } else {
                registeredCpus += worker.getCpuNb();
                computingCpus += worker.getComputingChainTaskIds().size();
            }
        }

        aliveWorkersGauge.set(workers.size());
        aliveComputingCpuGauge.set(computingCpus);
        aliveRegisteredCpuGauge.set(registeredCpus);
        aliveComputingGpuGauge.set(computingGpus);
        aliveRegisteredGpuGauge.set(registeredGpus);
    }

    public AliveWorkerMetrics getAliveWorkerMetrics() {
        return AliveWorkerMetrics.builder()
                .aliveWorkers(aliveWorkersGauge.get())
                .aliveComputingCpu(aliveComputingCpuGauge.get())
                .aliveRegisteredCpu(aliveRegisteredCpuGauge.get())
                .aliveComputingGpu(aliveComputingGpuGauge.get())
                .aliveRegisteredGpu(aliveRegisteredGpuGauge.get())
                .build();
    }

    // region Read methods
    public Optional<Worker> getWorker(String walletAddress) {
        return workerRepository.findByWalletAddress(walletAddress);
    }

    public boolean isAllowedToJoin(String workerAddress) {
        final List<String> whitelist = workerConfiguration.getWhitelist();
        // if the whitelist is empty, there is no restriction on the workers
        return whitelist.isEmpty() || whitelist.contains(workerAddress);
    }

    public boolean isWorkerAllowedToAskReplicate(String walletAddress) {
        Date lastReplicateDemandDate = workerStatsMap.computeIfAbsent(walletAddress, WorkerStats::new)
                .getLastReplicateDemandDate();
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
        return getWorker(walletAddress)
                .map(Worker::getParticipatingChainTaskIds)
                .orElse(Collections.emptyList());
    }

    public List<String> getComputingTaskIds(String walletAddress) {
        return getWorker(walletAddress)
                .map(Worker::getComputingChainTaskIds)
                .orElse(Collections.emptyList());
    }


    // worker is considered lost if it didn't ping for 1 minute
    public List<Worker> getLostWorkers() {
        final Date oneMinuteAgo = Date.from(Instant.now().minus(1L, ChronoUnit.MINUTES));
        final List<String> lostWorkers = workerStatsMap.entrySet()
                .stream()
                .filter(entry -> entry.getValue().getLastAliveDate().getTime() < oneMinuteAgo.getTime())
                .map(Map.Entry::getKey)
                .toList();
        return workerRepository.findByWalletAddressIn(lostWorkers);
    }

    // worker is considered alive if it received a ping during the last minute
    public List<Worker> getAliveWorkers() {
        final Date oneMinuteAgo = Date.from(Instant.now().minus(1L, ChronoUnit.MINUTES));
        final List<String> aliveWorkers = workerStatsMap.entrySet()
                .stream()
                .filter(entry -> entry.getValue().getLastAliveDate().getTime() > oneMinuteAgo.getTime())
                .map(Map.Entry::getKey)
                .toList();
        return workerRepository.findByWalletAddressIn(aliveWorkers);
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
        mongoTemplate.updateFirst(
                Query.query(Criteria.where(WALLET_ADDRESS_FIELD).is(walletAddress)),
                new Update().currentDate("lastAliveDate"),
                Worker.class);
    }

    public void updateLastReplicateDemandDate(String walletAddress) {
        workerStatsMap.computeIfAbsent(walletAddress, WorkerStats::new)
                .setLastReplicateDemandDate(new Date());
        mongoTemplate.updateFirst(
                Query.query(Criteria.where(WALLET_ADDRESS_FIELD).is(walletAddress)),
                new Update().currentDate("lastReplicateDemandDate"),
                Worker.class);
    }

    public Optional<Worker> addChainTaskIdToWorker(String chainTaskId, String walletAddress) {
        return contextualLockRunner.applyWithLock(
                walletAddress,
                address -> addChainTaskIdToWorkerWithoutThreadSafety(chainTaskId, address)
        );
    }

    private Optional<Worker> addChainTaskIdToWorkerWithoutThreadSafety(String chainTaskId, String walletAddress) {
        final Worker worker = workerRepository.findByWalletAddress(walletAddress).orElse(null);
        if (worker == null) {
            log.warn("Can't add chainTaskId to worker when unknown worker [chainTaskId:{}, workerAddress:{}]",
                    chainTaskId, walletAddress);
            return Optional.empty();
        }
        if (worker.hasNoRemainingComputingSlot()) {
            log.warn("Can't add chainTaskId to worker when already full [chainTaskId:{}, workerAddress:{}]",
                    chainTaskId, walletAddress);
            return Optional.empty();
        }
        worker.addChainTaskId(chainTaskId);
        log.info("Added chainTaskId to worker [chainTaskId:{}, workerAddress:{}]", chainTaskId, walletAddress);
        return Optional.of(workerRepository.save(worker));
    }

    public Optional<Worker> removeChainTaskIdFromWorker(String chainTaskId, String walletAddress) {
        return contextualLockRunner.applyWithLock(
                walletAddress,
                address -> removeChainTaskIdFromWorkerWithoutThreadSafety(chainTaskId, address)
        );
    }

    private Optional<Worker> removeChainTaskIdFromWorkerWithoutThreadSafety(String chainTaskId, String walletAddress) {
        UpdateResult result = mongoTemplate.updateFirst(
                Query.query(Criteria.where(WALLET_ADDRESS_FIELD).is(walletAddress)),
                new Update().pull("computingChainTaskIds", chainTaskId).pull("participatingChainTaskIds", chainTaskId),
                Worker.class);
        log.info("Remove chainTaskId [chainTaskId:{}, workerAddress:{}, result:{}]", chainTaskId, walletAddress, result);
        return workerRepository.findByWalletAddress(walletAddress);
    }

    public Optional<Worker> removeComputedChainTaskIdFromWorker(String chainTaskId, String walletAddress) {
        return contextualLockRunner.applyWithLock(
                walletAddress,
                address -> removeComputedChainTaskIdFromWorkerWithoutThreadSafety(chainTaskId, address)
        );
    }

    private Optional<Worker> removeComputedChainTaskIdFromWorkerWithoutThreadSafety(String chainTaskId, String walletAddress) {
        UpdateResult result = mongoTemplate.updateFirst(
                Query.query(Criteria.where(WALLET_ADDRESS_FIELD).is(walletAddress)),
                new Update().pull("computingChainTaskIds", chainTaskId),
                Worker.class);
        log.debug("Remove computed chainTaskId [chainTaskId:{}, workerAddress:{}, result:{}]", chainTaskId, walletAddress, result);
        return workerRepository.findByWalletAddress(walletAddress);
    }
    // endregion
}
