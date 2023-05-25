/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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

import com.iexec.commons.poco.chain.ChainApp;
import com.iexec.commons.poco.chain.ChainCategory;
import com.iexec.commons.poco.chain.ChainDeal;
import com.iexec.commons.poco.chain.DealParams;
import com.iexec.commons.poco.contract.generated.IexecHubContract;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.core.configuration.ConfigurationService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.event.TaskCreatedEvent;
import io.reactivex.Flowable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DealWatcherServiceTests {

    @Mock
    private ChainConfig chainConfig;
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

    private IexecHubContract.SchedulerNoticeEventResponse createSchedulerNotice(BigInteger noticeBlockNumber) {
        IexecHubContract.SchedulerNoticeEventResponse schedulerNotice = new IexecHubContract.SchedulerNoticeEventResponse();
        schedulerNotice.workerpool = "0x1";
        schedulerNotice.dealid = "chainDealId".getBytes();
        Log schedulerNoticeLog = new Log();
        schedulerNoticeLog.setBlockNumber(noticeBlockNumber.toString());
        schedulerNotice.log = schedulerNoticeLog;
        return schedulerNotice;
    }

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        when(chainConfig.getHubAddress()).thenReturn("hubAddress");
        when(chainConfig.getPoolAddress()).thenReturn("0x1");
    }

    @Test
    void shouldRun() {
        BigInteger blockNumber = BigInteger.TEN;
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(blockNumber);
        when(iexecHubService.getDealEventObservable(any())).thenReturn(Flowable.empty());
        dealWatcherService.run();
        verify(iexecHubService).getDealEventObservable(any());
    }

    @Test
    void shouldUpdateLastSeenBlockWhenOneDeal() {
        BigInteger from = BigInteger.valueOf(0);
        BigInteger blockOfDeal = BigInteger.valueOf(3);
        IexecHubContract.SchedulerNoticeEventResponse schedulerNotice = createSchedulerNotice(blockOfDeal);

        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);
        when(iexecHubService.getDealEventObservable(any())).thenReturn(Flowable.just(schedulerNotice));

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        verify(configurationService).setLastSeenBlockWithDeal(blockOfDeal);
    }

    @Test
    void shouldUpdateLastSeenBlockWhenOneDealAndCreateTask() {
        ChainApp chainApp = ChainApp.builder()
                .uri("0x00").build();

        ChainCategory chainCategory = ChainCategory.builder().build();

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
        IexecHubContract.SchedulerNoticeEventResponse schedulerNotice = createSchedulerNotice(blockOfDeal);

        Task task = new Task();

        when(iexecHubService.getDealEventObservable(any())).thenReturn(Flowable.just(schedulerNotice));
        when(iexecHubService.getChainDeal(BytesUtils.bytesToString(schedulerNotice.dealid))).thenReturn(Optional.of(chainDeal));
        when(iexecHubService.isBeforeContributionDeadline(chainDeal)).thenReturn(true);
        when(taskService.addTask(any(), anyInt(), anyLong(), any(), any(), anyInt(), anyLong(), any(), any(), any()))
                        .thenReturn(Optional.of(task));
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);

        ArgumentCaptor<TaskCreatedEvent> argumentCaptor = ArgumentCaptor.forClass(TaskCreatedEvent.class);

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        verify(configurationService).setLastSeenBlockWithDeal(blockOfDeal);
        verify(applicationEventPublisher).publishEvent(any(TaskCreatedEvent.class));
        verify(applicationEventPublisher).publishEvent(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).isEqualTo(new TaskCreatedEvent(task.getChainTaskId()));
    }

    @Test
    void shouldUpdateLastSeenBlockWhenOneDealAndNotCreateTaskSinceDealIsExpired() {
        ChainDeal chainDeal = ChainDeal.builder()
                .botFirst(BigInteger.valueOf(0))
                .botSize(BigInteger.valueOf(1))
                .chainApp(ChainApp.builder().uri("0x00").build())
                .chainCategory(ChainCategory.builder().build())
                .params(DealParams.builder().iexecArgs("args").build())
                .trust(BigInteger.valueOf(3))
                .build();

        BigInteger from = BigInteger.valueOf(0);
        BigInteger blockOfDeal = BigInteger.valueOf(3);
        IexecHubContract.SchedulerNoticeEventResponse schedulerNotice = createSchedulerNotice(blockOfDeal);

        when(iexecHubService.getDealEventObservable(any())).thenReturn(Flowable.just(schedulerNotice));
        when(iexecHubService.getChainDeal(BytesUtils.bytesToString(schedulerNotice.dealid))).thenReturn(Optional.of(chainDeal));
        when(iexecHubService.isBeforeContributionDeadline(chainDeal)).thenReturn(false);
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        verify(configurationService).setLastSeenBlockWithDeal(blockOfDeal);
        verifyNoInteractions(taskService, applicationEventPublisher);
    }

    @Test
    void shouldUpdateLastSeenBlockWhenOneDealAndNotCreateTaskSinceBotSizeIsZero() {
        BigInteger from = BigInteger.valueOf(0);
        BigInteger blockOfDeal = BigInteger.valueOf(3);
        IexecHubContract.SchedulerNoticeEventResponse schedulerNotice = createSchedulerNotice(blockOfDeal);

        ChainDeal chainDeal = ChainDeal.builder()
            .botFirst(BigInteger.valueOf(0))
            .botSize(BigInteger.valueOf(0))
            .build();

        when(iexecHubService.getDealEventObservable(any())).thenReturn(Flowable.just(schedulerNotice));
        when(iexecHubService.getChainDeal(BytesUtils.bytesToString(schedulerNotice.dealid))).thenReturn(Optional.of(chainDeal));
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        verify(configurationService).setLastSeenBlockWithDeal(blockOfDeal);
        verifyNoInteractions(taskService, applicationEventPublisher);
    }

    @Test
    void shouldUpdateLastSeenBlockWhenOneDealButNotCreateTaskSinceExceptionThrown() {
        BigInteger from = BigInteger.valueOf(0);
        BigInteger blockOfDeal = BigInteger.valueOf(3);
        IexecHubContract.SchedulerNoticeEventResponse schedulerNotice = createSchedulerNotice(blockOfDeal);

        ChainDeal chainDeal = ChainDeal.builder()
            .botFirst(BigInteger.valueOf(0))
            .botSize(BigInteger.valueOf(1))
            .build();

        when(iexecHubService.getDealEventObservable(any())).thenReturn(Flowable.just(schedulerNotice));
        when(iexecHubService.getChainDeal(BytesUtils.bytesToString(schedulerNotice.dealid))).thenReturn(Optional.of(chainDeal));
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        verify(configurationService).setLastSeenBlockWithDeal(blockOfDeal);
        verifyNoInteractions(taskService, applicationEventPublisher);
    }

    @Test
    void shouldUpdateLastSeenBlockTwiceWhenTwoDeals() {
        BigInteger from = BigInteger.valueOf(0);
        BigInteger blockOfDeal1 = BigInteger.valueOf(3);
        BigInteger blockOfDeal2 = BigInteger.valueOf(5);
        IexecHubContract.SchedulerNoticeEventResponse schedulerNotice1 = createSchedulerNotice(blockOfDeal1);
        IexecHubContract.SchedulerNoticeEventResponse schedulerNotice2 = createSchedulerNotice(blockOfDeal2);

        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);
        when(iexecHubService.getDealEventObservable(any())).thenReturn(Flowable.just(schedulerNotice1, schedulerNotice2));

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        verify(configurationService).setLastSeenBlockWithDeal(blockOfDeal1);
        verify(configurationService).setLastSeenBlockWithDeal(blockOfDeal2);
    }

    @Test
    void shouldNotUpdateLastSeenBlockWhenReceivingOldMissedDeal() {
        BigInteger from = BigInteger.valueOf(5);
        BigInteger blockOfDeal = BigInteger.valueOf(3);
        IexecHubContract.SchedulerNoticeEventResponse schedulerNotice = createSchedulerNotice(blockOfDeal);

        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);
        when(iexecHubService.getDealEventObservable(any())).thenReturn(Flowable.just(schedulerNotice));

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        verify(configurationService).getLastSeenBlockWithDeal();
        verify(configurationService, never()).setLastSeenBlockWithDeal(blockOfDeal);
    }

    @Test
    void shouldReplayAllEventInRange() {
        BigInteger blockOfDeal = BigInteger.valueOf(3);
        IexecHubContract.SchedulerNoticeEventResponse schedulerNotice = createSchedulerNotice(blockOfDeal);

        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(BigInteger.TEN);
        when(configurationService.getFromReplay()).thenReturn(BigInteger.ZERO);
        when(iexecHubService.getDealEventObservable(any())).thenReturn(Flowable.just(schedulerNotice));

        dealWatcherService.replayDealEvent();

        verify(iexecHubService).getChainDeal(any());
    }

    @Test
    void shouldNotReplayIfFromReplayEqualsLastSeenBlock() {
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(BigInteger.ZERO);
        when(configurationService.getFromReplay()).thenReturn(BigInteger.ZERO);
        dealWatcherService.replayDealEvent();
        verifyNoInteractions(iexecHubService);
    }
}
