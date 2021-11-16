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

    @Value("${blockchain-adapter.protocol}")
    private String protocol;
    @Value("${blockchain-adapter.host}")
    private String host;
    @Value("${blockchain-adapter.port}")
    private int port;
    @Value("${blockchain-adapter.user.name}")
    private String username;
    @Value("${blockchain-adapter.user.password}")
    private String password;

    public String getUrl() {
        return buildHostUrl(protocol, host, port);
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    private String buildHostUrl(String protocol, String host, int port) {
        return protocol + "://" + host + ":" + port;
    }

}
