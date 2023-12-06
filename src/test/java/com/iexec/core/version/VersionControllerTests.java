/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.version;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.info.ProjectInfoAutoConfiguration;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(SpringExtension.class)
@Import(ProjectInfoAutoConfiguration.class)
class VersionControllerTests {

    private VersionController versionController;

    @Autowired
    private BuildProperties buildProperties;

    @BeforeAll
    static void initRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
    }

    @BeforeEach
    void init() {
        versionController = new VersionController(buildProperties);
        versionController.initializeGaugeVersion();
    }

    @AfterEach
    void afterEach() {
        Metrics.globalRegistry.clear();
    }

    @Test
    void testVersionController() {
        assertEquals(ResponseEntity.ok(buildProperties.getVersion()), versionController.getVersion());
    }

    @Test
    void shouldReturnInfoGauge() {
        final Gauge info = Metrics.globalRegistry.find(VersionController.METRIC_INFO_GAUGE_NAME).gauge();
        assertThat(info)
                .isNotNull()
                .extracting(Gauge::getId)
                .isNotNull()
                .extracting(
                        id -> id.getTag(VersionController.METRIC_INFO_LABEL_APP_NAME),
                        id -> id.getTag(VersionController.METRIC_INFO_LABEL_APP_VERSION)
                )
                .containsExactly(buildProperties.getName(), buildProperties.getVersion());
        assertThat(info.value()).isEqualTo(VersionController.METRIC_VALUE);
    }
}

