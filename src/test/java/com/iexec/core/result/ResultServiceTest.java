package com.iexec.core.result;

import com.iexec.common.chain.ChainDeal;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.utils.BytesUtils;
import com.iexec.core.chain.IexecHubService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.when;

public class ResultServiceTest {

    @Mock
    private IexecHubService iexecHubService;

    @InjectMocks
    private ResultService resultService;

    private Integer chainId;
    private String chainDealId;
    private String chainTaskId;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        chainId = 17;
        chainDealId = "Oxdea1";
        chainTaskId = "0x1";
    }

    @Test
    public void isNotAuthorizedToGetResultSinceWalletAddressDifferentFromRequester() {
        String requester = "0xa";
        String beneficiary = BytesUtils.EMPTY_ADDRESS;
        when(iexecHubService.getChainTask("0x1")).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));
        assertThat(resultService.canGetResult(chainId, chainTaskId, "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E")).isFalse();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceWalletAddressDifferentFromBeneficiary() {
        String requester = "0xa";
        String beneficiary = "0xb";
        when(iexecHubService.getChainTask("0x1")).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));
        assertThat(resultService.canGetResult(chainId, chainTaskId, "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E")).isFalse();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceWalletAddressShouldBeBeneficiaryWhenSet() {
        String requester = "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E";
        String beneficiary = "0xb";
        when(iexecHubService.getChainTask("0x1")).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));
        assertThat(resultService.canGetResult(chainId, chainTaskId,"0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E")).isFalse();
    }

    @Test
    public void isAuthorizedToGetResult() {
        String requester = "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E";
        String beneficiary = BytesUtils.EMPTY_ADDRESS;
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));
        assertThat(resultService.canGetResult(chainId, chainTaskId, "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E")).isTrue();
    }
}