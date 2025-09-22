/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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
import com.iexec.commons.poco.contract.generated.IexecHubContract;
import com.iexec.commons.poco.tee.TeeUtils;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.core.chain.event.ChainConnectedEvent;
import com.iexec.core.chain.event.ChainDisconnectedEvent;
import com.iexec.core.configuration.ConfigurationService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.event.TaskCreatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.reactivex.disposables.Disposable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.abi.EventEncoder;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static com.iexec.commons.poco.contract.generated.IexecHubContract.SCHEDULERNOTICE_EVENT;

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
    // internal variables
    private boolean outOfService = true;
    private Disposable dealEventsSubscription;
    private Disposable dealEventSubscriptionReplay;
    @Getter
    private BigInteger latestBlockNumberWithDeal = BigInteger.ZERO;
    @Getter
    private long dealEventsCount = 0;
    @Getter
    private long dealsCount = 0;
    @Getter
    private long replayDealsCount = 0;

    private final Counter dealEventsCounter;
    private final Counter dealsCounter;
    private final Counter replayDealsCounter;
    private final Counter latestBlockNumberWithDealCounter;

    public DealWatcherService(ChainConfig chainConfig,
                              IexecHubService iexecHubService,
                              ConfigurationService configurationService,
                              ApplicationEventPublisher applicationEventPublisher,
                              TaskService taskService,
                              Web3jService web3jService) {
        this.chainConfig = chainConfig;
        this.iexecHubService = iexecHubService;
        this.configurationService = configurationService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.taskService = taskService;
        this.web3jService = web3jService;

        this.dealEventsCounter = Metrics.counter(METRIC_DEALS_EVENTS_COUNT);
        this.dealsCounter = Metrics.counter(METRIC_DEALS_COUNT);
        this.replayDealsCounter = Metrics.counter(METRIC_DEALS_REPLAY_COUNT);
        this.latestBlockNumberWithDealCounter = Metrics.counter(METRIC_DEALS_LAST_BLOCK);
    }

    /**
     * This should be non-blocking to liberate
     * the main thread, since deals can have
     * a large number of tasks (BoT).
     */
    @Async
    @EventListener(ChainConnectedEvent.class)
    public void run() {
        outOfService = false;
        disposeSubscription(dealEventsSubscription);
        dealEventsSubscription = subscribeToDealEventFromOneBlockToLatest(configurationService.getLastSeenBlockWithDeal());
    }

    /**
     * Dispose of deal watching subscription.
     */
    @Async
    @EventListener(ChainDisconnectedEvent.class)
    public void stop() {
        outOfService = true;
        disposeSubscription(dealEventsSubscription);
    }

    /**
     * Dispose of a {@link Disposable} subscription if it exists and is not already disposed.
     *
     * @param subscription Deal event subscription to dispose of.
     */
    private void disposeSubscription(Disposable subscription) {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }

    /**
     * Subscribe to onchain deal events from
     * a given block to the latest block.
     *
     * @param from start block
     * @return disposable subscription
     */
    Disposable subscribeToDealEventFromOneBlockToLatest(BigInteger from) {
        log.info("Watcher DealEvent started [from:{}, to:{}]", from, "latest");
        EthFilter filter = createDealEventFilter(from, null);
        return iexecHubService.getDealEventObservable(filter)
                .map(this::schedulerNoticeToDealEvent)
                .subscribe(dealEvent -> dealEvent.ifPresent(event -> onDealEvent(event, "start")));
    }

    /**
     * Update last seen block in the database
     * and run {@link DealEvent} handler.
     *
     * @param dealEvent
     */
    private void onDealEvent(DealEvent dealEvent, String watcher) {
        if ("replay".equals(watcher)) {
            replayDealsCount++;
            replayDealsCounter.increment();
        } else {
            dealsCount++;
            dealsCounter.increment();
        }
        String dealId = dealEvent.getChainDealId();
        BigInteger dealBlock = dealEvent.getBlockNumber();
        log.info("Received deal [dealId:{}, block:{}, watcher: {}]",
                dealId, dealBlock, watcher);
        if (dealBlock.equals(BigInteger.ZERO)) {
            log.warn("Deal block number is empty, fetching later blockchain " +
                    "events will be more expensive [chainDealId:{}, dealBlock:{}, " +
                    "lastBlock:{}]", dealId, dealBlock, web3jService.getLatestBlockNumber());
        }
        this.handleDeal(dealEvent);
        if (configurationService.getLastSeenBlockWithDeal().compareTo(dealBlock) < 0) {
            configurationService.setLastSeenBlockWithDeal(dealBlock);
        }
    }

    /**
     * Handle new on-chain deals and add its tasks to MongoDB ask collection.
     *
     * @param dealEvent Object representing PoCo SchedulerNoticeEvent
     */
    private void handleDeal(DealEvent dealEvent) {
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
        if (TeeUtils.isTeeTag(tag)
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
        disposeSubscription(dealEventSubscriptionReplay);
        if (outOfService) {
            log.info("OUT-OF-SERVICE do not create replay subscription");
            return;
        }
        final BigInteger lastSeenBlockWithDeal = configurationService.getLastSeenBlockWithDeal();
        final BigInteger replayFromBlock = configurationService.getFromReplay();
        if (replayFromBlock.compareTo(lastSeenBlockWithDeal) >= 0) {
            return;
        }
        dealEventSubscriptionReplay = subscribeToDealEventInRange(replayFromBlock, lastSeenBlockWithDeal);
        configurationService.setFromReplay(lastSeenBlockWithDeal);
    }

    /**
     * Subscribe to onchain deal events for
     * a fixed range of blocks.
     *
     * @param from start block
     * @param to   end block
     * @return disposable subscription
     */
    private Disposable subscribeToDealEventInRange(BigInteger from, BigInteger to) {
        log.info("Replay Watcher DealEvent started [from:{}, to:{}]",
                from, (to == null) ? "latest" : to);
        EthFilter filter = createDealEventFilter(from, to);
        return iexecHubService.getDealEventObservable(filter)
                .map(this::schedulerNoticeToDealEvent)
                .subscribe(dealEvent -> dealEvent.ifPresent(event -> onDealEvent(event, "replay")));
    }

    EthFilter createDealEventFilter(BigInteger from, BigInteger to) {
        final DefaultBlockParameter fromBlock = DefaultBlockParameter.valueOf(from);
        final DefaultBlockParameter toBlock = to == null
                ? DefaultBlockParameterName.LATEST
                : DefaultBlockParameter.valueOf(to);
        final EthFilter filter = new EthFilter(fromBlock, toBlock, chainConfig.getHubAddress());
        final BigInteger poolAddressBigInt = Numeric.toBigInt(chainConfig.getPoolAddress());
        filter.addSingleTopic(EventEncoder.encode(SCHEDULERNOTICE_EVENT));
        filter.addSingleTopic(Numeric.toHexStringWithPrefixZeroPadded(poolAddressBigInt, 64));
        return filter;
    }

    Optional<DealEvent> schedulerNoticeToDealEvent(IexecHubContract.SchedulerNoticeEventResponse schedulerNotice) {
        dealEventsCount++;
        dealEventsCounter.increment();
        BigInteger noticeBlockNumber = schedulerNotice.log.getBlockNumber();
        if (latestBlockNumberWithDeal.compareTo(noticeBlockNumber) < 0) {
            double deltaBlocksNumber = noticeBlockNumber.subtract(latestBlockNumberWithDeal).doubleValue();
            latestBlockNumberWithDeal = noticeBlockNumber;
            latestBlockNumberWithDealCounter.increment(deltaBlocksNumber);
        }
        log.info("Received new deal [blockNumber:{}, chainDealId:{}, dealEventsCount:{}]",
                schedulerNotice.log.getBlockNumber(), BytesUtils.bytesToString(schedulerNotice.dealid), dealEventsCount);
        if (schedulerNotice.workerpool.equalsIgnoreCase(chainConfig.getPoolAddress())) {
            return Optional.of(new DealEvent(schedulerNotice));
        }
        log.warn("This deal event should not have been received [blockNumber:{}, chainDealId:{}, dealEventsCount:{}]",
                schedulerNotice.log.getBlockNumber(), BytesUtils.bytesToString(schedulerNotice.dealid), dealEventsCount);
        return Optional.empty();
    }
}
