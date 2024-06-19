/*
 * Copyright 2023-2024 IEXEC BLOCKCHAIN TECH
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class PublicConfigurationTests {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldSerializeAndDeserialize() throws JsonProcessingException {
        PublicConfiguration config = PublicConfiguration.builder().build();
        String jsonString = mapper.writeValueAsString(config);
        assertThat(jsonString).isEqualTo("{\"workerPoolAddress\":null,\"schedulerPublicAddress\":null," +
                "\"blockchainAdapterUrl\":null,\"configServerUrl\":null,\"resultRepositoryURL\":null," +
                "\"askForReplicatePeriod\":0,\"requiredWorkerVersion\":null}");
        PublicConfiguration parsedConfig = mapper.readValue(jsonString, PublicConfiguration.class);
        assertThat(parsedConfig).isEqualTo(config);
    }

    @Test
    void shouldDeserializeWhenBlockchainAdapterUrlIsPresentButConfigServerUrlNot() throws JsonProcessingException {

        String jsonString = "{\"workerPoolAddress\":null,\"schedulerPublicAddress\":null," +
                "\"blockchainAdapterUrl\":\"http://localhost:8080\",\"resultRepositoryURL\":null," +
                "\"askForReplicatePeriod\":0,\"requiredWorkerVersion\":null}";

        PublicConfiguration parsedConfig = mapper.readValue(jsonString, PublicConfiguration.class);
        assertThat(parsedConfig.getBlockchainAdapterUrl()).isEqualTo("http://localhost:8080");
        assertThat(parsedConfig.getConfigServerUrl()).isNull();
    }

    @Test
    void shouldDeserializeWhenConfigServerUrlIsPresentButBlockchainAdapterUrlNot() throws JsonProcessingException {

        String jsonString = "{\"workerPoolAddress\":null,\"schedulerPublicAddress\":null," +
                "\"configServerUrl\":\"http://localhost:8080\",\"resultRepositoryURL\":null," +
                "\"askForReplicatePeriod\":0,\"requiredWorkerVersion\":null}";

        PublicConfiguration parsedConfig = mapper.readValue(jsonString, PublicConfiguration.class);
        assertThat(parsedConfig.getConfigServerUrl()).isEqualTo("http://localhost:8080");
        assertThat(parsedConfig.getBlockchainAdapterUrl()).isNull();
    }

    @Test
    void shouldDeserializeWhenConfigServerUrlAndBlockchainAdapterUrlArePresent() throws JsonProcessingException {

        String jsonString = "{\"workerPoolAddress\":null,\"schedulerPublicAddress\":null," +
                "\"configServerUrl\":\"http://localhost:8080\",\"blockchainAdapterUrl\":\"http://localhost:8082\",\"resultRepositoryURL\":null," +
                "\"askForReplicatePeriod\":0,\"requiredWorkerVersion\":null}";

        PublicConfiguration parsedConfig = mapper.readValue(jsonString, PublicConfiguration.class);
        assertThat(parsedConfig.getConfigServerUrl()).isEqualTo("http://localhost:8080");
        assertThat(parsedConfig.getBlockchainAdapterUrl()).isEqualTo("http://localhost:8082");
    }
}
