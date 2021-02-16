/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.chain.ChainDeal;
import com.iexec.common.utils.BytesUtils;
import com.iexec.core.configuration.ConfigurationService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.event.TaskCreatedEvent;
import io.reactivex.disposables.Disposable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.Optional;

@Slf4j
@Service
public class DealWatcherService {

    private final IexecHubService iexecHubService;
    private final ConfigurationService configurationService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final TaskService taskService;
    private final Web3jService web3jService;
    // internal variables
    private Disposable dealEventSubscriptionReplay;

    @Autowired
    public DealWatcherService(IexecHubService iexecHubService,
                              ConfigurationService configurationService,
                              ApplicationEventPublisher applicationEventPublisher,
                              TaskService taskService,
                              Web3jService web3jService) {
        this.iexecHubService = iexecHubService;
        this.configurationService = configurationService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.taskService = taskService;
        this.web3jService = web3jService;
    }

    /**
     * This should be non-blocking to librate
     * the main thread, since deals can have
     * a large number of tasks (BoT).
     */
    @Async
    public void run() {
        subscribeToDealEventFromOneBlockToLatest(configurationService.getLastSeenBlockWithDeal());
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
        return iexecHubService.getDealEventObservableToLatest(from)
                .subscribe(dealEvent -> dealEvent.ifPresent(this::onDealEvent));
    }

    /**
     * Update last seen block in the database
     * and run {@link DealEvent} handler.
     * 
     * @param dealEvent
     */
    private void onDealEvent(DealEvent dealEvent) {
        String dealId = dealEvent.getChainDealId();
        BigInteger dealBlock = dealEvent.getBlockNumber();
        log.info("Received deal [dealId:{}, block:{}]", dealId,
                dealBlock);
        if (dealBlock == null || dealBlock.equals(BigInteger.ZERO)){
            log.warn("Deal block number is empty, fetching later blockchain " +
                    "events will be more expensive [chainDealId:{}, dealBlock:{}, " +
                    "lastBlock:{}]", dealId, dealBlock, web3jService.getLatestBlockNumber());
            dealEvent.setBlockNumber(BigInteger.ZERO);
        }
        this.handleDeal(dealEvent);
        if (configurationService.getLastSeenBlockWithDeal().compareTo(dealBlock) < 0) {
            configurationService.setLastSeenBlockWithDeal(dealBlock);
        }
    }

    /**
     * Handle new onchain deals and add its tasks
     * to db.
     *
     * @param dealEvent
     */
    private void handleDeal(DealEvent dealEvent) {
        String chainDealId = dealEvent.getChainDealId();
        Optional<ChainDeal> oChainDeal = iexecHubService.getChainDeal(chainDealId);
        if (oChainDeal.isEmpty()) {
            log.error("Could not get chain deal [chainDealId:{}]", chainDealId);
            return;
        }
        ChainDeal chainDeal = oChainDeal.get();
        // do not process deals after deadline
        if (!iexecHubService.isBeforeContributionDeadline(chainDeal)) {
            log.error("Deal has expired [chainDealId:{}, deadline:{}]",
                    chainDealId, iexecHubService.getChainDealContributionDeadline(chainDeal));
            return;
        }
        int startBag = chainDeal.getBotFirst().intValue();
        int endBag = chainDeal.getBotFirst().intValue() + chainDeal.getBotSize().intValue();
        for (int taskIndex = startBag; taskIndex < endBag; taskIndex++) {
            Optional<Task> optional = taskService.addTask(
                    chainDealId,
                    taskIndex,
                    dealEvent.getBlockNumber().longValue(),
                    BytesUtils.hexStringToAscii(chainDeal.getChainApp().getUri()),
                    chainDeal.getParams().getIexecArgs(),
                    chainDeal.getTrust().intValue(),
                    chainDeal.getChainCategory().getMaxExecutionTime(),
                    chainDeal.getTag(),
                    iexecHubService.getChainDealContributionDeadline(chainDeal),
                    iexecHubService.getChainDealFinalDeadline(chainDeal));
            optional.ifPresent(task -> applicationEventPublisher
                    .publishEvent(new TaskCreatedEvent(task.getChainTaskId())));
        }
    }

    /*
     * Some deal events are sometimes missed by #schedulerNoticeEventObservable method
     * so we decide to replay events from times to times (already saved events will be ignored)
     */
    @Scheduled(fixedRateString = "#{@cronConfiguration.getDealReplay()}")
    void replayDealEvent() {
        BigInteger lastSeenBlockWithDeal = configurationService.getLastSeenBlockWithDeal();
        BigInteger replayFromBlock = configurationService.getFromReplay();
        if (replayFromBlock.compareTo(lastSeenBlockWithDeal) >= 0) {
            return;
        }
        if (this.dealEventSubscriptionReplay != null && !this.dealEventSubscriptionReplay.isDisposed()) {
            this.dealEventSubscriptionReplay.dispose();
        }
        this.dealEventSubscriptionReplay = subscribeToDealEventInRange(replayFromBlock, lastSeenBlockWithDeal);
        configurationService.setFromReplay(lastSeenBlockWithDeal);
    }

    /**
     * Subscribe to onchain deal events for
     * a fixed range of blocks.
     * 
     * @param from start block
     * @param to end block
     * @return disposable subscription
     */
    private Disposable subscribeToDealEventInRange(BigInteger from, BigInteger to) {
        log.info("Replay Watcher DealEvent started [from:{}, to:{}]",
                from, (to == null) ? "latest" : to);
        return iexecHubService.getDealEventObservable(from, to)
                .subscribe(dealEvent -> dealEvent.ifPresent(this::onDealEvent));
    }
}
