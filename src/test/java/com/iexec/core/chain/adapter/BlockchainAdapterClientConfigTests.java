/*
 * Copyright 2021-2024 IEXEC BLOCKCHAIN TECH
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
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Slf4j
@EnableConfigurationProperties(value = BlockchainAdapterClientConfig.class)
class BlockchainAdapterClientConfigTests {

    @Test
    void checkURL() {
        final BlockchainAdapterClientConfig blockchainAdapterClientConfig = new BlockchainAdapterClientConfig("http", "localhost", 8080, "", "");
        assertThat(blockchainAdapterClientConfig.getUrl()).isEqualTo("http://localhost:8080");
    }
}
