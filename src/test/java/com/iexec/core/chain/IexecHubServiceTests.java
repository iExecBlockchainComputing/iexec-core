/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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

import com.iexec.commons.poco.chain.*;
import com.iexec.commons.poco.contract.generated.IexecHubContract;
import com.iexec.commons.poco.utils.BytesUtils;
import io.reactivex.Flowable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.protocol.core.methods.response.Log;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static com.iexec.commons.poco.utils.TestUtils.CHAIN_TASK_ID;
import static com.iexec.commons.poco.utils.TestUtils.WORKER_ADDRESS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IexecHubServiceTests {

    private static final String TRANSACTION_HASH = "transactionHash";

    @Mock
    private CredentialsService credentialsService;

    @Mock
    private Web3jService web3jService;

    @Mock
    private ChainConfig chainConfig;

    private IexecHubService iexecHubService;

    @BeforeEach
    void init() throws Exception {
        MockitoAnnotations.openMocks(this);

        final Credentials credentials = Credentials.create(Keys.createEcKeyPair());

        when(credentialsService.getCredentials()).thenReturn(credentials);
        when(web3jService.hasEnoughGas(any())).thenReturn(true);
        when(chainConfig.getHubAddress()).thenReturn("0x748e091bf16048cb5103E0E10F9D5a8b7fBDd860");

        iexecHubService = spy(new IexecHubService(credentialsService, web3jService, chainConfig));
    }

    // region isTaskInCompletedStatusOnChain
    @Test
    void shouldTaskBeInCompletedStatusOnChain() {
        final ChainTask task = ChainTask.builder().status(ChainTaskStatus.COMPLETED).build();
        doReturn(Optional.of(task)).when(iexecHubService).getChainTask(CHAIN_TASK_ID);

        assertThat(iexecHubService.isTaskInCompletedStatusOnChain(CHAIN_TASK_ID)).isTrue();
    }

    @Test
    void shouldTaskNotBeInCompletedStatusOnChain() {
        final ChainTask task = ChainTask.builder().status(ChainTaskStatus.REVEALING).build();
        doReturn(Optional.of(task)).when(iexecHubService).getChainTask(CHAIN_TASK_ID);

        assertThat(iexecHubService.isTaskInCompletedStatusOnChain(CHAIN_TASK_ID)).isFalse();
    }
    // endregion

    // region canFinalize
    @Test
    void canNotFinalizeWhenChainTaskNotFound() {
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        assertThat(iexecHubService.canFinalize(CHAIN_TASK_ID)).isFalse();
    }

    @Test
    void canNotFinalizeWhenNotRevealing() {
        final ChainTask chainTask = ChainTask.builder()
                .status(ChainTaskStatus.ACTIVE)
                .finalDeadline(Instant.now().plus(10L, ChronoUnit.SECONDS).toEpochMilli())
                .build();
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));
        assertThat(iexecHubService.canFinalize(CHAIN_TASK_ID)).isFalse();
    }

    @Test
    void canNotFinalizeWhenFinalDeadlineReached() {
        final ChainTask chainTask = ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .finalDeadline(Instant.now().minus(10L, ChronoUnit.MILLIS).toEpochMilli())
                .build();
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));
        assertThat(iexecHubService.canFinalize(CHAIN_TASK_ID)).isFalse();
    }
    // endregion

    // region isContributed
    @ParameterizedTest
    @EnumSource(value = ChainContributionStatus.class, mode = EnumSource.Mode.INCLUDE, names = {"CONTRIBUTED", "REVEALED"})
    void shouldBeContributed(ChainContributionStatus status) {
        final ChainContribution chainContribution = ChainContribution.builder().status(status).build();
        when(iexecHubService.getChainContribution(anyString(), anyString())).thenReturn(Optional.of(chainContribution));
        assertThat(iexecHubService.isContributed(CHAIN_TASK_ID, WORKER_ADDRESS)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ChainContributionStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"CONTRIBUTED", "REVEALED"})
    void shouldNotBeContributed(ChainContributionStatus status) {
        final ChainContribution chainContribution = ChainContribution.builder().status(status).build();
        when(iexecHubService.getChainContribution(anyString(), anyString())).thenReturn(Optional.of(chainContribution));
        assertThat(iexecHubService.isContributed(CHAIN_TASK_ID, WORKER_ADDRESS)).isFalse();
    }
    // endregion

    // region isRevealed
    @ParameterizedTest
    @EnumSource(value = ChainContributionStatus.class, mode = EnumSource.Mode.INCLUDE, names = {"REVEALED"})
    void shouldBeRevealed(ChainContributionStatus status) {
        final ChainContribution chainContribution = ChainContribution.builder().status(status).build();
        when(iexecHubService.getChainContribution(anyString(), anyString())).thenReturn(Optional.of(chainContribution));
        assertThat(iexecHubService.isRevealed(CHAIN_TASK_ID, WORKER_ADDRESS)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = ChainContributionStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"REVEALED"})
    void shouldNotBeRevealed(ChainContributionStatus status) {
        final ChainContribution chainContribution = ChainContribution.builder().status(status).build();
        when(iexecHubService.getChainContribution(anyString(), anyString())).thenReturn(Optional.of(chainContribution));
        assertThat(iexecHubService.isRevealed(CHAIN_TASK_ID, WORKER_ADDRESS)).isFalse();
    }
    // endregion

    // region get event blocks
    @Test
    void shouldGetContributionBlock() {
        final int fromBlock = 0;
        final long latestBlock = 1;
        when(web3jService.getLatestBlockNumber()).thenReturn(latestBlock);

        final IexecHubContract hubContract = mock(IexecHubContract.class);
        ReflectionTestUtils.setField(iexecHubService, "iexecHubContract", hubContract);

        final IexecHubContract.TaskContributeEventResponse taskContributeEventResponse = getTaskContributeEventResponse(latestBlock);
        when(hubContract.taskContributeEventFlowable(any()))
                .thenReturn(Flowable.fromArray(taskContributeEventResponse));

        final ChainReceipt chainReceipt = iexecHubService.getContributionBlock(CHAIN_TASK_ID, WORKER_ADDRESS, fromBlock);

        assertThat(chainReceipt)
                .isEqualTo(ChainReceipt.builder()
                        .blockNumber(latestBlock)
                        .txHash(TRANSACTION_HASH)
                        .build());
    }

    private static IexecHubContract.TaskContributeEventResponse getTaskContributeEventResponse(long latestBlock) {
        final IexecHubContract.TaskContributeEventResponse taskContributeEventResponse =
                new IexecHubContract.TaskContributeEventResponse();
        taskContributeEventResponse.taskid = BytesUtils.stringToBytes(CHAIN_TASK_ID);
        taskContributeEventResponse.worker = WORKER_ADDRESS;
        taskContributeEventResponse.log = new Log();
        taskContributeEventResponse.log.setBlockNumber(String.valueOf(latestBlock));
        taskContributeEventResponse.log.setTransactionHash(TRANSACTION_HASH);
        return taskContributeEventResponse;
    }

    @Test
    void shouldGetConsensusBlock() {
        final int fromBlock = 0;
        final long latestBlock = 1;
        when(web3jService.getLatestBlockNumber()).thenReturn(latestBlock);

        final IexecHubContract hubContract = mock(IexecHubContract.class);
        ReflectionTestUtils.setField(iexecHubService, "iexecHubContract", hubContract);

        final IexecHubContract.TaskConsensusEventResponse taskConsensusEventResponse = getTaskConsensusEventResponse(latestBlock);
        when(hubContract.taskConsensusEventFlowable(any()))
                .thenReturn(Flowable.fromArray(taskConsensusEventResponse));

        final ChainReceipt chainReceipt = iexecHubService.getConsensusBlock(CHAIN_TASK_ID, fromBlock);

        assertThat(chainReceipt)
                .isEqualTo(ChainReceipt.builder()
                        .blockNumber(latestBlock)
                        .txHash(TRANSACTION_HASH)
                        .build());
    }

    private static IexecHubContract.TaskConsensusEventResponse getTaskConsensusEventResponse(long latestBlock) {
        final IexecHubContract.TaskConsensusEventResponse taskContributeEventResponse =
                new IexecHubContract.TaskConsensusEventResponse();
        taskContributeEventResponse.taskid = BytesUtils.stringToBytes(CHAIN_TASK_ID);
        taskContributeEventResponse.log = new Log();
        taskContributeEventResponse.log.setBlockNumber(String.valueOf(latestBlock));
        taskContributeEventResponse.log.setTransactionHash(TRANSACTION_HASH);
        return taskContributeEventResponse;
    }

    @Test
    void shouldGetRevealBlock() {
        final int fromBlock = 0;
        final long latestBlock = 1;
        when(web3jService.getLatestBlockNumber()).thenReturn(latestBlock);

        final IexecHubContract hubContract = mock(IexecHubContract.class);
        ReflectionTestUtils.setField(iexecHubService, "iexecHubContract", hubContract);

        final IexecHubContract.TaskRevealEventResponse taskRevealEventResponse = getTaskRevealEventResponse(latestBlock);
        when(hubContract.taskRevealEventFlowable(any()))
                .thenReturn(Flowable.fromArray(taskRevealEventResponse));

        final ChainReceipt chainReceipt = iexecHubService.getRevealBlock(CHAIN_TASK_ID, WORKER_ADDRESS, fromBlock);

        assertThat(chainReceipt)
                .isEqualTo(ChainReceipt.builder()
                        .blockNumber(latestBlock)
                        .txHash(TRANSACTION_HASH)
                        .build());
    }

    private static IexecHubContract.TaskRevealEventResponse getTaskRevealEventResponse(long latestBlock) {
        final IexecHubContract.TaskRevealEventResponse taskContributeEventResponse =
                new IexecHubContract.TaskRevealEventResponse();
        taskContributeEventResponse.taskid = BytesUtils.stringToBytes(CHAIN_TASK_ID);
        taskContributeEventResponse.worker = WORKER_ADDRESS;
        taskContributeEventResponse.log = new Log();
        taskContributeEventResponse.log.setBlockNumber(String.valueOf(latestBlock));
        taskContributeEventResponse.log.setTransactionHash(TRANSACTION_HASH);
        return taskContributeEventResponse;
    }

    @Test
    void shouldGetFinalizeBlock() {
        final int fromBlock = 0;
        final long latestBlock = 1;
        when(web3jService.getLatestBlockNumber()).thenReturn(latestBlock);

        final IexecHubContract hubContract = mock(IexecHubContract.class);
        ReflectionTestUtils.setField(iexecHubService, "iexecHubContract", hubContract);

        final IexecHubContract.TaskFinalizeEventResponse taskFinalizeEventResponse = getTaskFinalizeEventResponse(latestBlock);
        when(hubContract.taskFinalizeEventFlowable(any()))
                .thenReturn(Flowable.fromArray(taskFinalizeEventResponse));

        final ChainReceipt chainReceipt = iexecHubService.getFinalizeBlock(CHAIN_TASK_ID, fromBlock);

        assertThat(chainReceipt)
                .isEqualTo(ChainReceipt.builder()
                        .blockNumber(latestBlock)
                        .txHash(TRANSACTION_HASH)
                        .build());
    }

    private static IexecHubContract.TaskFinalizeEventResponse getTaskFinalizeEventResponse(long latestBlock) {
        final IexecHubContract.TaskFinalizeEventResponse taskContributeEventResponse =
                new IexecHubContract.TaskFinalizeEventResponse();
        taskContributeEventResponse.taskid = BytesUtils.stringToBytes(CHAIN_TASK_ID);
        taskContributeEventResponse.log = new Log();
        taskContributeEventResponse.log.setBlockNumber(String.valueOf(latestBlock));
        taskContributeEventResponse.log.setTransactionHash(TRANSACTION_HASH);
        return taskContributeEventResponse;
    }

    static Stream<BiFunction<IexecHubService, Long, ChainReceipt>> eventBlockGetters() {
        return Stream.of(
                (iexecHubService, fromBlock) -> iexecHubService.getContributionBlock(CHAIN_TASK_ID, WORKER_ADDRESS, fromBlock),
                (iexecHubService, fromBlock) -> iexecHubService.getConsensusBlock(CHAIN_TASK_ID, fromBlock),
                (iexecHubService, fromBlock) -> iexecHubService.getRevealBlock(CHAIN_TASK_ID, WORKER_ADDRESS, fromBlock),
                (iexecHubService, fromBlock) -> iexecHubService.getFinalizeBlock(CHAIN_TASK_ID, fromBlock)
        );
    }

    @ParameterizedTest
    @MethodSource("eventBlockGetters")
    void shouldNotGetEventBlockWhenFromBlockInFuture(BiFunction<IexecHubService, Long, ChainReceipt> eventBlockGetter) {
        final long fromBlock = 2;
        final long latestBlock = 1;
        when(web3jService.getLatestBlockNumber()).thenReturn(latestBlock);

        final ChainReceipt chainReceipt = eventBlockGetter.apply(iexecHubService, fromBlock);
        assertThat(chainReceipt)
                .isEqualTo(ChainReceipt.builder().build());
    }
    // endregion
}
