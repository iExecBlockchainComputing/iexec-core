/*
 * Copyright 2024 IEXEC BLOCKCHAIN TECH
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
import lombok.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;

@Value
@ConfigurationProperties(prefix = "wallet")
public class WalletConfiguration {
    String encryptedFilePath;
    String password;

    @Bean
    SignerService signerService(Web3jService web3jService, PublicChainConfig publicChainConfig) throws Exception {
        return new SignerService(web3jService.getWeb3j(), publicChainConfig.getChainId(), password, encryptedFilePath);
    }
}
