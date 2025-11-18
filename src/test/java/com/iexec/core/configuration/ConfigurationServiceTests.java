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

package com.iexec.core.configuration;

import com.iexec.core.chain.ChainConfig;
import com.iexec.core.chain.event.LatestBlockEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@TestPropertySource(properties = {"mongock.enabled=false"})
@Testcontainers
class ConfigurationServiceTests {

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse(System.getProperty("mongo.image")));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.host", mongoDBContainer::getHost);
        registry.add("spring.data.mongodb.port", () -> mongoDBContainer.getMappedPort(27017));
    }

    @Autowired
    private ConfigurationRepository configurationRepository;
    @Autowired
    private ReplayConfigurationRepository replayConfigurationRepository;

    private ConfigurationService configurationService;

    @BeforeEach
    void init() {
        configurationRepository.deleteAll();
        replayConfigurationRepository.deleteAll();
    }

    private void initService(long startBlockNumber) throws Exception {
        final ChainConfig chainConfig = new ChainConfig(65535, true, "", Duration.ofSeconds(5), Duration.ofSeconds(30), "", "0x0", startBlockNumber, 1.0f, 0);
        configurationService = new ConfigurationService(configurationRepository, replayConfigurationRepository, chainConfig);
        configurationService.run();
    }

    // region setLastScannedBlock
    @Test
    void shouldUpdateLastSeenBlockWithDeal() {
        final Instant now = Instant.now();
        final Configuration configuration = Configuration.builder()
                .lastSeenBlockWithDeal(BigInteger.ONE)
                .lastUpdate(now.minus(90L, ChronoUnit.MINUTES))
                .build();
        configurationRepository.save(configuration);
        configurationService = new ConfigurationService(configurationRepository, null, null);
        ReflectionTestUtils.setField(configurationService, "configuration", configuration);
        configurationService.setLastScannedBlock(new LatestBlockEvent(this, 10, "", now.getEpochSecond()));
        assertThat(configurationRepository.count()).isOne();
        final Configuration savedConfiguration = configurationRepository.findAll().get(0);
        assertThat(savedConfiguration.getLastSeenBlockWithDeal()).isEqualTo(BigInteger.TEN);
        assertThat(savedConfiguration.getLastUpdate()).isBetween(now, Instant.now());
    }

    @Test
    void shouldNotUpdateLastSeenBlockWithDeal() {
        final Instant now = Instant.now();
        final Configuration configuration = Configuration.builder()
                .lastSeenBlockWithDeal(BigInteger.ONE)
                .lastUpdate(now.minus(30L, ChronoUnit.MINUTES))
                .build();
        configurationRepository.save(configuration);
        configurationService = new ConfigurationService(configurationRepository, null, null);
        ReflectionTestUtils.setField(configurationService, "configuration", configuration);
        configurationService.setLastScannedBlock(new LatestBlockEvent(this, 10, "", now.getEpochSecond()));
        assertThat(configurationRepository.count()).isOne();
        final Configuration savedConfiguration = configurationRepository.findAll().get(0);
        assertThat(savedConfiguration.getLastSeenBlockWithDeal()).isEqualTo(BigInteger.ONE);
        assertThat(savedConfiguration.getLastUpdate()).isBetween(now.minus(1L, ChronoUnit.HOURS), now);
    }
    // endregion

    // region lastSeenBlockWithDeal
    @Test
    void shouldGetLastSeenBlockWithDealFromDatabase() throws Exception {
        initService(10);
        final BigInteger lastSeenBlock = configurationService.getLastSeenBlockWithDeal();

        assertThat(lastSeenBlock).isEqualTo(BigInteger.TEN);
    }

    @Test
    void shouldGetZeroAsLastSeenBlockWithDeal() throws Exception {
        initService(0);
        final BigInteger lastSeenBlock = configurationService.getLastSeenBlockWithDeal();

        assertThat(lastSeenBlock).isEqualTo(BigInteger.ZERO);
    }

    @Test
    void shouldSetLastSeenBlockWithDeal() throws Exception {
        initService(1);
        configurationService.setLastSeenBlockWithDeal(BigInteger.TEN);

        assertThat(configurationService.getLastSeenBlockWithDeal()).isEqualTo(BigInteger.TEN);
    }
    // endregion

    // region replayConfiguration
    @Test
    void shouldGetFromReplayFromDatabase() throws Exception {
        initService(10);
        BigInteger fromReplay = configurationService.getFromReplay();

        assertThat(fromReplay).isEqualTo(BigInteger.TEN);
    }

    @Test
    void shouldGetZeroAsFromReplay() throws Exception {
        initService(0);
        BigInteger fromReplay = configurationService.getFromReplay();

        assertThat(fromReplay).isEqualTo(BigInteger.ZERO);
    }

    @Test
    void shouldSetFromReplay() throws Exception {
        initService(1);
        configurationService.setFromReplay(BigInteger.TEN);

        assertThat(configurationService.getFromReplay()).isEqualTo(BigInteger.TEN);
    }
    // endregion

    // region initialization
    @Test
    void shouldNotUpdateWhenConfigBeforeStartBlockNumber() throws Exception {
        final Configuration configuration = Configuration.builder()
                .lastSeenBlockWithDeal(BigInteger.TEN)
                .build();
        configurationRepository.save(configuration);
        final ReplayConfiguration replayConfiguration = ReplayConfiguration.builder()
                .fromBlockNumber(BigInteger.TEN)
                .build();
        replayConfigurationRepository.save(replayConfiguration);
        initService(0);
        assertThat(configurationService.getLastSeenBlockWithDeal()).isEqualTo(BigInteger.TEN);
        assertThat(configurationService.getFromReplay()).isEqualTo(BigInteger.TEN);
    }

    @Test
    void shouldUpdateWhenConfigAfterStartBlockNumber() throws Exception {
        final Configuration configuration = Configuration.builder()
                .lastSeenBlockWithDeal(BigInteger.TEN)
                .build();
        configurationRepository.save(configuration);
        final ReplayConfiguration replayConfiguration = ReplayConfiguration.builder()
                .fromBlockNumber(BigInteger.TEN)
                .build();
        replayConfigurationRepository.save(replayConfiguration);
        initService(100);
        assertThat(configurationService.getLastSeenBlockWithDeal()).isEqualTo(BigInteger.valueOf(100));
        assertThat(configurationService.getFromReplay()).isEqualTo(BigInteger.valueOf(100));
    }
    // endregion

}
