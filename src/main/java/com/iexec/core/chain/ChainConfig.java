/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ChainConfig {

    @Value("#{publicChainConfig.chainId}")
    private int chainId;

    @Value("#{publicChainConfig.sidechain}")
    private boolean isSidechain;

    @Value("#{publicChainConfig.iexecHubContractAddress}")
    private String hubAddress;

    @Value("#{publicChainConfig.blockTime}")
    private Duration blockTime;

    @Value("${chain.privateAddress}")
    private String privateChainAddress;

    @Value("${chain.poolAddress}")
    private String poolAddress;

    @Value("${chain.startBlockNumber}")
    private long startBlockNumber;

    @Value("${chain.gasPriceMultiplier}")
    private float gasPriceMultiplier;

    @Value("${chain.gasPriceCap}")
    private long gasPriceCap;

}
