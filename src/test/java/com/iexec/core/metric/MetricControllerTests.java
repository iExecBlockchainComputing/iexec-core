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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricControllerTests {

    @Mock
    private MetricService metricService;

    @InjectMocks
    private MetricController metricController;

    @Test
    void shouldGetMetrics() {
        PlatformMetric metric = PlatformMetric.builder()
                .aliveRegisteredCpu(1)
                .aliveRegisteredCpu(1)
                .build();
        when(metricService.getPlatformMetrics()).thenReturn(metric);
        assertThat(metricController.getPlatformMetric().getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(metricController.getPlatformMetric().getBody())
                .isEqualTo(metric);
    }
}
