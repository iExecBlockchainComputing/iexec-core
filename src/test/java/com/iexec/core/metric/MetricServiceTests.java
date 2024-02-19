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
        final LinkedHashMap<TaskStatus, Long> expectedCurrentTaskStatusesCount = createExpectedCurrentTaskStatusesCount();

        List<Worker> aliveWorkers = List.of(Worker.builder().build());
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
                () -> assertThat(metric.getCurrentTaskStatusesCount()).isEqualTo(expectedCurrentTaskStatusesCount),
                () -> assertThat(metric.getDealEventsCount()).isEqualTo(10),
                () -> assertThat(metric.getDealsCount()).isEqualTo(8),
                () -> assertThat(metric.getReplayDealsCount()).isEqualTo(2),
                () -> assertThat(metric.getLatestBlockNumberWithDeal()).isEqualTo(255)
        );
    }

    private LinkedHashMap<TaskStatus, Long> createExpectedCurrentTaskStatusesCount() {
        final LinkedHashMap<TaskStatus, Long> expectedCurrentTaskStatusesCount = new LinkedHashMap<>(TaskStatus.values().length);
        expectedCurrentTaskStatusesCount.put(TaskStatus.RECEIVED, 1L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.INITIALIZING, 2L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.INITIALIZED, 3L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.INITIALIZE_FAILED, 4L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.RUNNING, 5L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.RUNNING_FAILED, 6L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.CONTRIBUTION_TIMEOUT, 7L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.CONSENSUS_REACHED, 8L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.REOPENING, 9L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.REOPENED, 10L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.REOPEN_FAILED, 11L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.AT_LEAST_ONE_REVEALED, 12L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.RESULT_UPLOADING, 13L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.RESULT_UPLOADED, 14L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.RESULT_UPLOAD_TIMEOUT, 15L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.FINALIZING, 16L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.FINALIZED, 17L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.FINALIZE_FAILED, 18L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.FINAL_DEADLINE_REACHED, 19L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.COMPLETED, 20L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.FAILED, 21L);

        metricService.onTaskStatusesCountUpdateEvent(new TaskStatusesCountUpdatedEvent(expectedCurrentTaskStatusesCount));

        return expectedCurrentTaskStatusesCount;
    }

}
