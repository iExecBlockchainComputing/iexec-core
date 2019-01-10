package com.iexec.core.chain;

import com.iexec.common.chain.ChainApp;
import com.iexec.common.chain.ChainAppParams;
import com.iexec.common.chain.ChainCategory;
import com.iexec.common.chain.ChainDeal;
import com.iexec.core.configuration.ConfigurationService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.event.TaskCreatedEvent;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import rx.Observable;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
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

        when(iexecHubService.getDealEventObservableToLatest(from)).thenReturn(Observable.just(dealEvent));
        when(iexecHubService.getChainDeal(dealEvent.get().getChainDealId())).thenReturn(Optional.of(chainDeal));
        // when(taskService.addTask(any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        Mockito.verify(configurationService, Mockito.times(1))
                .setLastSeenBlockWithDeal(blockOfDeal);
        Mockito.verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
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
        when(iexecHubService.getDealEventObservableToLatest(from)).thenReturn(Observable.just(dealEvent));

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        Mockito.verify(configurationService, Mockito.times(1))
                .setLastSeenBlockWithDeal(blockOfDeal);
    }

    @Test
    public void shouldUpdateLastSeenBlockWhenOneDealAndCreateTask() {
        ChainAppParams chainAppParams = new ChainAppParams();
        chainAppParams.setUri("uri");

        ChainApp chainApp = new ChainApp();
        chainApp.setParams(chainAppParams);

        ChainCategory chainCategory = new ChainCategory();

        ChainDeal chainDeal = ChainDeal.builder()
                .botFirst(BigInteger.valueOf(0))
                .botSize(BigInteger.valueOf(1))
                .chainApp(chainApp)
                .chainCategory(chainCategory)
                .params(Arrays.asList("param1"))
                .trust(BigInteger.valueOf(3))
                .build();

        BigInteger from = BigInteger.valueOf(0);
        BigInteger blockOfDeal = BigInteger.valueOf(3);
        Optional<DealEvent> dealEvent = Optional.of(DealEvent.builder()
                .chainDealId("chainDealId")
                .blockNumber(blockOfDeal)
                .build());

        Task task = new Task();

        when(iexecHubService.getDealEventObservableToLatest(from)).thenReturn(Observable.just(dealEvent));
        when(iexecHubService.getChainDeal(dealEvent.get().getChainDealId())).thenReturn(Optional.of(chainDeal));
        when(taskService.addTask(any(), Mockito.anyInt(), any(), any(), Mockito.anyInt(), any())).thenReturn(Optional.of(task));
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);

        ArgumentCaptor<TaskCreatedEvent> argumentCaptor = ArgumentCaptor.forClass(TaskCreatedEvent.class);

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        Mockito.verify(configurationService, Mockito.times(1))
                .setLastSeenBlockWithDeal(blockOfDeal);
        Mockito.verify(applicationEventPublisher, Mockito.times(1))
                .publishEvent(Mockito.any(TaskCreatedEvent.class));

        Mockito.verify(applicationEventPublisher, Mockito.times(1))
        .publishEvent(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).isEqualTo(new TaskCreatedEvent(task));
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
        when(iexecHubService.getDealEventObservableToLatest(from)).thenReturn(Observable.just(dealEvent1, dealEvent2));

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
        when(iexecHubService.getDealEventObservableToLatest(from)).thenReturn(Observable.just(dealEvent1));

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
        when(iexecHubService.getDealEventObservable(any(), any())).thenReturn(Observable.just(dealEvent1));

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
        when(iexecHubService.getDealEventObservable(any(), any())).thenReturn(Observable.just(dealEvent1));

        dealWatcherService.replayDealEvent();

        Mockito.verify(iexecHubService, Mockito.times(0))
                .getChainDeal(any());
    }
}
