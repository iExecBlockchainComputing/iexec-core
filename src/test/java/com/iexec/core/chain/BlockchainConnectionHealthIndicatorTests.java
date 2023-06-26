package com.iexec.core.chain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.actuate.health.Health;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockchainConnectionHealthIndicatorTests {
    @Mock
    private Web3jService web3jService;
    @Mock
    private ChainConfig chainConfig;
    @Mock
    private ScheduledExecutorService executor;
    private final int pollingIntervalInBlocks = 3;
    private final int maxConsecutiveFailures = 4;
    private final Duration blockTime = Duration.ofSeconds(5);

    private BlockchainConnectionHealthIndicator blockchainConnectionHealthIndicator;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        when(chainConfig.getBlockTime()).thenReturn(blockTime);

        this.blockchainConnectionHealthIndicator = new BlockchainConnectionHealthIndicator(
                web3jService,
                chainConfig,
                pollingIntervalInBlocks,
                maxConsecutiveFailures,
                executor
        );
    }

    // region scheduleMonitoring
    @Test
    void shouldScheduleMonitoring() {
        blockchainConnectionHealthIndicator.scheduleMonitoring();

        Mockito.verify(executor).scheduleAtFixedRate(
                blockchainConnectionHealthIndicator.checkConnectionRunnable,
                0L,
                15L,
                TimeUnit.SECONDS
        );
    }
    // endregion

    // region checkConnection

    /**
     * Returns a list of inputs, initial states and expected outputs for the `checkConnection` method
     * in the following order:
     * <ol>
     *     <li>consecutiveFailures (initial state)</li>
     *     <li>outOfService (initial state)</li>
     *     <li>latestBlockNumber (input)</li>
     *     <li>consecutiveFailures (output)</li>
     *     <li>outOfService (output)</li>
     * </ol>
     */
    static Stream<Arguments> checkConnectionParameters() {
        return Stream.of(
                // Should get latest block number and do nothing more
                Arguments.of(0, false, 1L    , 0, false),
                Arguments.of(0, false, 5L    , 0, false),
                Arguments.of(0, false, 100L  , 0, false),
                Arguments.of(0, false, 5_000L, 0, false),

                // Should not get latest block number and increment consecutive failures (but stays UP)
                Arguments.of(0, false, 0L, 1, false),
                Arguments.of(1, false, 0L, 2, false),
                Arguments.of(2, false, 0L, 3, false),

                // Should not get latest block number and become OUT-OF-SERVICE
                Arguments.of(3 , false, 0L, 4 , true),
                Arguments.of(4 , true , 0L, 5 , true),
                Arguments.of(50, true , 0L, 51, true),

                // Should get latest block number but stay OUT-OF-SERVICE
                Arguments.of(4 , true, 1L, 0, true),
                Arguments.of(5 , true, 1L, 0, true),
                Arguments.of(50, true, 1L, 0, true)
        );
    }

    @ParameterizedTest
    @MethodSource("checkConnectionParameters")
    void checkConnection(int previousConsecutiveFailures,
                         boolean previousOutOfService,
                         long latestBlockNumber,
                         int expectedConsecutiveFailures,
                         boolean expectedOutOfService) {
        setConsecutiveFailures(previousConsecutiveFailures);
        setOufOService(previousOutOfService);

        when(web3jService.getLatestBlockNumber()).thenReturn(latestBlockNumber);

        blockchainConnectionHealthIndicator.checkConnection();

        final Integer consecutiveFailures = getConsecutiveFailures();
        final Boolean outOfService = isOutOfService();

        Assertions.assertThat(consecutiveFailures).isEqualTo(expectedConsecutiveFailures);
        Assertions.assertThat(outOfService).isEqualTo(expectedOutOfService);

        verify(web3jService).getLatestBlockNumber();
    }
    // endregion

    // region health
    @Test
    void shouldReturnOutOfService() {

        setOufOService(true);
        setConsecutiveFailures(maxConsecutiveFailures);

        final Health expectedHealth = Health.outOfService()
                .withDetail("consecutiveFailures", maxConsecutiveFailures)
                .withDetail("pollingInterval", Duration.ofSeconds(15))
                .withDetail("maxConsecutiveFailuresBeforeOutOfService", maxConsecutiveFailures)
                .build();

        final Health health = blockchainConnectionHealthIndicator.health();
        Assertions.assertThat(health).isEqualTo(expectedHealth);
    }

    @Test
    void shouldReturnUp() {
        setOufOService(false);

        final Health expectedHealth = Health.up()
                .withDetail("consecutiveFailures", 0)
                .withDetail("pollingInterval", Duration.ofSeconds(15))
                .withDetail("maxConsecutiveFailuresBeforeOutOfService", maxConsecutiveFailures)
                .build();

        final Health health = blockchainConnectionHealthIndicator.health();
        Assertions.assertThat(health).isEqualTo(expectedHealth);
    }
    // endregion

    // region utils
    private Boolean isOutOfService() {
        return (Boolean) ReflectionTestUtils.getField(blockchainConnectionHealthIndicator, "outOfService");
    }

    private void setOufOService(boolean isOutOfService) {
        ReflectionTestUtils.setField(blockchainConnectionHealthIndicator, "outOfService", isOutOfService);
    }

    private Integer getConsecutiveFailures() {
        return (Integer) ReflectionTestUtils.getField(blockchainConnectionHealthIndicator, "consecutiveFailures");
    }

    private void setConsecutiveFailures(int consecutiveFailures) {
        ReflectionTestUtils.setField(blockchainConnectionHealthIndicator, "consecutiveFailures", consecutiveFailures);
    }
    // endregion
}