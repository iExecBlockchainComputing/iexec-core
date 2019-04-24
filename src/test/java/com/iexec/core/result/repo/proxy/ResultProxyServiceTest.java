package com.iexec.core.result.repo.proxy;

import com.iexec.common.chain.ChainDeal;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.utils.BytesUtils;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.result.repo.ipfs.IpfsResultService;
import com.iexec.core.result.repo.mongo.MongoResultService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

public class ResultProxyServiceTest {

    @Mock
    private IexecHubService iexecHubService;

    @Mock
    private MongoResultService mongoResultService;

    @Mock
    private IpfsResultService ipfsResultService;


    @InjectMocks
    private ResultProxyService resultProxyService;

    private Integer chainId;
    private String chainDealId;
    private String chainTaskId;
    private String walletAddress;
    private byte[] zip;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        chainId = 17;
        chainDealId = "Oxdea1";
        chainTaskId = "0x1";
        walletAddress = "0x123abc";
        zip = new byte[10];
    }

    @Test
    public void isNotAbleToUploadSinceResultAlreadyExistsWithMongo() {
        when(iexecHubService.isPublicResult(chainTaskId, 0)).thenReturn(false);
        when(mongoResultService.doesResultExist(chainTaskId)).thenReturn(true);

        assertThat(resultProxyService.canUploadResult(chainTaskId, walletAddress, zip)).isFalse();
    }

    @Test
    public void isNotAbleToUploadSinceResultAlreadyExistsWithIpfs() {
        when(iexecHubService.isPublicResult(chainTaskId, 0)).thenReturn(true);
        when(ipfsResultService.doesResultExist(chainTaskId)).thenReturn(true);

        assertThat(resultProxyService.canUploadResult(chainTaskId, walletAddress, zip)).isFalse();
    }

    @Test
    public void isNotAbleToUploadSinceChainStatusIsNotRevealedWithMongo() {
        when(iexecHubService.isPublicResult(chainTaskId, 0)).thenReturn(false);
        when(mongoResultService.doesResultExist(chainTaskId)).thenReturn(true);
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(false);

        assertThat(resultProxyService.canUploadResult(chainTaskId, walletAddress, zip)).isFalse();
    }

    @Test
    public void isNotAbleToUploadSinceChainStatusIsNotRevealedWithIpfs() {
        when(iexecHubService.isPublicResult(chainTaskId, 0)).thenReturn(true);
        when(ipfsResultService.doesResultExist(chainTaskId)).thenReturn(true);
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(false);

        assertThat(resultProxyService.canUploadResult(chainTaskId, walletAddress, zip)).isFalse();
    }

    @Test
    public void isAbleToUploadWithMongo() {
        when(iexecHubService.isPublicResult(chainTaskId, 0)).thenReturn(false);
        when(mongoResultService.doesResultExist(chainTaskId)).thenReturn(false);
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(true);

        assertThat(resultProxyService.canUploadResult(chainTaskId, walletAddress, zip)).isTrue();
    }

    //@Test
    public void isAbleToUploadWithIpfs() {
        when(iexecHubService.isPublicResult(chainTaskId, 0)).thenReturn(true);
        when(ipfsResultService.doesResultExist(chainTaskId)).thenReturn(false);
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(true);

        assertThat(resultProxyService.canUploadResult(chainTaskId, walletAddress, zip)).isTrue();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceWalletAddressDifferentFromRequester() {
        String requester = "0xa";
        String beneficiary = BytesUtils.EMPTY_ADDRESS;
        when(iexecHubService.getChainTask("0x1")).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));
        assertThat(resultProxyService.isOwnerOfResult(chainId, chainTaskId, "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E")).isFalse();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceCannotGetChainTask() {
        when(iexecHubService.getChainTask("0x1")).thenReturn(Optional.empty());

        assertThat(resultProxyService.isOwnerOfResult(chainId, chainTaskId, "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E")).isFalse();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceCannotGetChainDeal() {
        when(iexecHubService.getChainTask("0x1")).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.empty());

        assertThat(resultProxyService.isOwnerOfResult(chainId, chainTaskId, "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E")).isFalse();
    }

    @Test
    public void isNotOwnerOfResultSinceWalletAddressDifferentFromBeneficiary() {
        String beneficiary = "0xb";
        when(iexecHubService.getChainTask("0x1")).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().beneficiary(beneficiary).build()));
        assertThat(resultProxyService.isOwnerOfResult(chainId, chainTaskId, "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E")).isFalse();
    }

    @Test
    public void isNotOwnerOfResultSinceWalletAddressShouldBeBeneficiary() {
        String beneficiary = "0xb";
        when(iexecHubService.getChainTask("0x1")).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().beneficiary(beneficiary).build()));
        assertThat(resultProxyService.isOwnerOfResult(chainId, chainTaskId,"0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E")).isFalse();
    }

    @Test
    public void isOwnerOfResult() {
        String beneficiary = "0xabcd1339ec7e762e639f4887e2bfe5ee8023e23e";
        when(iexecHubService.getTaskBeneficiary(chainTaskId, chainId)).thenReturn(Optional.of(beneficiary));

        assertThat(resultProxyService.isOwnerOfResult(chainId, chainTaskId, "0xabcd1339ec7e762e639f4887e2bfe5ee8023e23e")).isTrue();
    }

    @Test
    public void isPublicResult() {
        when(iexecHubService.isPublicResult(chainTaskId, 0)).thenReturn(true);
        assertThat(resultProxyService.isPublicResult(chainTaskId)).isTrue();
    }

    @Test
    public void isNotPublicResult() {
        String beneficiary = "0xb";
        when(iexecHubService.getTaskBeneficiary(chainTaskId, chainId)).thenReturn(Optional.of(beneficiary));
        assertThat(resultProxyService.isPublicResult(chainTaskId)).isFalse();
    }
}