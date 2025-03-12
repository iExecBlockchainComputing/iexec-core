/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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

import com.iexec.commons.poco.chain.validation.ValidNonZeroEthereumAddress;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Validated
public class ChainConfig {

    @Value("#{publicChainConfig.chainId}")
    @Positive
    @NotNull
    private int chainId;

    @Value("#{publicChainConfig.sidechain}")
    private boolean sidechain;

    @Value("#{publicChainConfig.iexecHubContractAddress}")
    @ValidNonZeroEthereumAddress
    private String hubAddress;

    @Value("#{publicChainConfig.blockTime}")
    @Positive
    @NotNull
    private Duration blockTime;

    @Value("${chain.private-address}")
    @URL
    @NotEmpty
    private String privateChainAddress;

    @Value("${chain.pool-address}")
    private String poolAddress;

    @Value("${chain.start-block-number}")
    @PositiveOrZero
    private long startBlockNumber;

    @Value("${chain.gas-price-multiplier}")
    @Positive
    private float gasPriceMultiplier;

    @Value("${chain.gas-price-cap}")
    @PositiveOrZero
    private long gasPriceCap;

}
