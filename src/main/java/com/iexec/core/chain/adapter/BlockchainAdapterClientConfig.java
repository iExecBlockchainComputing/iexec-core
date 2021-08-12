/*
 * Copyright 2021 IEXEC BLOCKCHAIN TECH
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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class BlockchainAdapterClientConfig {

    @Value("${blockchain-adapter.host}")
    private String blockchainAdapterHost;
    @Value("${blockchain-adapter.port}")
    private int blockchainAdapterPort;
    @Value("${blockchain-adapter.user.name}")
    private String blockchainAdapterUsername;
    @Value("${blockchain-adapter.user.password}")
    private String blockchainAdapterPassword;

    public String getBlockchainAdapterUrl() {
        return buildHostUrl(blockchainAdapterHost, blockchainAdapterPort);
    }

    public String getBlockchainAdapterUsername() {
        return blockchainAdapterUsername;
    }

    public String getBlockchainAdapterPassword() {
        return blockchainAdapterPassword;
    }

    private String buildHostUrl(String host, int port) {
        return "http://" + host + ":" + port;
    }

}
