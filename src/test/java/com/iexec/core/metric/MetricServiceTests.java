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

import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class MetricServiceTests {

    @Mock
    private WorkerService workerService;
    @Mock
    private TaskService taskService;

    @InjectMocks
    private MetricService metricService;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldGetPlatformMetrics() {
        List<Worker> aliveWorkers = List.of(new Worker());
        when(workerService.getAliveWorkers()).thenReturn(aliveWorkers);
        when(workerService.getAliveTotalCpu()).thenReturn(1);
        when(workerService.getAliveAvailableCpu()).thenReturn(1);
        when(workerService.getAliveTotalGpu()).thenReturn(1);
        when(workerService.getAliveAvailableGpu()).thenReturn(1);
        when(taskService.findByCurrentStatus(TaskStatus.COMPLETED))
                .thenReturn(List.of());

        PlatformMetric metric = metricService.getPlatformMetrics();
        assertThat(metric.getAliveWorkers()).isEqualTo(aliveWorkers.size());
        assertThat(metric.getAliveTotalCpu()).isEqualTo(1);
        assertThat(metric.getAliveAvailableCpu()).isEqualTo(1);
        assertThat(metric.getAliveTotalGpu()).isEqualTo(1);
        assertThat(metric.getAliveAvailableGpu()).isEqualTo(1);
        assertThat(metric.getCompletedTasks()).isZero();
    }

}
