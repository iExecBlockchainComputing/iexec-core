/*
 * Copyright 2023-2023 IEXEC BLOCKCHAIN TECH
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

import com.iexec.core.chain.event.ChainConnectedEvent;
import com.iexec.core.chain.event.ChainDisconnectedEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class BlockchainConnectionHealthIndicator implements HealthIndicator {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final Web3jService web3jService;
    /**
     * Interval between 2 requests onto the chain.
     */
    private final Duration pollingInterval;
    /**
     * Number of consecutive failures before declaring this Scheduler is out-of-service.
     */
    private final int outOfServiceThreshold;
    private final ScheduledExecutorService monitoringExecutor;

    /**
     * Current number of consecutive failures.
     */
    @Getter
    private int consecutiveFailures = 0;
    @Getter
    private LocalDateTime firstFailure = null;
    @Getter
    private boolean outOfService = false;

    /**
     * Required for test purposes.
     */
    private final Clock clock;

    @Autowired
    public BlockchainConnectionHealthIndicator(
            ApplicationEventPublisher applicationEventPublisher,
            Web3jService web3jService,
            ChainConfig chainConfig,
            @Value("${chain.health.pollingIntervalInBlocks}") int pollingIntervalInBlocks,
            @Value("${chain.health.outOfServiceThreshold}") int outOfServiceThreshold) {
        this(
                applicationEventPublisher,
                web3jService,
                chainConfig,
                pollingIntervalInBlocks,
                outOfServiceThreshold,
                Executors.newSingleThreadScheduledExecutor(),
                Clock.systemDefaultZone()
        );
    }

    BlockchainConnectionHealthIndicator(
            ApplicationEventPublisher applicationEventPublisher,
            Web3jService web3jService,
            ChainConfig chainConfig,
            int pollingIntervalInBlocks,
            int outOfServiceThreshold,
            ScheduledExecutorService monitoringExecutor,
            Clock clock) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.web3jService = web3jService;
        this.pollingInterval = chainConfig.getBlockTime().multipliedBy(pollingIntervalInBlocks);
        this.outOfServiceThreshold = outOfServiceThreshold;
        this.monitoringExecutor = monitoringExecutor;
        this.clock = clock;
    }

    @PostConstruct
    void scheduleMonitoring() {
        monitoringExecutor.scheduleAtFixedRate(this::checkConnection, 0, pollingInterval.toSeconds(), TimeUnit.SECONDS);
    }

    /**
     * Check blockchain is reachable by retrieving the latest block number.
     * <p>
     * If it isn't, then increment {@link BlockchainConnectionHealthIndicator#consecutiveFailures} counter.
     * If counter reaches {@link BlockchainConnectionHealthIndicator#outOfServiceThreshold},
     * then this Health Indicator becomes {@link Status#OUT_OF_SERVICE}.
     * <p>
     * If blockchain is reachable, then reset the counter.
     * <p>
     * /!\ If the indicator becomes {@code OUT_OF_SERVICE}, then it stays as is until the Scheduler is restarted
     * even if the blockchain is later available again.
     */
    void checkConnection() {
        final long latestBlockNumber = web3jService.getLatestBlockNumber();
        log.debug("blockNumber {}", latestBlockNumber);
        if (latestBlockNumber == 0) {
            connectionFailed();
        } else {
            connectionSucceeded(latestBlockNumber);
        }
    }

    /**
     * Increment the {@link BlockchainConnectionHealthIndicator#consecutiveFailures} counter.
     * <p>
     * If first failure, set the {@link BlockchainConnectionHealthIndicator#firstFailure} to current time.
     * <p>
     * If {@link BlockchainConnectionHealthIndicator#outOfServiceThreshold} has been reached for the first time:
     * <ul>
     * <li> Set {@link BlockchainConnectionHealthIndicator#outOfService} to {@literal true}
     * <li> Publish a {@link ChainDisconnectedEvent} event.
     */
    private void connectionFailed() {
        ++consecutiveFailures;
        if (consecutiveFailures >= outOfServiceThreshold) {
            log.error("Blockchain hasn't been accessed for a long period. " +
                    "This Scheduler is now OUT-OF-SERVICE until communication is restored." +
                    " [unavailabilityPeriod:{}]", pollingInterval.multipliedBy(outOfServiceThreshold));
            if (!outOfService) {
                outOfService = true;
                applicationEventPublisher.publishEvent(new ChainDisconnectedEvent(this));
            }
        } else {
            if (consecutiveFailures == 1) {
                firstFailure = LocalDateTime.now(clock);
            }
            log.warn("Blockchain is unavailable. Will retry connection." +
                            " [unavailabilityPeriod:{}, nextRetry:{}]",
                    pollingInterval.multipliedBy(consecutiveFailures), pollingInterval);
        }
    }

    /**
     * Reset {@link BlockchainConnectionHealthIndicator#consecutiveFailures} to {@code 0}.
     * <p>
     * If previous failures occurred, then:
     * <ul>
     * <li>Log a "connection restored" message.
     * <li>Reset the {@link BlockchainConnectionHealthIndicator#firstFailure} var to {@code null}
     * <li>If OUT-OF-SERVICE, publish a {@link ChainConnectedEvent} event and reset the OUT-OF-SERVICE state
     */
    private void connectionSucceeded(long latestBlockNumber) {
        if (consecutiveFailures > 0) {
            log.info("Blockchain connection is now restored after a period of unavailability." +
                    " [block:{}, unavailabilityPeriod:{}]",
                    latestBlockNumber, pollingInterval.multipliedBy(consecutiveFailures));
            firstFailure = null;
            consecutiveFailures = 0;
            if (outOfService) {
                applicationEventPublisher.publishEvent(new ChainConnectedEvent(this));
                outOfService = false;
            }
        }
    }

    @Override
    public Health health() {
        final Health.Builder healthBuilder = outOfService
                ? Health.outOfService()
                : Health.up();

        if (firstFailure != null) {
            healthBuilder.withDetail("firstFailure", firstFailure);
        }

        return healthBuilder
                .withDetail("consecutiveFailures", consecutiveFailures)
                .withDetail("pollingInterval", pollingInterval)
                .withDetail("outOfServiceThreshold", outOfServiceThreshold)
                .build();
    }

    public boolean isUp() {
        return health().getStatus() == Status.UP;
    }
}
