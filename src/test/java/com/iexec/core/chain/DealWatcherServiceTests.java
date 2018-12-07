package com.iexec.core.chain;

import com.iexec.core.configuration.ConfigurationService;
import com.iexec.core.task.TaskService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import rx.Observable;

import java.math.BigInteger;

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
    public void shouldUpdateLastSeenBlockWhenOneDeal() {
        BigInteger from = BigInteger.valueOf(0);
        BigInteger blockOfDeal = BigInteger.valueOf(3);
        DealEvent dealEvent = DealEvent
                .builder()
                .chainDealId("chainDealId")
                .blockNumber(blockOfDeal)
                .build();

        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);
        when(iexecHubService.getDealEventObservableToLatest(from)).thenReturn(Observable.just(dealEvent));

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        Mockito.verify(configurationService, Mockito.times(1))
                .setLastSeenBlockWithDeal(blockOfDeal);
    }

    @Test
    public void shouldUpdateLastSeenBlockTwiceWhenTwoDeals() {
        BigInteger from = BigInteger.valueOf(0);
        BigInteger blockOfDeal1 = BigInteger.valueOf(3);
        DealEvent dealEvent1 = DealEvent
                .builder()
                .chainDealId("chainDealId1")
                .blockNumber(blockOfDeal1)
                .build();
        BigInteger blockOfDeal2 = BigInteger.valueOf(5);
        DealEvent dealEvent2 = DealEvent
                .builder()
                .chainDealId("chainDealId2")
                .blockNumber(blockOfDeal2)
                .build();

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
        DealEvent dealEvent1 = DealEvent
                .builder()
                .chainDealId("chainDealId1")
                .blockNumber(blockOfDeal1)
                .build();

        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);
        when(iexecHubService.getDealEventObservableToLatest(from)).thenReturn(Observable.just(dealEvent1));

        dealWatcherService.subscribeToDealEventFromOneBlockToLatest(from);

        Mockito.verify(configurationService, Mockito.times(0))
                .setLastSeenBlockWithDeal(blockOfDeal1);
    }

    @Test
    public void shouldReplayAllEventInRange() {
        BigInteger blockOfDeal1 = BigInteger.valueOf(3);
        DealEvent dealEvent1 = DealEvent
                .builder()
                .chainDealId("chainDealId1")
                .blockNumber(blockOfDeal1)
                .build();

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
        DealEvent dealEvent1 = DealEvent
                .builder()
                .chainDealId("chainDealId1")
                .blockNumber(blockOfDeal1)
                .build();

        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(BigInteger.ZERO);
        when(configurationService.getFromReplay()).thenReturn(BigInteger.ZERO);
        when(iexecHubService.getDealEventObservable(any(), any())).thenReturn(Observable.just(dealEvent1));

        dealWatcherService.replayDealEvent();

        Mockito.verify(iexecHubService, Mockito.times(0))
                .getChainDeal(any());
    }
}
