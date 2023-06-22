package com.iexec.core.chain;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class BlockchainConnectionHealthIndicator implements HealthIndicator {

    private final Web3jService web3jService;
    private final Duration pollingInterval;
    private final int maxConsecutiveFailures;
    private final ScheduledExecutorService executor;

    private int consecutiveFailures = 0;
    private boolean outOfService = false;

    @Autowired
    public BlockchainConnectionHealthIndicator(Web3jService web3jService,
                                               ChainConfig chainConfig,
                                               @Value("${chain.health.pollingIntervalInBlocks}") int pollingIntervalInBlocks,
                                               @Value("${chain.health.maxConsecutiveFailures}") int maxConsecutiveFailures) {
        this(
                web3jService,
                chainConfig,
                pollingIntervalInBlocks,
                maxConsecutiveFailures,
                Executors.newSingleThreadScheduledExecutor()
        );
    }

    public BlockchainConnectionHealthIndicator(Web3jService web3jService,
                                               ChainConfig chainConfig,
                                               int pollingIntervalInBlocks,
                                               int maxConsecutiveFailures,
                                               ScheduledExecutorService executor) {
        this.web3jService = web3jService;
        this.pollingInterval = chainConfig.getBlockTime().multipliedBy(pollingIntervalInBlocks);
        this.maxConsecutiveFailures = maxConsecutiveFailures;
        this.executor = executor;
    }

    @PostConstruct
    void scheduleMonitoring() {
        executor.scheduleAtFixedRate(this::checkConnection, 0, pollingInterval.toSeconds(), TimeUnit.SECONDS);
    }

    /**
     * Check blockchain is reachable by retrieving the latest block number.
     * <p>
     * If it isn't, then increment {@link BlockchainConnectionHealthIndicator#consecutiveFailures} counter.
     * If counter reaches {@link BlockchainConnectionHealthIndicator#maxConsecutiveFailures},
     * then this Health Indicator becomes {@link Status#OUT_OF_SERVICE}.
     * <p>
     * If blockchain is reachable, then reset the counter.
     * <p>
     * /!\ If the indicator becomes {@code OUT_OF_SERVICE}, then it stays as is until the Scheduler is restarted
     * even if the blockchain is later available again.
     */
    void checkConnection() {
        final long latestBlockNumber = web3jService.getLatestBlockNumber();
        if (latestBlockNumber == 0) {
            ++consecutiveFailures;
            if (consecutiveFailures >= maxConsecutiveFailures) {
                outOfService = true;
                log.error("Blockchain hasn't been accessed for a long period. " +
                        "This Scheduler is now OUT-OF-SERVICE until it is restarted." +
                        "[unavailabilityPeriod:{}]", pollingInterval.multipliedBy(maxConsecutiveFailures));
            } else {
                log.warn("Blockchain is unavailable. Will retry connection." +
                        "[unavailabilityPeriod:{}, nextRetry:{}]",
                        pollingInterval.multipliedBy(consecutiveFailures), pollingInterval);
            }
        } else {
            if (!outOfService) {
                log.info("Blockchain connection is now restored after a period of unavailability." +
                        "[unavailabilityPeriod:{}]", pollingInterval.multipliedBy(consecutiveFailures));
            }
            consecutiveFailures = 0;
        }
    }


    @Override
    public Health health() {
        final Health.Builder healthBuilder = outOfService
                ? Health.outOfService()
                : Health.up();

        return healthBuilder
                .withDetail("pollingInterval", pollingInterval)
                .withDetail("consecutiveFailures", consecutiveFailures)
                .withDetail("maxConsecutiveFailuresBeforeOutOfService", maxConsecutiveFailures)
                .build();
    }
}
