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

import com.iexec.core.chain.event.LatestBlockEvent;
import io.micrometer.core.instrument.Metrics;
import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthSubscribe;
import org.web3j.protocol.websocket.WebSocketService;
import org.web3j.protocol.websocket.events.NewHeadsNotification;
import org.web3j.utils.Numeric;

import java.net.ConnectException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class WebSocketBlockchainListener {
    static final String LATEST_BLOCK_METRIC_NAME = "iexec.chain.block.latest";

    private static final String SUBSCRIBE_METHOD = "eth_subscribe";
    private static final String UNSUBSCRIBE_METHOD = "eth_unsubscribe";

    private final ApplicationEventPublisher applicationEventPublisher;
    private final WebSocketService webSocketService;

    private final AtomicLong lastSeenBlock;
    private Disposable newHeads;

    public WebSocketBlockchainListener(final ApplicationEventPublisher applicationEventPublisher,
                                       final ChainConfig chainConfig) {
        this.applicationEventPublisher = applicationEventPublisher;
        final String wsUrl = chainConfig.getNodeAddress().replace("http", "ws");
        this.webSocketService = new WebSocketService(wsUrl, false);
        lastSeenBlock = Metrics.gauge(LATEST_BLOCK_METRIC_NAME, new AtomicLong(0));
    }

    @Scheduled(fixedRate = 5000)
    private void run() throws ConnectException {
        if (newHeads != null && !newHeads.isDisposed()) {
            return;
        }

        log.warn("web socket disconnection detected");
        webSocketService.connect();

        final Request<?, EthSubscribe> newHeadsRequest = new Request<>(
                SUBSCRIBE_METHOD,
                List.of("newHeads", Map.of()),
                webSocketService,
                EthSubscribe.class
        );

        final Flowable<NewHeadsNotification> newHeadsEvents = webSocketService.subscribe(
                newHeadsRequest, UNSUBSCRIBE_METHOD, NewHeadsNotification.class);
        newHeads = newHeadsEvents.subscribe(this::processHead, this::handleError);
    }

    private void processHead(final NewHeadsNotification event) {
        final long blockNumber = Numeric.toBigInt(event.getParams().getResult().getNumber()).longValue();
        final String blockHash = event.getParams().getResult().getHash();
        final long blockTimestamp = Numeric.toBigInt(event.getParams().getResult().getTimestamp()).longValue();
        final Instant blockTimestampInstant = Instant.ofEpochSecond(blockTimestamp);
        log.info("Last seen block [number:{}, hash:{}, timestamp:{}, instant:{}]",
                blockNumber, blockHash, blockTimestamp, blockTimestampInstant);
        lastSeenBlock.set(blockNumber);
        applicationEventPublisher.publishEvent(new LatestBlockEvent(this, blockNumber, blockHash, blockTimestamp));
    }

    private void handleError(final Throwable t) {
        log.error("An error happened during subscription", t);
    }
}
