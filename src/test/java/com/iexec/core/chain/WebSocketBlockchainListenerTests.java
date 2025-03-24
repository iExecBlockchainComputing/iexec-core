/*
 * Copyright 2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.chain;

import com.iexec.core.metric.MetricService;
import com.iexec.core.metric.PlatformMetric;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.iexec.core.chain.WebSocketBlockchainListener.LATEST_BLOCK_METRIC_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest
class WebSocketBlockchainListenerTests {
    private static final String CHAIN_SVC_NAME = "chain";
    private static final int CHAIN_SVC_PORT = 8545;
    private static final String CONFIG_SVC_NAME = "config-server";
    private static final int CONFIG_SVC_PORT = 8080;
    private static final String MONGO_SVC_NAME = "mongo";
    private static final int MONGO_SVC_PORT = 27017;

    @Container
    static ComposeContainer environment = new ComposeContainer(new File("docker-compose.yml"))
            .withExposedService(CHAIN_SVC_NAME, CHAIN_SVC_PORT, Wait.forListeningPort())
            .withExposedService(CONFIG_SVC_NAME, CONFIG_SVC_PORT, Wait.forListeningPort())
            .withExposedService(MONGO_SVC_NAME, MONGO_SVC_PORT, Wait.forListeningPort())
            .withPull(true);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("sms.scone", () -> "");
        registry.add("sms.gramine", () -> "");
        registry.add("chain.node-address", () -> getServiceUrl(
                environment.getServiceHost(CHAIN_SVC_NAME, CHAIN_SVC_PORT),
                environment.getServicePort(CHAIN_SVC_NAME, CHAIN_SVC_PORT))
        );
        registry.add("config-server.url", () -> getServiceUrl(
                environment.getServiceHost(CONFIG_SVC_NAME, CONFIG_SVC_PORT),
                environment.getServicePort(CONFIG_SVC_NAME, CONFIG_SVC_PORT))
        );
        registry.add("sprint.data.mongodb.host", () -> environment.getServiceHost(MONGO_SVC_NAME, MONGO_SVC_PORT));
        registry.add("spring.data.mongodb.port", () -> environment.getServicePort(MONGO_SVC_NAME, MONGO_SVC_PORT));
    }

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private MetricService metricService;

    @Autowired
    private Web3jService web3jService;

    private static String getServiceUrl(String serviceHost, int servicePort) {
        return "http://" + serviceHost + ":" + servicePort;
    }

    @Test
    void shouldConnect() {
        await().atMost(10L, TimeUnit.SECONDS)
                .until(() -> Objects.requireNonNull(meterRegistry.find(LATEST_BLOCK_METRIC_NAME).gauge()).value() != 0.0);
        final Long latestBlockNumber = (long) Objects.requireNonNull(meterRegistry.find(LATEST_BLOCK_METRIC_NAME).gauge()).value();
        assertThat(latestBlockNumber).isEqualTo(web3jService.getLatestBlockNumber());
        assertThat(metricService.getPlatformMetrics())
                .extracting(PlatformMetric::getLatestBlockMetric)
                .extracting(PlatformMetric.LatestBlockMetric::blockNumber)
                .isEqualTo(latestBlockNumber);
    }

}
