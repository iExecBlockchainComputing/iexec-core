/*
 * Copyright 2024 IEXEC BLOCKCHAIN TECH
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

import com.iexec.commons.poco.encoding.LogTopic;
import com.iexec.core.task.event.TaskUpdateRequestEvent;
import io.micrometer.core.instrument.Metrics;
import io.reactivex.Flowable;
import io.reactivex.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthSubscribe;
import org.web3j.protocol.websocket.WebSocketService;
import org.web3j.protocol.websocket.events.Log;
import org.web3j.protocol.websocket.events.LogNotification;
import org.web3j.protocol.websocket.events.NewHeadsNotification;
import org.web3j.utils.Numeric;

import java.net.ConnectException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.iexec.commons.poco.encoding.LogTopic.*;

@Slf4j
public class ListenerService {
    static final String LATEST_BLOCK_METRIC_NAME = "iexec.chain.block.latest";

    private static final String SUBSCRIBE_METHOD = "eth_subscribe";
    private static final String UNSUBSCRIBE_METHOD = "eth_unsubscribe";

    private final ApplicationEventPublisher applicationEventPublisher;
    private final ChainConfig chainConfig;
    private final WebSocketService webSocketService;

    private final AtomicLong lastSeenBlock;
    private Disposable newHeads;
    private Disposable logs;

    public ListenerService(final ApplicationEventPublisher applicationEventPublisher,
                           final ChainConfig chainConfig) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.chainConfig = chainConfig;
        final String wsUrl = chainConfig.getNodeAddress().replace("http", "ws");
        this.webSocketService = new WebSocketService(wsUrl, false);
        lastSeenBlock = Metrics.gauge(LATEST_BLOCK_METRIC_NAME, new AtomicLong(0));
    }

    @Scheduled(fixedRate = 5000)
    private void run() throws ConnectException {
        if (newHeads != null && !newHeads.isDisposed() && logs != null && !logs.isDisposed()) {
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

        final Request<?, EthSubscribe> logsRequest = new Request<>(
                SUBSCRIBE_METHOD,
                List.of("logs", Map.of("address", chainConfig.getHubAddress(),
                        "topics", List.of(List.of(TASK_INITIALIZE_EVENT, TASK_CONSENSUS_EVENT, TASK_FINALIZE_EVENT)))),
                webSocketService,
                EthSubscribe.class
        );

        final Flowable<LogNotification> logsEvents = webSocketService.subscribe(
                logsRequest, UNSUBSCRIBE_METHOD, LogNotification.class);
        logs = logsEvents.subscribe(this::processLog, this::handleError);
    }

    private void processHead(final NewHeadsNotification event) {
        final long blockNumber = Numeric.toBigInt(event.getParams().getResult().getNumber()).longValue();
        lastSeenBlock.set(blockNumber);
    }

    private void processLog(final LogNotification event) {
        final Log logEvent = event.getParams().getResult();
        log.info("Received log {} {}", logEvent.getBlockNumber(),
                logEvent.getTopics().stream().map(LogTopic::decode).toList());
        applicationEventPublisher.publishEvent(
                new TaskUpdateRequestEvent(this, logEvent.getTopics().get(1)));
    }

    private void handleError(final Throwable t) {
        log.error("An error happened during subscription", t);
    }
}
