package com.iexec.core.chain;

import com.iexec.commons.poco.chain.ChainTask;
import com.iexec.commons.poco.chain.ChainTaskStatus;
import com.iexec.commons.poco.contract.generated.IexecHubContract;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.tx.TransactionManager;

import java.math.BigInteger;
import java.util.Optional;

import static com.iexec.commons.poco.utils.TestUtils.CHAIN_TASK_ID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class IexecHubServiceTests {

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
}
