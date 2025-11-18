/*
 * Copyright 2025 IEXEC BLOCKCHAIN TECH
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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ChainConfigTests {
    private static final String IEXEC_NODE_ADDRESS = "https://bellecour.iex.ec";
    private static final String IEXEC_HUB_ADDRESS = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private static final String POOL_ADDRESS = "poolAddress";

    private Validator validator;

    @BeforeEach
    void setUp() {
        try (final ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void chainIdMustBePositive() {
        final ChainConfig config = new ChainConfig(
                0, // invalid chainId
                false,
                IEXEC_HUB_ADDRESS,
                Duration.ofMillis(100),
                Duration.ofSeconds(30),
                IEXEC_NODE_ADDRESS,
                POOL_ADDRESS,
                0L,
                1.0f,
                100L
        );
        final Set<ConstraintViolation<ChainConfig>> violations = validator.validate(config);
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Chain id must be greater than 0");
    }

    @Test
    void nodeAddressMustBeValidURL() {
        final ChainConfig config = new ChainConfig(
                1,
                false,
                IEXEC_HUB_ADDRESS,
                Duration.ofMillis(100),
                Duration.ofSeconds(30),
                "invalid-url", // invalid URL
                POOL_ADDRESS,
                0L,
                1.0f,
                100L
        );
        final Set<ConstraintViolation<ChainConfig>> violations = validator.validate(config);
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Node address must be a valid URL");
    }

    @Test
    void nodeAddressMustNotBeEmpty() {
        final ChainConfig config = new ChainConfig(
                1,
                false,
                IEXEC_HUB_ADDRESS,
                Duration.ofMillis(100),
                Duration.ofSeconds(30),
                "", // empty nodeAddress
                POOL_ADDRESS,
                0L,
                1.0f,
                100L
        );
        final Set<ConstraintViolation<ChainConfig>> violations = validator.validate(config);
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Node address must not be empty");
    }

    @Test
    void blockTimeMustBeAtLeast100ms() {
        final ChainConfig config = new ChainConfig(
                1,
                false,
                IEXEC_HUB_ADDRESS,
                Duration.ofMillis(99), // less than 100ms
                Duration.ofSeconds(30),
                IEXEC_NODE_ADDRESS,
                POOL_ADDRESS,
                0L,
                1.0f,
                100L
        );
        final Set<ConstraintViolation<ChainConfig>> violations = validator.validate(config);
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Block time must be greater than 100ms");
    }

    @Test
    void blockTimeMustBeAtMost20Seconds() {
        final ChainConfig config = new ChainConfig(
                1,
                false,
                IEXEC_HUB_ADDRESS,
                Duration.ofSeconds(21), // more than 20 seconds
                Duration.ofSeconds(30),
                IEXEC_NODE_ADDRESS,
                POOL_ADDRESS,
                0L,
                1.0f,
                100L
        );
        final Set<ConstraintViolation<ChainConfig>> violations = validator.validate(config);
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Block time must be less than 20s");
    }

    @Test
    void gasPriceMultiplierMustBePositive() {
        final ChainConfig config = new ChainConfig(
                1,
                false,
                IEXEC_HUB_ADDRESS,
                Duration.ofMillis(100),
                Duration.ofSeconds(30),
                IEXEC_NODE_ADDRESS,
                POOL_ADDRESS,
                0L,
                0.0f, // invalid multiplier
                100L
        );
        final Set<ConstraintViolation<ChainConfig>> violations = validator.validate(config);
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Gas price multiplier must be greater than 0");
    }

    @Test
    void gasPriceCapMustBePositiveOrZero() {
        final ChainConfig config = new ChainConfig(
                1,
                false,
                IEXEC_HUB_ADDRESS,
                Duration.ofMillis(100),
                Duration.ofSeconds(30),
                IEXEC_NODE_ADDRESS,
                POOL_ADDRESS,
                0L,
                1.0f,
                -1L // invalid gasPriceCap
        );
        Set<ConstraintViolation<ChainConfig>> violations = validator.validate(config);
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Gas price cap must be greater or equal to 0");
    }

    @Test
    void hubAddressMustBeValidEthereumAddress() {
        final ChainConfig config = new ChainConfig(
                1,
                false,
                "0x0", // invalid address
                Duration.ofMillis(100),
                Duration.ofSeconds(30),
                IEXEC_NODE_ADDRESS,
                POOL_ADDRESS,
                0L,
                1.0f,
                100L
        );
        final Set<ConstraintViolation<ChainConfig>> violations = validator.validate(config);
        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Hub address must be a valid non zero Ethereum address");
    }
}
