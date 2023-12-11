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

import com.iexec.blockchain.api.BlockchainAdapterApiClient;
import com.iexec.blockchain.api.BlockchainAdapterApiClientBuilder;
import com.iexec.common.config.PublicChainConfig;
import feign.Logger;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.context.annotation.Bean;

@Data
@ConstructorBinding
@ConfigurationProperties(prefix = "blockchain-adapter")
public class BlockchainAdapterClientConfig {

    public static final int WATCH_PERIOD_SECONDS = 1;//To tune
    public static final int MAX_ATTEMPTS = 50;

    private final String protocol;
    private final String host;
    private final int port;
    // TODO improve property names before next major version
    @Value("${blockchain-adapter.user.name}")
    private final String username;
    @Value("${blockchain-adapter.user.password}")
    private final String password;

    public String getUrl() {
        return buildHostUrl(protocol, host, port);
    }

    private String buildHostUrl(String protocol, String host, int port) {
        return protocol + "://" + host + ":" + port;
    }

    @Bean
    public BlockchainAdapterApiClient blockchainAdapterClient() {
        return BlockchainAdapterApiClientBuilder.getInstanceWithBasicAuth(
                Logger.Level.NONE, getUrl(), username, password);
    }

    @Bean
    public PublicChainConfig publicChainConfig(BlockchainAdapterApiClient apiClient) {
        return apiClient.getPublicChainConfig();
    }

    @Bean
    public int getChainId(PublicChainConfig publicChainConfig) {
        return publicChainConfig.getChainId();
    }

}
