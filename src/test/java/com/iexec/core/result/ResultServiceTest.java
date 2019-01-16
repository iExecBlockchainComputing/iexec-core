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
    private Eip712ChallengeService eip712ChallengeService;

    @Mock
    private IexecHubService iexecHubService;

    @InjectMocks
    private ResultService resultService;

    private String challengeSignature;
    private String challenge;
    private Integer chainId;
    private String chainDealId;
    private String chainTaskId;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        challenge = "0xb7a099c5998bb07a9e30ad6faaa79ddfc70c3475134957de7343ddb13f4c382a";
        challengeSignature = "0x1b0b90d9f17a30d42492c8a2f98a24374600729a98d4e0b663a44ed48b589cab0e445eec300245e590150c7d88340d902c27e0d8673f3257cb8393f647d6c75c1b";
        chainId = 17;
        chainDealId = "Oxdea1";
        chainTaskId = "0x1";
    }

    @Test
    public void isNotAuthorizedToGetResultSinceNoChallengeInMap() {
        when(eip712ChallengeService.containsEip712ChallengeString(challenge)).thenReturn(false);
        assertThat(resultService.isAuthorizedToGetResult(chainId, chainTaskId, challenge, challengeSignature, "0xa")).isFalse();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceChallengeSignatureIsWrong() {
        String requester = "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E";
        String beneficiary = "0xb";
        when(eip712ChallengeService.containsEip712ChallengeString(challenge)).thenReturn(true);
        when(iexecHubService.getChainTask("0x1")).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));
        assertThat(resultService.isAuthorizedToGetResult(chainId, chainTaskId, challenge,
                "0x1b0b90d9f17a30d42492c8a2f98a24374600729a98d4e0b663a44ed48b589cab0e445eec300245e590150c7d88340d902c27e0d8673f3257cb8393f647d6c7dead"
                , "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E")).isFalse();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceChallengeSignatureIsBadFormat() {
        String requester = "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E";
        String beneficiary = "0xb";
        when(eip712ChallengeService.containsEip712ChallengeString(challenge)).thenReturn(true);
        when(iexecHubService.getChainTask("0x1")).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));
        assertThat(resultService.isAuthorizedToGetResult(chainId, chainTaskId, challenge,
                "0xbad"
                , "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E")).isFalse();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceChallengeSignatureIsBadFormat2() {
        String requester = "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E";
        String beneficiary = "0xb";
        when(eip712ChallengeService.containsEip712ChallengeString(challenge)).thenReturn(true);
        when(iexecHubService.getChainTask("0x1")).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));
        assertThat(resultService.isAuthorizedToGetResult(chainId, chainTaskId, challenge,
                "1b0b90d9f17a30d42492c8a2f98a24374600729a98d4e0b663a44ed48b589cab0e445eec300245e590150c7d88340d902c27e0d8673f3257cb8393f647d6c7FAKE"
                , "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E")).isFalse();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceWalletAddressDifferentFromRequester() {
        String requester = "0xa";
        String beneficiary = BytesUtils.EMPTY_ADDRESS;
        when(eip712ChallengeService.containsEip712ChallengeString(challenge)).thenReturn(true);
        when(iexecHubService.getChainTask("0x1")).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));
        assertThat(resultService.isAuthorizedToGetResult(chainId, chainTaskId, challenge, challengeSignature, "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E")).isFalse();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceWalletAddressDifferentFromBeneficiary() {
        String requester = "0xa";
        String beneficiary = "0xb";
        when(eip712ChallengeService.containsEip712ChallengeString(challenge)).thenReturn(true);
        when(iexecHubService.getChainTask("0x1")).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));
        assertThat(resultService.isAuthorizedToGetResult(chainId, chainTaskId, challenge, challengeSignature, "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E")).isFalse();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceWalletAddressShouldBeBeneficiaryWhenSet() {
        String requester = "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E";
        String beneficiary = "0xb";
        when(eip712ChallengeService.containsEip712ChallengeString(challenge)).thenReturn(true);
        when(iexecHubService.getChainTask("0x1")).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));
        assertThat(resultService.isAuthorizedToGetResult(chainId, chainTaskId, challenge, challengeSignature, "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E")).isFalse();
    }

    @Test
    public void isAuthorizedToGetResult() {
        String requester = "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E";
        String beneficiary = BytesUtils.EMPTY_ADDRESS;
        when(eip712ChallengeService.containsEip712ChallengeString(challenge)).thenReturn(true);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));
        assertThat(resultService.isAuthorizedToGetResult(chainId, chainTaskId, challenge, challengeSignature, "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E")).isTrue();
    }
}