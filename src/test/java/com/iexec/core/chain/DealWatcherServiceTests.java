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

import com.iexec.commons.poco.chain.ChainApp;
import com.iexec.commons.poco.chain.ChainCategory;
import com.iexec.commons.poco.chain.ChainDeal;
import com.iexec.commons.poco.chain.DealParams;
import com.iexec.commons.poco.encoding.LogTopic;
import com.iexec.core.chain.event.DealEvent;
import com.iexec.core.configuration.ConfigurationService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.event.TaskCreatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.iexec.commons.poco.tee.TeeUtils.TEE_GRAMINE_ONLY_TAG;
import static com.iexec.commons.poco.tee.TeeUtils.TEE_SCONE_ONLY_TAG;
import static com.iexec.core.TestUtils.NO_TEE_TAG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DealWatcherServiceTests {

    private static final String DEAL_ID = "0x0";

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

    @Mock
    private Web3jService web3jService;

    @Mock
    private Web3j web3j;

    @Mock
    private Request<?, EthLog> ethLogRequest;

    @InjectMocks
    private DealWatcherService dealWatcherService;

    @BeforeAll
    static void initRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
    }

    void initMocks() {
        when(chainConfig.getHubAddress()).thenReturn("hubAddress");
        when(chainConfig.getPoolAddress()).thenReturn("0xe0cdddbd7956622874d4eb796e8ec600ad2ff14f");
    }

    @AfterEach
    void afterEach() {
        Metrics.globalRegistry.clear();
    }

    @Test
    void shouldReturnZeroForAllCountersWhereNothingHasAppended() {
        Counter dealsCounter = Metrics.globalRegistry.find(DealWatcherService.METRIC_DEALS_COUNT).counter();
        Counter dealsEventsCounter = Metrics.globalRegistry.find(DealWatcherService.METRIC_DEALS_EVENTS_COUNT).counter();
        Counter dealsReplayCounter = Metrics.globalRegistry.find(DealWatcherService.METRIC_DEALS_REPLAY_COUNT).counter();
        Counter lastBlockCounter = Metrics.globalRegistry.find(DealWatcherService.METRIC_DEALS_LAST_BLOCK).counter();

        assertThat(dealsCounter).isNotNull();
        assertThat(dealsEventsCounter).isNotNull();
        assertThat(dealsReplayCounter).isNotNull();
        assertThat(lastBlockCounter).isNotNull();

        assertThat(dealsCounter.count()).isZero();
        assertThat(dealsEventsCounter.count()).isZero();
        assertThat(dealsReplayCounter.count()).isZero();
        assertThat(lastBlockCounter.count()).isZero();
    }

    // region onDealEvent
    @Test
    void shouldUpdateLastSeenBlockWhenOneDeal() {
        BigInteger from = BigInteger.valueOf(0);
        BigInteger blockOfDeal = BigInteger.valueOf(3);
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);

        dealWatcherService.onDealEvent(new DealEvent(this, DEAL_ID, blockOfDeal));

        verify(configurationService).setLastSeenBlockWithDeal(blockOfDeal);
        Counter lastBlockCounter = Metrics.globalRegistry.find(DealWatcherService.METRIC_DEALS_LAST_BLOCK).counter();
        Counter dealsCounter = Metrics.globalRegistry.find(DealWatcherService.METRIC_DEALS_COUNT).counter();
        Counter dealsEventsCounter = Metrics.globalRegistry.find(DealWatcherService.METRIC_DEALS_EVENTS_COUNT).counter();
        assertThat(lastBlockCounter).isNotNull();
        assertThat(dealsCounter).isNotNull();
        assertThat(dealsEventsCounter).isNotNull();
        assertThat(lastBlockCounter.count()).isEqualTo(blockOfDeal.doubleValue());
        assertThat(dealsCounter.count()).isOne();
        assertThat(dealsEventsCounter.count()).isZero();
    }

    @Test
    void shouldUpdateLastSeenBlockWhenOneDealAndCreateTask() {
        ChainApp chainApp = ChainApp.builder()
                .multiaddr("0x00").build();

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
        Task task = new Task();
        when(iexecHubService.getChainDealWithDetails(DEAL_ID)).thenReturn(Optional.of(chainDeal));
        when(iexecHubService.isBeforeContributionDeadline(chainDeal)).thenReturn(true);
        when(taskService.addTask(any(), anyInt(), anyLong(), any(), any(), anyInt(), anyLong(), any(), any(), any()))
                .thenReturn(Optional.of(task));
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);

        ArgumentCaptor<TaskCreatedEvent> argumentCaptor = ArgumentCaptor.forClass(TaskCreatedEvent.class);

        dealWatcherService.onDealEvent(new DealEvent(this, DEAL_ID, blockOfDeal));

        verify(configurationService).setLastSeenBlockWithDeal(blockOfDeal);
        verify(applicationEventPublisher).publishEvent(any(TaskCreatedEvent.class));
        verify(applicationEventPublisher).publishEvent(argumentCaptor.capture());
        assertThat(argumentCaptor.getValue())
                .usingRecursiveComparison()
                .ignoringFields("timestamp")
                .isEqualTo(new TaskCreatedEvent(dealWatcherService, task.getChainTaskId(), task.getChainDealId(), task.getTaskIndex()));
    }

    @Test
    void shouldUpdateLastSeenBlockWhenOneDealAndNotCreateTaskSinceDealIsExpired() {
        ChainDeal chainDeal = ChainDeal.builder()
                .botFirst(BigInteger.valueOf(0))
                .botSize(BigInteger.valueOf(1))
                .chainApp(ChainApp.builder().multiaddr("0x00").build())
                .chainCategory(ChainCategory.builder().build())
                .params(DealParams.builder().iexecArgs("args").build())
                .trust(BigInteger.valueOf(3))
                .build();

        BigInteger from = BigInteger.valueOf(0);
        BigInteger blockOfDeal = BigInteger.valueOf(3);
        when(iexecHubService.getChainDealWithDetails(DEAL_ID)).thenReturn(Optional.of(chainDeal));
        when(iexecHubService.isBeforeContributionDeadline(chainDeal)).thenReturn(false);
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);

        dealWatcherService.onDealEvent(new DealEvent(this, DEAL_ID, blockOfDeal));

        verify(configurationService).setLastSeenBlockWithDeal(blockOfDeal);
        verifyNoInteractions(taskService, applicationEventPublisher);
    }

    @Test
    void shouldUpdateLastSeenBlockWhenOneDealAndNotCreateTaskSinceBotSizeIsZero() {
        BigInteger from = BigInteger.valueOf(0);
        BigInteger blockOfDeal = BigInteger.valueOf(3);
        ChainDeal chainDeal = ChainDeal.builder()
                .botFirst(BigInteger.valueOf(0))
                .botSize(BigInteger.valueOf(0))
                .build();
        when(iexecHubService.getChainDealWithDetails(DEAL_ID)).thenReturn(Optional.of(chainDeal));
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);

        dealWatcherService.onDealEvent(new DealEvent(this, DEAL_ID, blockOfDeal));

        verify(configurationService).setLastSeenBlockWithDeal(blockOfDeal);
        verifyNoInteractions(taskService, applicationEventPublisher);
    }

    @Test
    void shouldUpdateLastSeenBlockWhenOneDealButNotCreateTaskSinceExceptionThrown() {
        BigInteger from = BigInteger.valueOf(0);
        BigInteger blockOfDeal = BigInteger.valueOf(3);
        ChainDeal chainDeal = ChainDeal.builder()
                .botFirst(BigInteger.valueOf(0))
                .botSize(BigInteger.valueOf(1))
                .build();

        when(iexecHubService.getChainDealWithDetails(DEAL_ID)).thenReturn(Optional.of(chainDeal));
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);

        dealWatcherService.onDealEvent(new DealEvent(this, DEAL_ID, blockOfDeal));

        verify(configurationService).setLastSeenBlockWithDeal(blockOfDeal);
        verifyNoInteractions(taskService, applicationEventPublisher);
    }

    @Test
    void shouldNotUpdateLastSeenBlockWhenReceivingOldMissedDeal() {
        BigInteger from = BigInteger.valueOf(5);
        BigInteger blockOfDeal = BigInteger.valueOf(3);
        when(configurationService.getLastSeenBlockWithDeal()).thenReturn(from);

        dealWatcherService.onDealEvent(new DealEvent(this, DEAL_ID, blockOfDeal));

        verify(configurationService).getLastSeenBlockWithDeal();
        verify(configurationService, never()).setLastSeenBlockWithDeal(blockOfDeal);
    }
    // endregion

    // region replayDealEvent
    @Test
    void shouldReplayAllEventInRange() throws IOException {
        ReflectionTestUtils.setField(dealWatcherService, "maxLogsBatchSize", BigInteger.valueOf(100));
        final BigInteger blockOfDeal = BigInteger.valueOf(3);
        final EthLog.LogObject logObject = new EthLog.LogObject();
        logObject.setBlockNumber("0x3");
        logObject.setTopics(List.of(LogTopic.SCHEDULER_NOTICE_EVENT, Numeric.toHexStringNoPrefixZeroPadded(Numeric.toBigInt("0xe0cdddbd7956622874d4eb796e8ec600ad2ff14f"), 64)));
        final EthLog ethLog = new EthLog();
        ethLog.setResult(List.of(logObject));

        initMocks();
        when(web3jService.getLatestBlockNumber()).thenReturn(10L);
        when(configurationService.getFromReplay()).thenReturn(BigInteger.ZERO);
        when(web3jService.getWeb3j()).thenReturn(web3j);
        doReturn(ethLogRequest).when(web3j).ethGetLogs(any());
        when(ethLogRequest.send()).thenReturn(ethLog);

        dealWatcherService.replayDealEvent();

        verify(iexecHubService).getChainDealWithDetails(any());
        Counter lastBlockCounter = Metrics.globalRegistry.find(DealWatcherService.METRIC_DEALS_LAST_BLOCK).counter();
        Counter dealsCounter = Metrics.globalRegistry.find(DealWatcherService.METRIC_DEALS_COUNT).counter();
        assertThat(lastBlockCounter).isNotNull();
        assertThat(dealsCounter).isNotNull();
        assertThat(lastBlockCounter.count()).isEqualTo(blockOfDeal.doubleValue());
        assertThat(dealsCounter.count()).isEqualTo(1);
    }

    @Test
    void shouldNotReplayIfFromReplayEqualsLastSeenBlock() {
        when(web3jService.getLatestBlockNumber()).thenReturn(0L);
        when(configurationService.getFromReplay()).thenReturn(BigInteger.ZERO);
        dealWatcherService.replayDealEvent();
        verifyNoInteractions(iexecHubService);
    }
    // endregion

    // region shouldProcessDeal
    static Stream<Arguments> validDeals() {
        return Stream.of(
                Arguments.of(NO_TEE_TAG, BigInteger.ZERO),
                Arguments.of(NO_TEE_TAG, BigInteger.ONE),
                Arguments.of(NO_TEE_TAG, BigInteger.TEN),
                Arguments.of(TEE_SCONE_ONLY_TAG, BigInteger.ZERO),
                Arguments.of(TEE_SCONE_ONLY_TAG, BigInteger.ONE),
                Arguments.of(TEE_GRAMINE_ONLY_TAG, BigInteger.ZERO),
                Arguments.of(TEE_GRAMINE_ONLY_TAG, BigInteger.ONE)
        );
    }

    @ParameterizedTest
    @MethodSource("validDeals")
    void shouldProcessDeal(String tag, BigInteger trust) {
        final ChainDeal chainDeal = ChainDeal.builder()
                .tag(tag)
                .trust(trust)
                .build();

        when(iexecHubService.isBeforeContributionDeadline(chainDeal)).thenReturn(true);

        final boolean shouldProcess = dealWatcherService.shouldProcessDeal(chainDeal);
        assertThat(shouldProcess).isTrue();

        verify(iexecHubService).isBeforeContributionDeadline(chainDeal);
        verify(iexecHubService, never()).getChainDealContributionDeadline(chainDeal);
    }

    static Stream<Arguments> invalidDeals() {
        return Stream.of(
                Arguments.of(TEE_SCONE_ONLY_TAG, BigInteger.TEN),
                Arguments.of(TEE_GRAMINE_ONLY_TAG, BigInteger.TEN)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidDeals")
    void shouldNotProcessDealSinceWrongTagTrustCouple(String tag, BigInteger trust) {
        final ChainDeal chainDeal = ChainDeal.builder()
                .tag(tag)
                .trust(trust)
                .build();

        when(iexecHubService.isBeforeContributionDeadline(chainDeal)).thenReturn(true);

        final boolean shouldProcess = dealWatcherService.shouldProcessDeal(chainDeal);
        assertThat(shouldProcess).isFalse();

        verify(iexecHubService).isBeforeContributionDeadline(chainDeal);
        verify(iexecHubService, never()).getChainDealContributionDeadline(chainDeal);
    }

    @Test
    void shouldNotProcessDealsSinceAfterContributionDeadline() {
        final ChainDeal chainDeal = ChainDeal.builder()
                .tag(NO_TEE_TAG)
                .trust(BigInteger.ONE)
                .build();

        when(iexecHubService.isBeforeContributionDeadline(chainDeal)).thenReturn(false);

        final boolean shouldProcess = dealWatcherService.shouldProcessDeal(chainDeal);
        assertThat(shouldProcess).isFalse();

        verify(iexecHubService).isBeforeContributionDeadline(chainDeal);
        verify(iexecHubService).getChainDealContributionDeadline(chainDeal);
    }
    // endregion
}
