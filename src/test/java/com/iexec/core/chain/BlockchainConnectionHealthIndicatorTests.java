package com.iexec.core.chain;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.actuate.health.Health;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    @ParameterizedTest
    @ValueSource(longs = {1L, 5L, 100L, 5_000L})
    void shouldGetLatestBlockNumberAndDoNothing(long latestBlockNumber) {
        when(web3jService.getLatestBlockNumber()).thenReturn(latestBlockNumber);

        blockchainConnectionHealthIndicator.checkConnection();

        final Integer consecutiveFailures = getConsecutiveFailures();
        final Boolean outOfService = isOutOfService();

        Assertions.assertThat(consecutiveFailures).isZero();
        Assertions.assertThat(outOfService).isFalse();

        verify(web3jService).getLatestBlockNumber();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void shouldNotGetLatestBlockNumberAndIncrementConsecutiveFailures(int previousConsecutiveFailuresCount) {
        setConsecutiveFailures(previousConsecutiveFailuresCount);

        when(web3jService.getLatestBlockNumber()).thenReturn(0L);

        blockchainConnectionHealthIndicator.checkConnection();

        final Integer consecutiveFailures = getConsecutiveFailures();
        final Boolean outOfService = isOutOfService();

        Assertions.assertThat(consecutiveFailures).isEqualTo(previousConsecutiveFailuresCount + 1);
        Assertions.assertThat(outOfService).isFalse();


        verify(web3jService).getLatestBlockNumber();
    }

    @Test
    void shouldNotGetLatestBlockNumberAndBecomeOutOfService() {
        setConsecutiveFailures(3);

        when(web3jService.getLatestBlockNumber()).thenReturn(0L);

        blockchainConnectionHealthIndicator.checkConnection();

        final Integer consecutiveFailures = getConsecutiveFailures();
        final Boolean outOfService = isOutOfService();

        Assertions.assertThat(consecutiveFailures).isEqualTo(4);
        Assertions.assertThat(outOfService).isTrue();

        verify(web3jService).getLatestBlockNumber();
    }

    @Test
    void shouldNotComeBackToUpIfOutOfService() {
        setOufOService(true);
        setConsecutiveFailures(maxConsecutiveFailures);

        when(web3jService.getLatestBlockNumber()).thenReturn(1L);

        blockchainConnectionHealthIndicator.checkConnection();

        final Integer consecutiveFailures = getConsecutiveFailures();
        final Boolean outOfService = isOutOfService();

        Assertions.assertThat(consecutiveFailures).isZero();
        Assertions.assertThat(outOfService).isTrue();

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