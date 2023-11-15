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

package com.iexec.core.metric;

import com.iexec.core.chain.DealWatcherService;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.worker.WorkerService;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class MetricService {

    private final DealWatcherService dealWatcherService;
    private final WorkerService workerService;
    private final TaskService taskService;
    private AtomicInteger aliveWorkersCount;
    private AtomicInteger aliveTotalCpuCount;
    private AtomicInteger aliveAvailableCpuCount;
    private AtomicInteger completedTasksCount;
    private AtomicLong dealEventsCount;
    private AtomicLong replayDealsCount;
    private AtomicLong dealsCount;
    private AtomicLong latestBlockNumberWithDeal;

    public MetricService(DealWatcherService dealWatcherService,
                         WorkerService workerService,
                         TaskService taskService) {
        this.dealWatcherService = dealWatcherService;
        this.workerService = workerService;
        this.taskService = taskService;
    }

    //The PostConstruct will be invoked during the bean initialization.
    @PostConstruct
    void initializeMetrics() {
        aliveWorkersCount = Metrics.gauge("iexec.core.workers", new AtomicInteger(workerService.getAliveWorkers().size()));
        aliveTotalCpuCount = Metrics.gauge("iexec.core.cpu.total", new AtomicInteger(workerService.getAliveTotalCpu()));
        aliveAvailableCpuCount = Metrics.gauge("iexec.core.cpu.available", new AtomicInteger(workerService.getAliveAvailableCpu()));
        completedTasksCount = Metrics.gauge("iexec.core.tasks.completed", new AtomicInteger(taskService.findByCurrentStatus(TaskStatus.COMPLETED).size()));
        dealEventsCount = Metrics.gauge("iexec.core.deals.events", new AtomicLong(dealWatcherService.getDealEventsCount()));
        dealsCount = Metrics.gauge("iexec.core.deals", new AtomicLong(dealWatcherService.getDealsCount()));
        replayDealsCount = Metrics.gauge("iexec.core.deals.replay", new AtomicLong(dealWatcherService.getReplayDealsCount()));
        if (null != dealWatcherService.getLatestBlockNumberWithDeal()) {
            latestBlockNumberWithDeal = Metrics.gauge("iexec.core.deals.last.block", new AtomicLong(dealWatcherService.getLatestBlockNumberWithDeal().longValue()));
        } else {
            latestBlockNumberWithDeal = Metrics.gauge("iexec.core.deals.last.block", new AtomicLong(-1L));
        }
    }

    public PlatformMetric getPlatformMetrics() {
        return PlatformMetric.builder()
                .aliveWorkers(workerService.getAliveWorkers().size())
                .aliveTotalCpu(workerService.getAliveTotalCpu())
                .aliveAvailableCpu(workerService.getAliveAvailableCpu())
                .aliveTotalGpu(workerService.getAliveTotalGpu())
                .aliveAvailableGpu(workerService.getAliveAvailableGpu())
                .completedTasks(taskService.findByCurrentStatus(TaskStatus.COMPLETED).size())
                .dealEventsCount(dealWatcherService.getDealEventsCount())
                .dealsCount(dealWatcherService.getDealsCount())
                .replayDealsCount(dealWatcherService.getReplayDealsCount())
                .latestBlockNumberWithDeal(dealWatcherService.getLatestBlockNumberWithDeal())
                .build();
    }

    //The Scheduled method will be invoked after the bean was initialized.
    @Scheduled(fixedDelayString = "${cron.metrics.refresh.period}", initialDelayString = "${cron.metrics.refresh.period}")
    void updateMetrics() {
        try {
            aliveWorkersCount.set(workerService.getAliveWorkers().size());
            aliveAvailableCpuCount.set(workerService.getAliveAvailableCpu());
            aliveTotalCpuCount.set(workerService.getAliveTotalCpu());
            completedTasksCount.set(taskService.findByCurrentStatus(TaskStatus.COMPLETED).size());
            dealEventsCount.set(dealWatcherService.getDealEventsCount());
            dealsCount.set(dealWatcherService.getDealsCount());
            replayDealsCount.set(dealWatcherService.getReplayDealsCount());
            latestBlockNumberWithDeal.set(dealWatcherService.getLatestBlockNumberWithDeal().longValue());
        } catch (RuntimeException ex) {
            log.error("Update metrics has failed ", ex);
        }
    }
}
