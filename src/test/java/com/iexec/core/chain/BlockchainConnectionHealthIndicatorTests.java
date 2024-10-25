/*
 * Copyright 2023-2024 IEXEC BLOCKCHAIN TECH
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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlockchainConnectionHealthIndicatorTests {
    private static final int OUT_OF_SERVICE_THRESHOLD = 4;
    private static final Duration BLOCK_TIME = Duration.ofSeconds(5);
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1), ZoneId.systemDefault());

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private Web3jService web3jService;
    @Mock
    private ChainConfig chainConfig;
    @Mock
    private ScheduledExecutorService executor;

    private BlockchainConnectionHealthIndicator blockchainConnectionHealthIndicator;

    @BeforeEach
    void init() {
        when(chainConfig.getBlockTime()).thenReturn(BLOCK_TIME);

        this.blockchainConnectionHealthIndicator = new BlockchainConnectionHealthIndicator(
                applicationEventPublisher,
                web3jService,
                chainConfig,
                executor,
                CLOCK
        );
    }

    // region scheduleMonitoring
    @Test
    void shouldScheduleMonitoring() {
        blockchainConnectionHealthIndicator.scheduleMonitoring();

        Mockito.verify(executor).scheduleAtFixedRate(
                any(),
                eq(0L),
                eq(5L),
                eq(TimeUnit.SECONDS)
        );
    }
    // endregion

    // region checkConnection

    /**
     * Returns a list of inputs, initial states and expected outputs for the `checkConnection` method
     * in the following order:
     * <ol>
     * <li>consecutiveFailures (initial state)
     * <li>outOfService (initial state)
     * <li>firstFailure (initial state)
     * <li>latestBlockNumber (input)
     * <li>consecutiveFailures (output)
     * <li>outOfService (output)
     * <li>firstFailure (output)
     * </ol>
     */
    static Stream<Arguments> checkConnectionParameters() {
        return Stream.of(
                // Should get latest block number and reset `firstFailure`
                Arguments.of(0, null, 1L, false, null),
                Arguments.of(0, null, 5L, false, null),
                Arguments.of(0, null, 100L, false, null),
                Arguments.of(0, null, 5_000L, false, null),
                Arguments.of(1, LocalDateTime.now(CLOCK), 1L, false, null),

                // Should not get latest block number and become OUT-OF-SERVICE
                Arguments.of(0, null, 0L, true, LocalDateTime.now(CLOCK)),
                Arguments.of(1, LocalDateTime.now(CLOCK), 0L, true, LocalDateTime.now(CLOCK)),
                Arguments.of(2, LocalDateTime.now(CLOCK), 0L, true, LocalDateTime.now(CLOCK)),
                Arguments.of(3, LocalDateTime.now(CLOCK), 0L, true, LocalDateTime.now(CLOCK)),
                Arguments.of(4, LocalDateTime.now(CLOCK), 0L, true, LocalDateTime.now(CLOCK)),
                Arguments.of(50, LocalDateTime.now(CLOCK), 0L, true, LocalDateTime.now(CLOCK)),

                // Should get latest block number and exit OUT-OF-SERVICE
                Arguments.of(4, LocalDateTime.now(CLOCK), 1L, false, null),
                Arguments.of(5, LocalDateTime.now(CLOCK), 1L, false, null),
                Arguments.of(50, LocalDateTime.now(CLOCK), 1L, false, null)
        );
    }

    @ParameterizedTest
    @MethodSource("checkConnectionParameters")
    void checkConnection(int previousConsecutiveFailures,
                         LocalDateTime previousFirstFailure,
                         long latestBlockNumber,
                         boolean expectedOutOfService,
                         LocalDateTime expectedFirstFailure) {
        setConsecutiveFailures(previousConsecutiveFailures);
        setFirstFailure(previousFirstFailure);

        when(web3jService.getLatestBlockNumber()).thenReturn(latestBlockNumber);

        blockchainConnectionHealthIndicator.checkConnection();

        final boolean outOfService = blockchainConnectionHealthIndicator.isOutOfService();
        final LocalDateTime firstFailure = blockchainConnectionHealthIndicator.getFirstFailure();

        Assertions.assertThat(outOfService).isEqualTo(expectedOutOfService);
        Assertions.assertThat(firstFailure).isEqualTo(expectedFirstFailure);

        verify(web3jService).getLatestBlockNumber();
    }
    // endregion

    // region health
    @Test
    void shouldReturnOutOfService() {
        final LocalDateTime firstFailure = LocalDateTime.now(CLOCK);

        setConsecutiveFailures(OUT_OF_SERVICE_THRESHOLD);
        setFirstFailure(firstFailure);

        final Health expectedHealth = Health.outOfService()
                .withDetail("consecutiveFailures", OUT_OF_SERVICE_THRESHOLD)
                .withDetail("pollingInterval", Duration.ofSeconds(5))
                .withDetail("firstFailure", firstFailure)
                .build();

        final Health health = blockchainConnectionHealthIndicator.health();
        Assertions.assertThat(health).isEqualTo(expectedHealth);
    }

    @Test
    void shouldReturnUpAndNoFirstFailure() {
        setConsecutiveFailures(0);
        setFirstFailure(null);

        final Health expectedHealth = Health.up()
                .withDetail("consecutiveFailures", 0)
                .withDetail("pollingInterval", Duration.ofSeconds(5))
                .build();

        final Health health = blockchainConnectionHealthIndicator.health();
        Assertions.assertThat(health).isEqualTo(expectedHealth);
    }

    @Test
    void shouldReturnUpButWithFirstFailure() {
        final LocalDateTime firstFailure = LocalDateTime.now(CLOCK);

        setConsecutiveFailures(1);
        setFirstFailure(firstFailure);

        final Health expectedHealth = Health.outOfService()
                .withDetail("consecutiveFailures", 1)
                .withDetail("pollingInterval", Duration.ofSeconds(5))
                .withDetail("firstFailure", firstFailure)
                .build();

        final Health health = blockchainConnectionHealthIndicator.health();
        Assertions.assertThat(health).isEqualTo(expectedHealth);
    }
    // endregion

    // region utils
    private void setConsecutiveFailures(int consecutiveFailures) {
        ReflectionTestUtils.setField(blockchainConnectionHealthIndicator, "consecutiveFailures", consecutiveFailures);
    }

    private void setFirstFailure(LocalDateTime firstFailure) {
        ReflectionTestUtils.setField(blockchainConnectionHealthIndicator, "firstFailure", firstFailure);
    }
    // endregion
}
