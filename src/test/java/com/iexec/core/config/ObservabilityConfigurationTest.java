/*
 * Copyright 2023-2023 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.info.BuildProperties;

class ObservabilityConfigurationTest {

    @Mock
    private BuildProperties buildProperties;

    @BeforeAll
    static void initRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
    }

    @AfterEach
    void afterEach() {
        Metrics.globalRegistry.clear();
    }

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldReturnInfoGauge() {
        final String version = "1.1.0";
        final String name = "iexec-core";
        Mockito.when(buildProperties.getVersion()).thenReturn(version);
        Mockito.when(buildProperties.getName()).thenReturn(name);

        final ObservabilityConfiguration observabilityConfiguration = new ObservabilityConfiguration(buildProperties);
        Assertions.assertThat(observabilityConfiguration).isNotNull();
        final Gauge info = Metrics.globalRegistry.find(ObservabilityConfiguration.METRIC_INFO_GAUGE_NAME).gauge();
        Assertions.assertThat(info).isNotNull();
        Assertions.assertThat(info.getId()).isNotNull();
        Assertions.assertThat(info.getId().getTag(ObservabilityConfiguration.METRIC_INFO_LABEL_APP_NAME)).isEqualTo(name);
        Assertions.assertThat(info.getId().getTag(ObservabilityConfiguration.METRIC_INFO_LABEL_APP_VERSION)).isEqualTo(version);
    }
}

