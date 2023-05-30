package com.iexec.core.chain;

import com.iexec.commons.poco.chain.ChainReceipt;
import com.iexec.commons.poco.chain.ChainTask;
import com.iexec.commons.poco.chain.ChainTaskStatus;
import com.iexec.commons.poco.contract.generated.IexecHubContract;
import com.iexec.commons.poco.utils.BytesUtils;
import io.reactivex.Flowable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.tx.TransactionManager;

import java.math.BigInteger;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static com.iexec.commons.poco.utils.TestUtils.CHAIN_TASK_ID;
import static com.iexec.commons.poco.utils.TestUtils.WORKER_ADDRESS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IexecHubServiceTests {

    private  static final String TRANSACTION_HASH = "transactionHash";

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

        try (MockedStatic<IexecHubContract> iexecHubContract = Mockito.mockStatic(IexecHubContract.class)) {
            final IexecHubContract mockIexecContract = mock(IexecHubContract.class);
            final RemoteFunctionCall<BigInteger> mockRemoteFunctionCall = mock(RemoteFunctionCall.class);
            iexecHubContract.when(() -> IexecHubContract.load(any(), any(), (TransactionManager) any(), any()))
                    .thenReturn(mockIexecContract);
            when(mockIexecContract.contribution_deadline_ratio()).thenReturn(mockRemoteFunctionCall);
            when(mockRemoteFunctionCall.send()).thenReturn(BigInteger.ONE);
            iexecHubService = spy(new IexecHubService(credentialsService, web3jService, chainConfig));
        }
    }


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

    // region Get event blocks
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
