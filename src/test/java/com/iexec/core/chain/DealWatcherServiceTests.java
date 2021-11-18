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

import com.iexec.common.chain.ChainApp;
import com.iexec.common.chain.ChainCategory;
import com.iexec.common.chain.ChainDeal;
import com.iexec.common.chain.DealParams;
import com.iexec.core.configuration.ConfigurationService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.event.TaskCreatedEvent;
import io.reactivex.Flowable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigInteger;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DealWatcherServiceTests {

    @Mock
    private IexecHubService iexecHubService;

    @Mock
    private ConfigurationService configurationService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private TaskService taskService;

    @InjectMocks
    private DealWatcherService dealWatcherService;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldRun() {
        BigInteger blockNumber = BigInteger.TEN;
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(blockNumber);
        when(iexecHubService.getDealEventObservableToLatest(blockNumber))
            .thenReturn(Flowable.just(Optional.empty()));

        dealWatcherService.run();

        Mockito.verify(iexecHubService, Mockito.times(1))
            .getDealEventObservableToLatest(blockNumber);
    }

    @Test
    public void shouldUpdateLastSeenBlockWhenOneDeal() {
        BigInteger from = BigInteger.valueOf(0);
        BigInteger blockOfDeal = BigInteger.valueOf(3);
        Optional<DealEvent> dealEvent = Optional.of(DealEvent
                .builder()
                .chainDealId("chainDealId")
                .blockNumber(blockOfDeal)
                .build());

        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);
        when(iexecHubService.getDealEventObservableToLatest(from)).thenReturn(Flowable.just(dealEvent));

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        Mockito.verify(configurationService, Mockito.times(1))
                .setLastSeenBlockWithDeal(blockOfDeal);
    }

    @Test
    public void shouldUpdateLastSeenBlockWhenOneDealAndCreateTask() {
        ChainApp chainApp = new ChainApp();
        chainApp.setUri("0x00");

        ChainCategory chainCategory = new ChainCategory();

        ChainDeal chainDeal = ChainDeal.builder()
                .botFirst(BigInteger.valueOf(0))
                .botSize(BigInteger.valueOf(1))
                .chainApp(chainApp)
                .chainCategory(chainCategory)
                .params(DealParams.builder().iexecArgs("args").build())
                .trust(BigInteger.valueOf(3))
                .build();

        BigInteger from = BigInteger.valueOf(0);
        BigInteger blockOfDeal = BigInteger.valueOf(3);
        Optional<DealEvent> dealEvent = Optional.of(DealEvent.builder()
                .chainDealId("chainDealId")
                .blockNumber(blockOfDeal)
                .build());

        Task task = new Task();

        when(iexecHubService.getDealEventObservableToLatest(from)).thenReturn(Flowable.just(dealEvent));
        when(iexecHubService.getChainDeal(dealEvent.get().getChainDealId())).thenReturn(Optional.of(chainDeal));
        when(taskService.addTask(any(), Mockito.anyInt(), anyLong(), any(), any(), Mockito.anyInt(), anyLong(), any(), any(), any()))
                        .thenReturn(Optional.of(task));
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);
        when(iexecHubService.isBeforeContributionDeadline(chainDeal)).thenReturn(true);

        ArgumentCaptor<TaskCreatedEvent> argumentCaptor = ArgumentCaptor.forClass(TaskCreatedEvent.class);

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        Mockito.verify(configurationService, Mockito.times(1))
                .setLastSeenBlockWithDeal(blockOfDeal);
        Mockito.verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(Mockito.any(TaskCreatedEvent.class));

        Mockito.verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).isEqualTo(new TaskCreatedEvent(task.getChainTaskId()));
    }

    @Test
    public void shouldUpdateLastSeenBlockWhenOneDealAndNotCreateTaskSinceDealIsExpired() {
        ChainDeal chainDeal = ChainDeal.builder()
                .botFirst(BigInteger.valueOf(0))
                .botSize(BigInteger.valueOf(1))
                .chainApp(ChainApp.builder().uri("0x00").build())
                .chainCategory(new ChainCategory())
                .params(DealParams.builder().iexecArgs("args").build())
                .trust(BigInteger.valueOf(3))
                .build();

        BigInteger from = BigInteger.valueOf(0);
        BigInteger blockOfDeal = BigInteger.valueOf(3);
        Optional<DealEvent> dealEvent = Optional.of(DealEvent.builder()
                .chainDealId("chainDealId")
                .blockNumber(blockOfDeal)
                .build());

        when(iexecHubService.getDealEventObservableToLatest(from))
                .thenReturn(Flowable.just(dealEvent));
        when(iexecHubService.getChainDeal(anyString()))
                .thenReturn(Optional.of(chainDeal));
        when(iexecHubService.isBeforeContributionDeadline(chainDeal))
                .thenReturn(false);
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        verify(configurationService, times(1))
                .setLastSeenBlockWithDeal(blockOfDeal);
        verify(applicationEventPublisher, never())
                .publishEvent(any());
        verify(taskService, never())
                .addTask(anyString(), anyInt(), anyLong(),
                        anyString(), anyString(), anyInt(), anyLong(),
                        anyString(), any(), any());
    }

    @Test
    public void shouldUpdateLastSeenBlockWhenOneDealAndNotCreateTaskSinceBotSizeIsZero() {
        BigInteger from = BigInteger.valueOf(0);
        BigInteger blockOfDeal = BigInteger.valueOf(3);
        Optional<DealEvent> dealEvent = Optional.of(DealEvent.builder()
            .chainDealId("chainDealId")
            .blockNumber(blockOfDeal)
            .build());

        ChainDeal chainDeal = ChainDeal.builder()
            .botFirst(BigInteger.valueOf(0))
            .botSize(BigInteger.valueOf(0))
            .build();

        when(iexecHubService.getDealEventObservableToLatest(from)).thenReturn(Flowable.just(dealEvent));
        when(iexecHubService.getChainDeal(dealEvent.get().getChainDealId())).thenReturn(Optional.of(chainDeal));
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        Mockito.verify(configurationService, Mockito.times(1))
            .setLastSeenBlockWithDeal(blockOfDeal);
        Mockito.verify(applicationEventPublisher, Mockito.times(0))
            .publishEvent(any());
    }

    @Test
    public void shouldUpdateLastSeenBlockWhenOneDealButNotCreateTaskSinceExceptionThrown() {
        BigInteger from = BigInteger.valueOf(0);
        BigInteger blockOfDeal = BigInteger.valueOf(3);
        Optional<DealEvent> dealEvent = Optional.of(DealEvent.builder()
            .chainDealId("chainDealId")
            .blockNumber(blockOfDeal)
            .build());

        ChainDeal chainDeal = ChainDeal.builder()
            .botFirst(BigInteger.valueOf(0))
            .botSize(BigInteger.valueOf(1))
            .build();


        when(iexecHubService.getDealEventObservableToLatest(from)).thenReturn(Flowable.just(dealEvent));
        when(iexecHubService.getChainDeal(dealEvent.get().getChainDealId())).thenReturn(Optional.of(chainDeal));
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        Mockito.verify(configurationService, Mockito.times(1))
                .setLastSeenBlockWithDeal(blockOfDeal);
        Mockito.verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
    }

    @Test
    public void shouldUpdateLastSeenBlockTwiceWhenTwoDeals() {
        BigInteger from = BigInteger.valueOf(0);
        BigInteger blockOfDeal1 = BigInteger.valueOf(3);
        Optional<DealEvent> dealEvent1 = Optional.of(DealEvent
                .builder()
                .chainDealId("chainDealId1")
                .blockNumber(blockOfDeal1)
                .build());
        BigInteger blockOfDeal2 = BigInteger.valueOf(5);
        Optional<DealEvent> dealEvent2 = Optional.of(DealEvent
                .builder()
                .chainDealId("chainDealId2")
                .blockNumber(blockOfDeal2)
                .build());

        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);
        when(iexecHubService.getDealEventObservableToLatest(from)).thenReturn(Flowable.just(dealEvent1, dealEvent2));

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        Mockito.verify(configurationService, Mockito.times(1))
                .setLastSeenBlockWithDeal(blockOfDeal1);
        Mockito.verify(configurationService, Mockito.times(1))
                .setLastSeenBlockWithDeal(blockOfDeal2);
    }

    @Test
    public void shouldNOtUpdateLastSeenBlockWhenReceivingOldMissedDeal() {
        BigInteger from = BigInteger.valueOf(5);
        BigInteger blockOfDeal1 = BigInteger.valueOf(3);
        Optional<DealEvent> dealEvent1 = Optional.of(DealEvent
                .builder()
                .chainDealId("chainDealId1")
                .blockNumber(blockOfDeal1)
                .build());

        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);
        when(iexecHubService.getDealEventObservableToLatest(from)).thenReturn(Flowable.just(dealEvent1));

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        Mockito.verify(configurationService, Mockito.times(0))
                .setLastSeenBlockWithDeal(blockOfDeal1);
    }

    @Test
    public void shouldReplayAllEventInRange() {
        BigInteger blockOfDeal1 = BigInteger.valueOf(3);
        Optional<DealEvent> dealEvent1 = Optional.of(DealEvent
                .builder()
                .chainDealId("chainDealId1")
                .blockNumber(blockOfDeal1)
                .build());

        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(BigInteger.TEN);
        when(configurationService.getFromReplay()).thenReturn(BigInteger.ZERO);
        when(iexecHubService.getDealEventObservable(any(), any())).thenReturn(Flowable.just(dealEvent1));

        dealWatcherService.replayDealEvent();

        Mockito.verify(iexecHubService, Mockito.times(1))
                .getChainDeal(any());
    }

    @Test
    public void shouldNotReplayIfFromReplayEqualsLastSeenBlock() {
        BigInteger blockOfDeal1 = BigInteger.valueOf(3);
        Optional<DealEvent> dealEvent1 = Optional.of(DealEvent
                .builder()
                .chainDealId("chainDealId1")
                .blockNumber(blockOfDeal1)
                .build());

        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(BigInteger.ZERO);
        when(configurationService.getFromReplay()).thenReturn(BigInteger.ZERO);
        when(iexecHubService.getDealEventObservable(any(), any())).thenReturn(Flowable.just(dealEvent1));

        dealWatcherService.replayDealEvent();

        Mockito.verify(iexecHubService, Mockito.times(0))
                .getChainDeal(any());
    }
}
