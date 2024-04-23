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

package com.iexec.core.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

/**
 * The {@link #blockchainAdapterUrl} and {@link #configServerUrl} fields are repetitive,
 * so if both are populated, they must contain the same value.
 * Duplicating these fields ensures worker/scheduler compatibility on different v8 versions.
 * In v9, the {@link #blockchainAdapterUrl} field will be removed.
 */
@Value
@Builder
@JsonDeserialize(builder = PublicConfiguration.PublicConfigurationBuilder.class)
public class PublicConfiguration {
    String workerPoolAddress;
    String schedulerPublicAddress;
    /**
     * @deprecated Use {@link #configServerUrl} instead.
     */
    @Deprecated(forRemoval = true)
    String blockchainAdapterUrl;
    String configServerUrl;
    String resultRepositoryURL;
    long askForReplicatePeriod;
    String requiredWorkerVersion;

    @JsonPOJOBuilder(withPrefix = "")
    public static class PublicConfigurationBuilder {
    }
}
