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

import com.iexec.commons.poco.chain.*;
import io.reactivex.Flowable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthLog;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IexecHubServiceTests {

    private static final String CHAIN_TASK_ID = "0x1111111111111111111111111111111111111111111111111111111111111111";
    private static final String WORKER_ADDRESS = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private static final String TRANSACTION_HASH = "transactionHash";

    @Mock
    private SignerService signerService;

    @Mock
    private Web3jService web3jService;

    @Mock
    private ChainConfig chainConfig;

    @Mock
    private Web3j web3j;
    @Mock
    private Request<?, EthLog> logRequest;

    private IexecHubService iexecHubService;

    @BeforeEach
    void init() throws Exception {
        final Credentials credentials = Credentials.create(Keys.createEcKeyPair());

        when(signerService.getCredentials()).thenReturn(credentials);
        when(web3jService.hasEnoughGas(any())).thenReturn(true);
        when(chainConfig.getHubAddress()).thenReturn("0x748e091bf16048cb5103E0E10F9D5a8b7fBDd860");

        iexecHubService = spy(new IexecHubService(signerService, web3jService, chainConfig));
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
    private void mockWeb3jCall(final long latestBlock) {
        when(web3jService.getLatestBlockNumber()).thenReturn(latestBlock);
        when(web3jService.getWeb3j()).thenReturn(web3j);
        doReturn(logRequest).when(web3j).ethGetLogs(any());
    }

    @Test
    void shouldGetContributionBlock() {
        final int fromBlock = 0;
        final long latestBlock = 1;
        final EthLog ethLog = new EthLog();
        final EthLog.LogObject logObject = new EthLog.LogObject();
        logObject.setBlockNumber(String.valueOf(latestBlock));
        logObject.setTransactionHash(TRANSACTION_HASH);
        ethLog.setResult(List.of(logObject));

        mockWeb3jCall(latestBlock);
        when(logRequest.flowable()).thenReturn(Flowable.fromArray(ethLog));

        final ChainReceipt chainReceipt = iexecHubService.getContributionBlock(CHAIN_TASK_ID, WORKER_ADDRESS, fromBlock);

        assertThat(chainReceipt)
                .isEqualTo(ChainReceipt.builder()
                        .blockNumber(latestBlock)
                        .txHash(TRANSACTION_HASH)
                        .build());
    }

    @Test
    void shouldGetConsensusBlock() {
        final int fromBlock = 0;
        final long latestBlock = 1;
        final EthLog ethLog = new EthLog();
        final EthLog.LogObject logObject = new EthLog.LogObject();
        logObject.setBlockNumber(String.valueOf(latestBlock));
        logObject.setTransactionHash(TRANSACTION_HASH);
        ethLog.setResult(List.of(logObject));
        mockWeb3jCall(latestBlock);
        when(logRequest.flowable()).thenReturn(Flowable.fromArray(ethLog));

        final ChainReceipt chainReceipt = iexecHubService.getConsensusBlock(CHAIN_TASK_ID, fromBlock);

        assertThat(chainReceipt)
                .isEqualTo(ChainReceipt.builder()
                        .blockNumber(latestBlock)
                        .txHash(TRANSACTION_HASH)
                        .build());
    }

    @Test
    void shouldGetRevealBlock() {
        final int fromBlock = 0;
        final long latestBlock = 1;
        final EthLog ethLog = new EthLog();
        final EthLog.LogObject logObject = new EthLog.LogObject();
        logObject.setBlockNumber(String.valueOf(latestBlock));
        logObject.setTransactionHash(TRANSACTION_HASH);
        ethLog.setResult(List.of(logObject));
        mockWeb3jCall(latestBlock);
        when(logRequest.flowable()).thenReturn(Flowable.fromArray(ethLog));

        final ChainReceipt chainReceipt = iexecHubService.getRevealBlock(CHAIN_TASK_ID, WORKER_ADDRESS, fromBlock);

        assertThat(chainReceipt)
                .isEqualTo(ChainReceipt.builder()
                        .blockNumber(latestBlock)
                        .txHash(TRANSACTION_HASH)
                        .build());
    }

    @Test
    void shouldGetFinalizeBlock() {
        final int fromBlock = 0;
        final long latestBlock = 1;
        final EthLog ethLog = new EthLog();
        final EthLog.LogObject logObject = new EthLog.LogObject();
        logObject.setBlockNumber(String.valueOf(latestBlock));
        logObject.setTransactionHash(TRANSACTION_HASH);
        ethLog.setResult(List.of(logObject));
        mockWeb3jCall(latestBlock);
        when(logRequest.flowable()).thenReturn(Flowable.fromArray(ethLog));

        final ChainReceipt chainReceipt = iexecHubService.getFinalizeBlock(CHAIN_TASK_ID, fromBlock);

        assertThat(chainReceipt)
                .isEqualTo(ChainReceipt.builder()
                        .blockNumber(latestBlock)
                        .txHash(TRANSACTION_HASH)
                        .build());
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
