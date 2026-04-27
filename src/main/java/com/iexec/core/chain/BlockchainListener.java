/*
 * Copyright 2025-2026 IEXEC BLOCKCHAIN TECH
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Async;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class BlockchainListener {
    static final String LATEST_BLOCK_METRIC_NAME = "iexec.chain.block.latest";

    private final ApplicationEventPublisher applicationEventPublisher;
    private final Web3j web3Client;
    private final AtomicLong lastSeenBlock;

    public BlockchainListener(final ApplicationEventPublisher applicationEventPublisher,
                              final ChainConfig chainConfig) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.web3Client = Web3j.build(new HttpService(chainConfig.getNodeAddress()),
                chainConfig.getBlockTime().toMillis(), Async.defaultExecutorService());
        lastSeenBlock = Metrics.gauge(LATEST_BLOCK_METRIC_NAME, new AtomicLong(0));
    }

    @Scheduled(fixedRate = 5000)
    private void run() {
        try {
            final EthBlock.Block ethBlock = web3Client.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock();
            final long blockNumber = ethBlock.getNumber().longValue();
            final String blockHash = ethBlock.getHash();
            final long blockTimestamp = ethBlock.getTimestamp().longValue();
            final Instant blockTimestampInstant = Instant.ofEpochSecond(blockTimestamp);
            log.info("Last seen block [number:{}, hash:{}, timestamp:{}, instant:{}]",
                    blockNumber, blockHash, blockTimestamp, blockTimestampInstant);
            lastSeenBlock.set(blockNumber);
            applicationEventPublisher.publishEvent(new LatestBlockEvent(this, blockNumber, blockHash, blockTimestamp));
        } catch (Exception e) {
            log.error("An error happened while fetching data on-chain", e);
        }
    }
}
