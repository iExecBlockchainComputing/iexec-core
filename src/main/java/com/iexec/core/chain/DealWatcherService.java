/*
 * Copyright 2020-2026 IEXEC BLOCKCHAIN TECH
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

import com.iexec.commons.poco.chain.ChainDeal;
import com.iexec.commons.poco.encoding.LogTopic;
import com.iexec.commons.poco.tee.TeeUtils;
import com.iexec.core.chain.event.DealEvent;
import com.iexec.core.configuration.ConfigurationService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.event.TaskCreatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static com.iexec.commons.poco.chain.Web3jAbstractService.toEthereumAddress;

@Slf4j
@Service
public class DealWatcherService {

    public static final String METRIC_DEALS_EVENTS_COUNT = "iexec.core.deals.events";
    public static final String METRIC_DEALS_COUNT = "iexec.core.deals";
    public static final String METRIC_DEALS_REPLAY_COUNT = "iexec.core.deals.replay";
    public static final String METRIC_DEALS_LAST_BLOCK = "iexec.core.deals.last.block";
    private static final List<BigInteger> CORRECT_TEE_TRUSTS = List.of(BigInteger.ZERO, BigInteger.ONE);

    private final ChainConfig chainConfig;
    private final IexecHubService iexecHubService;
    private final ConfigurationService configurationService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TaskService taskService;
    private final Web3jService web3jService;
    private final BigInteger maxLogsBatchSize;
    // internal variables
    @Getter
    private BigInteger latestBlockNumberWithDeal = BigInteger.ZERO;
    @Getter
    private long dealEventsCount = 0;
    @Getter
    private long dealsCount = 0;
    /**
     * @deprecated obsolete after deal watching simplification
     */
    @Deprecated(forRemoval = true)
    @Getter
    private long replayDealsCount = 0;
    private final Counter dealEventsCounter;
    private final Counter dealsCounter;
    /**
     * @deprecated obsolete after deal watching simplification
     */
    @Deprecated(forRemoval = true)
    private final Counter replayDealsCounter;
    private final Counter latestBlockNumberWithDealCounter;

    public DealWatcherService(final ChainConfig chainConfig,
                              final IexecHubService iexecHubService,
                              final ConfigurationService configurationService,
                              final ApplicationEventPublisher applicationEventPublisher,
                              final TaskService taskService,
                              final Web3jService web3jService,
                              @Value("${chain.max-logs-batch-size}") final BigInteger maxLogsBatchSize) {
        this.chainConfig = chainConfig;
        this.iexecHubService = iexecHubService;
        this.configurationService = configurationService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.taskService = taskService;
        this.web3jService = web3jService;
        this.maxLogsBatchSize = maxLogsBatchSize;

        this.dealEventsCounter = Metrics.counter(METRIC_DEALS_EVENTS_COUNT);
        this.dealsCounter = Metrics.counter(METRIC_DEALS_COUNT);
        this.replayDealsCounter = Metrics.counter(METRIC_DEALS_REPLAY_COUNT);
        this.latestBlockNumberWithDealCounter = Metrics.counter(METRIC_DEALS_LAST_BLOCK);
    }

    /**
     * Update last seen block in the database
     * and run {@link DealEvent} handler.
     *
     * @param dealEvent deal event to process
     */
    void onDealEvent(final DealEvent dealEvent) {
        dealsCount++;
        dealsCounter.increment();
        final String dealId = dealEvent.getChainDealId();
        final BigInteger dealBlock = dealEvent.getBlockNumber();
        log.info("Received deal [dealId:{}, block:{}]", dealId, dealBlock);
        this.handleDeal(dealEvent);
        if (latestBlockNumberWithDeal.compareTo(dealBlock) < 0) {
            double deltaBlocksNumber = dealBlock.subtract(latestBlockNumberWithDeal).doubleValue();
            latestBlockNumberWithDeal = dealBlock;
            latestBlockNumberWithDealCounter.increment(deltaBlocksNumber);
        }
        if (configurationService.getLastSeenBlockWithDeal().compareTo(dealBlock) < 0) {
            configurationService.setLastSeenBlockWithDeal(dealBlock);
        }
    }

    /**
     * Handle new on-chain deals and add its tasks to MongoDB ask collection.
     *
     * @param dealEvent Object representing PoCo SchedulerNoticeEvent
     */
    private void handleDeal(final DealEvent dealEvent) {
        String chainDealId = dealEvent.getChainDealId();
        Optional<ChainDeal> oChainDeal = iexecHubService.getChainDealWithDetails(chainDealId);
        if (oChainDeal.isEmpty()) {
            log.error("Could not get chain deal [chainDealId:{}]", chainDealId);
            return;
        }
        ChainDeal chainDeal = oChainDeal.get();
        if (!shouldProcessDeal(chainDeal)) {
            return;
        }
        int startBag = chainDeal.getBotFirst().intValue();
        int endBag = chainDeal.getBotFirst().intValue() + chainDeal.getBotSize().intValue();
        for (int taskIndex = startBag; taskIndex < endBag; taskIndex++) {
            taskService
                    .addTask(
                            chainDealId,
                            taskIndex,
                            dealEvent.getBlockNumber().longValue(),
                            chainDeal.getChainApp().getMultiaddr(),
                            chainDeal.getParams().getIexecArgs(),
                            chainDeal.getTrust().intValue(),
                            chainDeal.getChainCategory().getMaxExecutionTime(),
                            chainDeal.getTag(),
                            iexecHubService.getChainDealContributionDeadline(chainDeal),
                            iexecHubService.getChainDealFinalDeadline(chainDeal))
                    .ifPresent(this::publishTaskCreatedEvent);
        }
    }

    /**
     * Check whether a deal should be processed or skipped.
     * A deal can be skipped for the following reasons:
     * <ul>
     *     <li>Its contribution deadline has already been met;</li>
     *     <li>It has a TEE tag but its trust is not in {0,1}.</li>
     * </ul>
     *
     * @param chainDeal Deal to check
     * @return {@literal true} if deal should be processed, {@literal false} otherwise.
     */
    boolean shouldProcessDeal(ChainDeal chainDeal) {
        final String chainDealId = chainDeal.getChainDealId();

        // do not process deals after deadline
        if (!iexecHubService.isBeforeContributionDeadline(chainDeal)) {
            log.error("Deal has expired [chainDealId:{}, deadline:{}]",
                    chainDealId, iexecHubService.getChainDealContributionDeadline(chainDeal));
            return false;
        }

        // do not process deals with TEE tag but trust not in {0,1}.
        final String tag = chainDeal.getTag();
        final BigInteger trust = chainDeal.getTrust();
        if (TeeUtils.getTeeFramework(tag) != null
                && !CORRECT_TEE_TRUSTS.contains(trust)) {
            log.error("Deal with TEE tag and trust not zero nor one [chainDealId:{}, tag:{}, trust:{}]",
                    chainDealId, tag, trust);
            return false;
        }

        return true;
    }

    /**
     * Publishes a {@code TaskCreatedEvent} for a newly created task.
     *
     * @param task Newly created task for which the event will be published
     */
    private void publishTaskCreatedEvent(final Task task) {
        final TaskCreatedEvent event = new TaskCreatedEvent(
                this, task.getChainTaskId(), task.getChainDealId(), task.getTaskIndex());
        applicationEventPublisher.publishEvent(event);
    }

    /*
     * Some deal events are sometimes missed by #schedulerNoticeEventObservable method
     * so we decide to replay events from times to times (already saved events will be ignored)
     */
    @Scheduled(fixedRateString = "#{@cronConfiguration.getDealReplay()}")
    void replayDealEvent() {
        final BigInteger lastSeenBlock = BigInteger.valueOf(web3jService.getLatestBlockNumber());
        final BigInteger from = configurationService.getFromReplay();
        if (from.compareTo(lastSeenBlock) >= 0) {
            return;
        }
        final BigInteger to = from.add(maxLogsBatchSize).subtract(BigInteger.ONE).min(lastSeenBlock);
        subscribeToDealEventInRange(from, to);
    }

    /**
     * Subscribe to on-chain deal events for a fixed range of blocks.
     *
     * @param from start block
     * @param to   end block
     */
    private void subscribeToDealEventInRange(final BigInteger from, final BigInteger to) {
        try {
            log.info("Query SchedulerNoticeEvent on range [from:{}, to:{}]", from, to);
            final EthFilter filter = createDealEventFilter(from, to);
            web3jService.getWeb3j().ethGetLogs(filter).send()
                    .getLogs()
                    .forEach(this::schedulerNoticeToDealEvent);
            configurationService.setFromReplay(to);
        } catch (Exception e) {
            log.warn("Communication failed", e);
        }
    }

    EthFilter createDealEventFilter(final BigInteger from, final BigInteger to) {
        final DefaultBlockParameter fromBlock = DefaultBlockParameter.valueOf(from);
        final DefaultBlockParameter toBlock = DefaultBlockParameter.valueOf(to);
        final EthFilter filter = new EthFilter(fromBlock, toBlock, chainConfig.getHubAddress());
        final BigInteger poolAddressBigInt = Numeric.toBigInt(chainConfig.getPoolAddress());
        filter.addSingleTopic(LogTopic.SCHEDULER_NOTICE_EVENT);
        filter.addSingleTopic(Numeric.toHexStringWithPrefixZeroPadded(poolAddressBigInt, 64));
        return filter;
    }

    void schedulerNoticeToDealEvent(final EthLog.LogResult<Log> ethLog) {
        dealEventsCount++;
        dealEventsCounter.increment();
        final BigInteger noticeBlockNumber = ethLog.get().getBlockNumber();
        final String dealId = ethLog.get().getData();
        final String eventPoolAddress = toEthereumAddress(ethLog.get().getTopics().get(1));
        if (eventPoolAddress.equalsIgnoreCase(chainConfig.getPoolAddress())) {
            log.info("SchedulerNoticeEvent received [blockNumber:{}, chainDealId:{}, dealEventsCount:{}]",
                    noticeBlockNumber, dealId, dealEventsCount);
            onDealEvent(new DealEvent(this, dealId, noticeBlockNumber));
        } else {
            log.warn("SchedulerNoticeEvent should not have been received [blockNumber:{}, chainDealId:{}, dealEventsCount:{}]",
                    noticeBlockNumber, dealId, dealEventsCount);
        }
    }
}
