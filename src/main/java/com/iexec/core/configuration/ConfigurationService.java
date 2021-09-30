/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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
import org.springframework.stereotype.Service;

import java.math.BigInteger;

@Service
public class ConfigurationService {

    private final ConfigurationRepository configurationRepository;
    private final ReplayConfigurationRepository replayConfigurationRepository;
    private final ChainConfig chainConfig;

    public ConfigurationService(ConfigurationRepository configurationRepository,
                                ReplayConfigurationRepository replayConfigurationRepository,
                                ChainConfig chainConfig) {
        this.configurationRepository = configurationRepository;
        this.replayConfigurationRepository = replayConfigurationRepository;
        this.chainConfig = chainConfig;
    }

    private Configuration getConfiguration() {
        if (configurationRepository.count() > 0)
            return configurationRepository.findAll().get(0);

        return configurationRepository.save(
            Configuration
                    .builder()
                    .lastSeenBlockWithDeal(BigInteger.valueOf(chainConfig.getStartBlockNumber()))
                    .build());
    }

    public BigInteger getLastSeenBlockWithDeal() {
        return this.getConfiguration().getLastSeenBlockWithDeal();
    }

    public void setLastSeenBlockWithDeal(BigInteger lastBlockNumber) {
        Configuration configuration = this.getConfiguration();
        configuration.setLastSeenBlockWithDeal(lastBlockNumber);
        configurationRepository.save(configuration);
    }

    private ReplayConfiguration getReplayConfiguration() {
        if (replayConfigurationRepository.count() > 0)
            return replayConfigurationRepository.findAll().get(0);

        return replayConfigurationRepository.save(
                ReplayConfiguration
                        .builder()
                        .fromBlockNumber(BigInteger.valueOf(chainConfig.getStartBlockNumber()))
                        .build());
    }

    public BigInteger getFromReplay() {
        return this.getReplayConfiguration().getFromBlockNumber();
    }

    public void setFromReplay(BigInteger fromReplay) {
        ReplayConfiguration replayConfiguration = this.getReplayConfiguration();
        replayConfiguration.setFromBlockNumber(fromReplay);
        replayConfigurationRepository.save(replayConfiguration);
    }

}
