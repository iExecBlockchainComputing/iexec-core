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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfiguration {

    public static final String METRIC_INFO_GAUGE_NAME = "iexec.app.info";
    public static final String METRIC_INFO_GAUGE_DESC = "A metric to expose version and application name.";
    public static final String METRIC_INFO_LABEL_APP_NAME = "app_name";
    public static final String METRIC_INFO_LABEL_APP_VERSION = "app_version";

    ObservabilityConfiguration(@Autowired BuildProperties buildProperties) {

        Gauge.builder(METRIC_INFO_GAUGE_NAME, 1.0, n -> n)
                .strongReference(true)
                .description(METRIC_INFO_GAUGE_DESC)
                .tags(METRIC_INFO_LABEL_APP_VERSION, buildProperties.getVersion(),
                        METRIC_INFO_LABEL_APP_NAME, buildProperties.getName())
                .register(Metrics.globalRegistry);
    }
}

