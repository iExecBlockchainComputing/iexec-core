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

package com.iexec.core.configuration;

import com.iexec.common.config.ConfigServerClient;
import com.iexec.common.config.ConfigServerClientBuilder;
import com.iexec.common.config.PublicChainConfig;
import feign.Logger;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

@Data
@ConfigurationProperties(prefix = "config-server")
public class ConfigServerClientConfig {

    private final String protocol;
    private final String host;
    private final int port;

    public String getUrl() {
        return protocol + "://" + host + ":" + port;
    }

    @Bean
    public ConfigServerClient configServerClient() {
        return ConfigServerClientBuilder.getInstance(
                Logger.Level.NONE, getUrl());
    }

    @Bean
    public PublicChainConfig publicChainConfig(ConfigServerClient apiClient) {
        return apiClient.getPublicChainConfig();
    }

    @Bean
    public int getChainId(PublicChainConfig publicChainConfig) {
        return publicChainConfig.getChainId();
    }
}
