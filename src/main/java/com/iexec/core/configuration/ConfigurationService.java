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
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
public class ConfigurationService implements CommandLineRunner {

    private final ConfigurationRepository configurationRepository;
    private final ReplayConfigurationRepository replayConfigurationRepository;
    private final ChainConfig chainConfig;

    private Configuration configuration;
    private ReplayConfiguration replayConfiguration;

    public ConfigurationService(ConfigurationRepository configurationRepository,
                                ReplayConfigurationRepository replayConfigurationRepository,
                                ChainConfig chainConfig) {
        this.configurationRepository = configurationRepository;
        this.replayConfigurationRepository = replayConfigurationRepository;
        this.chainConfig = chainConfig;
    }

    /**
     * Update last scanned block in configuration.
     *
     * @param event Event containing last block number
     */
    @EventListener
    void setLastScannedBlock(final LatestBlockEvent event) {
        final Instant now = Instant.now();
        if (now.isAfter(configuration.getLastUpdate().plus(1L, ChronoUnit.HOURS))) {
            updateConfiguration(BigInteger.valueOf(event.getBlockNumber()));
        }
    }

    public BigInteger getLastSeenBlockWithDeal() {
        return configuration.getLastSeenBlockWithDeal();
    }

    public void setLastSeenBlockWithDeal(final BigInteger lastBlockNumber) {
        updateConfiguration(lastBlockNumber);
    }

    private synchronized void updateConfiguration(final BigInteger lastBlockNumber) {
        configuration.setLastSeenBlockWithDeal(lastBlockNumber);
        configuration.setLastUpdate(Instant.now());
        configuration = configurationRepository.save(configuration);
    }

    public BigInteger getFromReplay() {
        return replayConfiguration.getFromBlockNumber();
    }

    public void setFromReplay(BigInteger fromReplay) {
        replayConfiguration.setFromBlockNumber(fromReplay);
        replayConfiguration = replayConfigurationRepository.save(replayConfiguration);
    }

    @Override
    public void run(String... args) throws Exception {
        final String messageDetails = String.format("[start:block:%s]", chainConfig.getStartBlockNumber());
        final Instant now = Instant.now();
        if (configurationRepository.count() == 0) {
            log.info("Creating configuration {}", messageDetails);
            configuration = configurationRepository.save(
                    Configuration
                            .builder()
                            .lastSeenBlockWithDeal(BigInteger.valueOf(chainConfig.getStartBlockNumber()))
                            .lastUpdate(now)
                            .build());
        } else {
            configuration = configurationRepository.findAll().get(0);
            if (chainConfig.getStartBlockNumber() > configuration.getLastSeenBlockWithDeal().longValue()) {
                log.info("Updating configuration {}", messageDetails);
                configuration.setLastSeenBlockWithDeal(BigInteger.valueOf(chainConfig.getStartBlockNumber()));
                configuration.setLastUpdate(now);
                configuration = configurationRepository.save(configuration);
            } else {
                log.info("Keeping current configuration [start-block:{}]", configuration.getLastSeenBlockWithDeal());
            }
        }
        if (replayConfigurationRepository.count() == 0) {
            log.info("Creating replay configuration {}", messageDetails);
            replayConfiguration = replayConfigurationRepository.save(
                    ReplayConfiguration
                            .builder()
                            .fromBlockNumber(BigInteger.valueOf(chainConfig.getStartBlockNumber()))
                            .lastUpdate(now)
                            .build());
        } else {
            replayConfiguration = replayConfigurationRepository.findAll().get(0);
            if (chainConfig.getStartBlockNumber() > replayConfiguration.getFromBlockNumber().longValue()) {
                log.info("Updating replay configuration {}", messageDetails);
                replayConfiguration.setFromBlockNumber(BigInteger.valueOf(chainConfig.getStartBlockNumber()));
                replayConfiguration.setLastUpdate(now);
                replayConfiguration = replayConfigurationRepository.save(replayConfiguration);
            } else {
                log.info("Keeping current replay configuration [start-block:{}]", replayConfiguration.getFromBlockNumber());
            }
        }
    }

}
