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
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
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
    @Positive(message = "Chain id must be greater than 0")
    @NotNull(message = "Chain id must not be null")
    private int chainId;

    @Value("#{publicChainConfig.sidechain}")
    private boolean sidechain;

    @Value("#{publicChainConfig.iexecHubContractAddress}")
    @ValidNonZeroEthereumAddress(message = "Hub address must be a valid non zero Ethereum address")
    private String hubAddress;

    @Value("#{publicChainConfig.blockTime}")
    @DurationMin(millis = 100, message = "Block time must be greater than 100ms")
    @DurationMax(seconds = 20, message = "Block time must be less than 20s")
    @NotNull(message = "Block time must not be null")
    private Duration blockTime;

    @Value("${chain.node-address}")
    @URL(message = "Node address must be a valid URL")
    @NotEmpty(message = "Node address must not be empty")
    private String nodeAddress;

    @Value("${chain.pool-address}")
    private String poolAddress;

    @Value("${chain.start-block-number}")
    @PositiveOrZero(message = "Start Block Number must be greater or equal to 0")
    private long startBlockNumber;

    @Value("${chain.gas-price-multiplier}")
    @Positive(message = "Gas price multiplier must be greater than 0")
    private float gasPriceMultiplier;

    @Value("${chain.gas-price-cap}")
    @PositiveOrZero(message = "Gas price cap must be greater or equal to 0")
    private long gasPriceCap;

}
