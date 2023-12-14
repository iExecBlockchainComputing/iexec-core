/*
 * Copyright 2021-2023 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.chain.adapter;

import com.iexec.common.config.PublicChainConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Slf4j
@ExtendWith(SpringExtension.class)
@EnableConfigurationProperties(value = BlockchainAdapterClientConfig.class)
@Testcontainers
class BlockchainAdapterClientConfigTests {
    private static final int WIREMOCK_PORT = 8080;

    @Autowired
    private PublicChainConfig chainConfig;

    @Container
    static final GenericContainer<?> wmServer = new GenericContainer<>("wiremock/wiremock:3.3.1")
            .withClasspathResourceMapping("wiremock", "/home/wiremock", BindMode.READ_ONLY)
            .withExposedPorts(WIREMOCK_PORT);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("blockchain-adapter.protocol", () -> "http");
        registry.add("blockchain-adapter.host", () -> "localhost");
        registry.add("blockchain-adapter.port", () -> wmServer.getMappedPort(WIREMOCK_PORT));
    }

    @Test
    void checkChainConfigInitialization() {
        PublicChainConfig expectedConfig = PublicChainConfig.builder()
                .chainId(255)
                .chainNodeUrl("http://localhost:8545")
                .blockTime(Duration.ofSeconds(5L))
                .sidechain(true)
                .build();
        assertThat(chainConfig).isEqualTo(expectedConfig);
    }

}
