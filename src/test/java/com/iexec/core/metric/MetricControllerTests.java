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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import static org.mockito.Mockito.when;

public class MetricControllerTests {

    @Mock
    private MetricService metricService;

    @InjectMocks
    private MetricController metricController;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldGetMetrics() {
        PlatformMetric metric = PlatformMetric.builder()
                .aliveAvailableCpu(1)
                .aliveAvailableGpu(1)
                .build();
        when(metricService.getPlatformMetrics()).thenReturn(metric);
        Assertions.assertThat(
                metricController.getPlatformMetric().getStatusCode())
        .isEqualTo(HttpStatus.OK);
        Assertions.assertThat(
                metricController.getPlatformMetric().getBody())
        .isEqualTo(metric);
    }
}
