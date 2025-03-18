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

package com.iexec.core.metric;

import com.iexec.core.chain.DealWatcherService;
import com.iexec.core.chain.event.LatestBlockEvent;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.event.TaskStatusesCountUpdatedEvent;
import com.iexec.core.worker.AliveWorkerMetrics;
import com.iexec.core.worker.WorkerService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;

@Service
public class MetricService {
    private final DealWatcherService dealWatcherService;
    private final WorkerService workerService;
    private LinkedHashMap<TaskStatus, Long> currentTaskStatusesCount;
    private PlatformMetric.LatestBlockMetric latestBlock;

    public MetricService(final DealWatcherService dealWatcherService,
                         final WorkerService workerService) {
        this.dealWatcherService = dealWatcherService;
        this.workerService = workerService;

        this.currentTaskStatusesCount = new LinkedHashMap<>();
    }

    public PlatformMetric getPlatformMetrics() {
        final AliveWorkerMetrics aliveWorkerMetrics = workerService.getAliveWorkerMetrics();
        return PlatformMetric.builder()
                .aliveWorkers(aliveWorkerMetrics.aliveWorkers())
                .aliveComputingCpu(aliveWorkerMetrics.aliveComputingCpu())
                .aliveRegisteredCpu(aliveWorkerMetrics.aliveRegisteredCpu())
                .aliveComputingGpu(aliveWorkerMetrics.aliveComputingGpu())
                .aliveRegisteredGpu(aliveWorkerMetrics.aliveRegisteredGpu())
                .currentTaskStatusesCount(currentTaskStatusesCount)
                .latestBlockMetric(latestBlock)
                .dealEventsCount(dealWatcherService.getDealEventsCount())
                .dealsCount(dealWatcherService.getDealsCount())
                .replayDealsCount(dealWatcherService.getReplayDealsCount())
                .latestBlockNumberWithDeal(dealWatcherService.getLatestBlockNumberWithDeal())
                .aliveAvailableCpu(aliveWorkerMetrics.aliveRegisteredCpu() - aliveWorkerMetrics.aliveComputingCpu())
                .aliveTotalCpu(aliveWorkerMetrics.aliveRegisteredCpu())
                .aliveAvailableGpu(aliveWorkerMetrics.aliveRegisteredGpu() - aliveWorkerMetrics.aliveComputingGpu())
                .aliveTotalGpu(aliveWorkerMetrics.aliveRegisteredGpu())
                .build();
    }

    @EventListener
    void onLatestBlockEvent(final LatestBlockEvent event) {
        latestBlock = new PlatformMetric.LatestBlockMetric(event.getBlockNumber(), event.getBlockHash(), event.getBlockTimestamp());
    }

    @EventListener
    void onTaskStatusesCountUpdateEvent(final TaskStatusesCountUpdatedEvent event) {
        this.currentTaskStatusesCount = event.getCurrentTaskStatusesCount();
    }
}
