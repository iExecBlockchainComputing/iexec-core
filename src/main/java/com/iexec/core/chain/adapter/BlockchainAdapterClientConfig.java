/*
 * Copyright 2021-2025 IEXEC BLOCKCHAIN TECH
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

import com.iexec.blockchain.api.BlockchainAdapterApiClient;
import com.iexec.blockchain.api.BlockchainAdapterApiClientBuilder;
import com.iexec.blockchain.api.BlockchainAdapterService;
import feign.Logger;
import jakarta.validation.constraints.NotEmpty;
import lombok.Value;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Value
@Validated
@ConfigurationProperties(prefix = "blockchain-adapter")
public class BlockchainAdapterClientConfig {

    // TODO add configuration parameters for next major version
    public static final int WATCH_PERIOD_SECONDS = 2;
    public static final int MAX_ATTEMPTS = 25;

    @URL(message = "URL must be a valid URL")
    @NotEmpty(message = "URL must not be empty")
    String url;

    BlockchainAdapterAuth auth;

    @Bean
    public BlockchainAdapterApiClient blockchainAdapterClient() {
        return BlockchainAdapterApiClientBuilder.getInstanceWithBasicAuth(
                Logger.Level.NONE, url, auth.username, auth.password);
    }

    @Bean
    public BlockchainAdapterService blockchainAdapterService(BlockchainAdapterApiClient blockchainAdapterClient) {
        return new BlockchainAdapterService(blockchainAdapterClient, Duration.ofSeconds(WATCH_PERIOD_SECONDS), MAX_ATTEMPTS);
    }

    record BlockchainAdapterAuth(String username, String password
    ) {
    }
}
