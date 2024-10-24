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

import com.iexec.core.chain.event.ChainConnectedEvent;
import com.iexec.core.chain.event.ChainDisconnectedEvent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

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
    private final ScheduledExecutorService monitoringExecutor;

    /**
     * Current number of consecutive failures.
     */
    @Getter
    private int consecutiveFailures = 0;
    @Getter
    private LocalDateTime firstFailure = null;

    /**
     * Required for test purposes.
     */
    private final Clock clock;

    @Autowired
    public BlockchainConnectionHealthIndicator(
            ApplicationEventPublisher applicationEventPublisher,
            Web3jService web3jService,
            ChainConfig chainConfig) {
        this(
                applicationEventPublisher,
                web3jService,
                chainConfig,
                Executors.newSingleThreadScheduledExecutor(),
                Clock.systemDefaultZone()
        );
    }

    BlockchainConnectionHealthIndicator(
            ApplicationEventPublisher applicationEventPublisher,
            Web3jService web3jService,
            ChainConfig chainConfig,
            ScheduledExecutorService monitoringExecutor,
            Clock clock) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.web3jService = web3jService;
        this.pollingInterval = chainConfig.getBlockTime();
        this.monitoringExecutor = monitoringExecutor;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    void scheduleMonitoring() {
        monitoringExecutor.scheduleAtFixedRate(this::checkConnection, 0, pollingInterval.toSeconds(), TimeUnit.SECONDS);
    }

    /**
     * Check blockchain is reachable by retrieving the latest block number.
     * <p>
     * If it isn't, then increment {@link BlockchainConnectionHealthIndicator#consecutiveFailures} counter,
     * then this Health Indicator becomes {@link Status#OUT_OF_SERVICE}.
     * <p>
     * If blockchain is reachable, then reset the counter.
     * <p>
     * If the indicator becomes {@link Status#OUT_OF_SERVICE}, then it stays as is until the communication to the
     * blockchain node is restored.
     */
    void checkConnection() {
        final long latestBlockNumber = web3jService.getLatestBlockNumber();
        log.debug("Latest on-chain block number [block:{}]", latestBlockNumber);
        if (latestBlockNumber == 0) {
            connectionFailed();
        } else {
            connectionSucceeded(latestBlockNumber);
        }
    }

    /**
     * Increment the {@link BlockchainConnectionHealthIndicator#consecutiveFailures} counter.
     * <p>
     * If first failure:
     * <ul>
     * <li> Publish a {@link ChainDisconnectedEvent} event
     * <li> Set the {@link BlockchainConnectionHealthIndicator#firstFailure} to current time
     * </ul>
     */
    private void connectionFailed() {
        if (consecutiveFailures == 0) {
            log.error("Blockchain communication failed, this Scheduler is now OUT-OF-SERVICE until communication is restored.");
            log.debug("Publishing ChainDisconnectedEvent");
            applicationEventPublisher.publishEvent(new ChainDisconnectedEvent(this));
            firstFailure = LocalDateTime.now(clock);
        }
        ++consecutiveFailures;
    }

    /**
     * Reset {@link BlockchainConnectionHealthIndicator#consecutiveFailures} to {@code 0}.
     * <p>
     * If previous failures occurred, then:
     * <ul>
     * <li>Log a "connection restored" message.
     * <li>Reset the {@link BlockchainConnectionHealthIndicator#firstFailure} var to {@code null}
     * <li>If {@link Status#OUT_OF_SERVICE}, publish a {@link ChainConnectedEvent} event
     * </ul>
     */
    private void connectionSucceeded(long latestBlockNumber) {
        if (consecutiveFailures > 0) {
            log.info("Blockchain connection is now restored after a period of unavailability." +
                            " [block:{}, unavailabilityPeriod:{}]",
                    latestBlockNumber, pollingInterval.multipliedBy(consecutiveFailures));
            firstFailure = null;
            consecutiveFailures = 0;
            log.debug("Publishing ChainConnectedEvent");
            applicationEventPublisher.publishEvent(new ChainConnectedEvent(this));
        }
    }

    @Override
    public Health health() {
        final Health.Builder healthBuilder = consecutiveFailures > 0
                ? Health.outOfService()
                : Health.up();

        if (firstFailure != null) {
            healthBuilder.withDetail("firstFailure", firstFailure);
        }

        return healthBuilder
                .withDetail("consecutiveFailures", consecutiveFailures)
                .withDetail("pollingInterval", pollingInterval)
                .build();
    }

    public boolean isUp() {
        return health().getStatus() == Status.UP;
    }
}
