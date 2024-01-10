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
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.event.TaskStatusesCountUpdatedEvent;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class MetricServiceTests {

    @Mock
    private DealWatcherService dealWatcherService;
    @Mock
    private WorkerService workerService;

    @InjectMocks
    private MetricService metricService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldGetPlatformMetrics() {
        final LinkedHashMap<TaskStatus, AtomicLong> expectedCurrentTaskStatusesCount = createExpectedCurrentTaskStatusesCount();

        List<Worker> aliveWorkers = List.of(new Worker());
        when(workerService.getAliveWorkers()).thenReturn(aliveWorkers);
        when(workerService.getAliveTotalCpu()).thenReturn(1);
        when(workerService.getAliveAvailableCpu()).thenReturn(1);
        when(workerService.getAliveTotalGpu()).thenReturn(1);
        when(workerService.getAliveAvailableGpu()).thenReturn(1);
        when(dealWatcherService.getDealEventsCount()).thenReturn(10L);
        when(dealWatcherService.getDealsCount()).thenReturn(8L);
        when(dealWatcherService.getReplayDealsCount()).thenReturn(2L);
        when(dealWatcherService.getLatestBlockNumberWithDeal()).thenReturn(BigInteger.valueOf(255L));

        PlatformMetric metric = metricService.getPlatformMetrics();
        Assertions.assertAll(
                () -> assertThat(metric.getAliveWorkers()).isEqualTo(aliveWorkers.size()),
                () -> assertThat(metric.getAliveTotalCpu()).isEqualTo(1),
                () -> assertThat(metric.getAliveAvailableCpu()).isEqualTo(1),
                () -> assertThat(metric.getAliveTotalGpu()).isEqualTo(1),
                () -> assertThat(metric.getAliveAvailableGpu()).isEqualTo(1),
                () -> assertThat(metric.getCurrentTaskStatusCounts()).isEqualTo(expectedCurrentTaskStatusesCount),
                () -> assertThat(metric.getDealEventsCount()).isEqualTo(10),
                () -> assertThat(metric.getDealsCount()).isEqualTo(8),
                () -> assertThat(metric.getReplayDealsCount()).isEqualTo(2),
                () -> assertThat(metric.getLatestBlockNumberWithDeal()).isEqualTo(255)
        );
    }

    private LinkedHashMap<TaskStatus, AtomicLong> createExpectedCurrentTaskStatusesCount() {
        final LinkedHashMap<TaskStatus, AtomicLong> expectedCurrentTaskStatusesCount = new LinkedHashMap<>(TaskStatus.values().length);
        expectedCurrentTaskStatusesCount.put(TaskStatus.RECEIVED, new AtomicLong(1));
        expectedCurrentTaskStatusesCount.put(TaskStatus.INITIALIZING, new AtomicLong(2));
        expectedCurrentTaskStatusesCount.put(TaskStatus.INITIALIZED, new AtomicLong(3));
        expectedCurrentTaskStatusesCount.put(TaskStatus.INITIALIZE_FAILED, new AtomicLong(4));
        expectedCurrentTaskStatusesCount.put(TaskStatus.RUNNING, new AtomicLong(5));
        expectedCurrentTaskStatusesCount.put(TaskStatus.RUNNING_FAILED, new AtomicLong(6));
        expectedCurrentTaskStatusesCount.put(TaskStatus.CONTRIBUTION_TIMEOUT, new AtomicLong(7));
        expectedCurrentTaskStatusesCount.put(TaskStatus.CONSENSUS_REACHED, new AtomicLong(8));
        expectedCurrentTaskStatusesCount.put(TaskStatus.REOPENING, new AtomicLong(9));
        expectedCurrentTaskStatusesCount.put(TaskStatus.REOPENED, new AtomicLong(10));
        expectedCurrentTaskStatusesCount.put(TaskStatus.REOPEN_FAILED, new AtomicLong(11));
        expectedCurrentTaskStatusesCount.put(TaskStatus.AT_LEAST_ONE_REVEALED, new AtomicLong(12));
        expectedCurrentTaskStatusesCount.put(TaskStatus.RESULT_UPLOADING, new AtomicLong(13));
        expectedCurrentTaskStatusesCount.put(TaskStatus.RESULT_UPLOADED, new AtomicLong(14));
        expectedCurrentTaskStatusesCount.put(TaskStatus.RESULT_UPLOAD_TIMEOUT, new AtomicLong(15));
        expectedCurrentTaskStatusesCount.put(TaskStatus.FINALIZING, new AtomicLong(16));
        expectedCurrentTaskStatusesCount.put(TaskStatus.FINALIZED, new AtomicLong(17));
        expectedCurrentTaskStatusesCount.put(TaskStatus.FINALIZE_FAILED, new AtomicLong(18));
        expectedCurrentTaskStatusesCount.put(TaskStatus.FINAL_DEADLINE_REACHED, new AtomicLong(19));
        expectedCurrentTaskStatusesCount.put(TaskStatus.COMPLETED, new AtomicLong(20));
        expectedCurrentTaskStatusesCount.put(TaskStatus.FAILED, new AtomicLong(21));
        metricService.onTaskStatusesCountUpdateEvent(new TaskStatusesCountUpdatedEvent(expectedCurrentTaskStatusesCount));

        return expectedCurrentTaskStatusesCount;
    }

}
