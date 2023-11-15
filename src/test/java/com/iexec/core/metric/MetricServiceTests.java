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
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class MetricServiceTests {

    @Mock
    private DealWatcherService dealWatcherService;
    @Mock
    private WorkerService workerService;
    @Mock
    private TaskService taskService;
    private MetricService metricService;

    @BeforeAll
    static void initRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
    }

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        List<Worker> aliveWorkers = List.of(new Worker(), new Worker(), new Worker());
        when(workerService.getAliveWorkers()).thenReturn(aliveWorkers);
        when(workerService.getAliveTotalCpu()).thenReturn(1);
        when(workerService.getAliveAvailableCpu()).thenReturn(1);
        when(workerService.getAliveTotalGpu()).thenReturn(1);
        when(workerService.getAliveAvailableGpu()).thenReturn(1);
        when(taskService.findByCurrentStatus(TaskStatus.COMPLETED))
                .thenReturn(List.of());
        when(dealWatcherService.getDealEventsCount()).thenReturn(10L);
        when(dealWatcherService.getDealsCount()).thenReturn(8L);
        when(dealWatcherService.getReplayDealsCount()).thenReturn(2L);
        when(dealWatcherService.getLatestBlockNumberWithDeal()).thenReturn(BigInteger.valueOf(255L));

        metricService = new MetricService(dealWatcherService, workerService, taskService);
        metricService.initializeMetrics();
    }

    @AfterEach
    void afterEach() {
        Metrics.globalRegistry.clear();
    }

    @Test
    void shouldGetPlatformMetrics() {

        PlatformMetric metric = metricService.getPlatformMetrics();
        assertThat(metric.getAliveWorkers()).isEqualTo(3);
        assertThat(metric.getAliveTotalCpu()).isEqualTo(1);
        assertThat(metric.getAliveAvailableCpu()).isEqualTo(1);
        assertThat(metric.getAliveTotalGpu()).isEqualTo(1);
        assertThat(metric.getAliveAvailableGpu()).isEqualTo(1);
        assertThat(metric.getCompletedTasks()).isZero();
        assertThat(metric.getDealEventsCount()).isEqualTo(10);
        assertThat(metric.getDealsCount()).isEqualTo(8);
        assertThat(metric.getReplayDealsCount()).isEqualTo(2);
        assertThat(metric.getLatestBlockNumberWithDeal()).isEqualTo(255);
    }

    @Test
    void shouldInitializeMetrics() {
        verifyGaugeMetric("iexec.core.workers", 3);
        verifyGaugeMetric("iexec.core.cpu.total", 1);
        verifyGaugeMetric("iexec.core.cpu.available", 1);
        verifyGaugeMetric("iexec.core.tasks.completed", 0);
        verifyGaugeMetric("iexec.core.deals.events", 10L);
        verifyGaugeMetric("iexec.core.deals", 8);
        verifyGaugeMetric("iexec.core.deals.replay", 2L);
        verifyGaugeMetric("iexec.core.deals.last.block", 255L);
    }

    @Test
    void shouldUpdateMetrics() {

        List<Worker> aliveWorkers = List.of(new Worker(), new Worker());

        when(workerService.getAliveWorkers()).thenReturn(aliveWorkers);
        when(workerService.getAliveAvailableCpu()).thenReturn(7);
        when(workerService.getAliveTotalCpu()).thenReturn(15);
        when(taskService.findByCurrentStatus(TaskStatus.COMPLETED)).thenReturn(Collections.emptyList());
        when(dealWatcherService.getDealEventsCount()).thenReturn(120L);
        when(dealWatcherService.getDealsCount()).thenReturn(60L);
        when(dealWatcherService.getReplayDealsCount()).thenReturn(25L);
        when(dealWatcherService.getLatestBlockNumberWithDeal()).thenReturn(BigInteger.valueOf(255L));

        metricService.updateMetrics();

        verifyGaugeMetric("iexec.core.workers", aliveWorkers.size());
        verifyGaugeMetric("iexec.core.cpu.total", 15);
        verifyGaugeMetric("iexec.core.cpu.available", 7);
        verifyGaugeMetric("iexec.core.tasks.completed", 0);
        verifyGaugeMetric("iexec.core.deals.events", 120L);
        verifyGaugeMetric("iexec.core.deals", 60L);
        verifyGaugeMetric("iexec.core.deals.replay", 25L);
        verifyGaugeMetric("iexec.core.deals.last.block", 255L);
    }

    private void verifyGaugeMetric(String metricName, long expectedValue) {
        Gauge gauge = Metrics.globalRegistry.find(metricName).gauge();
        Assertions.assertEquals(expectedValue, gauge.value());
    }
}
