/*
 * Copyright 2024-2025 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.config.PublicChainConfig;
import com.iexec.commons.poco.chain.SignerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.web3j.crypto.WalletUtils;

import java.io.File;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WalletConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner();
    @TempDir
    private File tempWalletDir;

    @Test
    void shouldCreateBeans() throws Exception {
        final String tempWalletName = WalletUtils.generateFullNewWalletFile("changeit", tempWalletDir);
        final String tempWalletPath = tempWalletDir.getAbsolutePath() + File.separator + tempWalletName;
        runner.withPropertyValues("chain.out-of-service-threshold=30s", "chain.node-address=http://localhost:8545", "chain.pool-address=0x1", "chain.start-block-number=0", "chain.gas-price-multiplier=1.0", "chain.gas-price-cap=0")
                .withBean(PropertySourcesPlaceholderConfigurer.class,
                        PropertySourcesPlaceholderConfigurer::new)
                .withInitializer(context ->
                        context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
                .withBean(IexecHubService.class)
                .withBean(PublicChainConfig.class, 65535, true, "http://localhost:8545", "0xC129e7917b7c7DeDfAa5Fff1FB18d5D7050fE8ca", Duration.ofSeconds(5))
                .withBean(WalletConfiguration.class, tempWalletPath, "changeit")
                .withBean(Web3jService.class)
                .withUserConfiguration(ChainConfig.class)
                .run(context -> assertThat(context)
                        .hasSingleBean(ChainConfig.class)
                        .hasSingleBean(IexecHubService.class)
                        .hasSingleBean(SignerService.class)
                        .hasSingleBean(WalletConfiguration.class)
                        .hasSingleBean(Web3jService.class));
    }
}
