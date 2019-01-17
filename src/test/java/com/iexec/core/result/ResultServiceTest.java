package com.iexec.core.result;

import com.iexec.common.chain.ChainDeal;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.utils.BytesUtils;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.result.eip712.Eip712AuthenticationModel;
import com.iexec.core.result.eip712.Eip712Challenge;
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

    private String challenge;
    private String challengeString;
    private String challengeSignature;
    private Integer chainId;
    private String chainDealId;
    private String chainTaskId;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        challenge = "4XriVH0gk1cpVECMDKf5v0mzpPMurXMowHv_rggczNo";
        challengeString = "0xb2875bcbc305278f67df0b2385aee3a7b03595c8896442dd5a3e663ad123d9c0";
        challengeSignature = "0xafb1ac2685f383e253a91387ef87163c6cf90353b480ff99c0d8aeca10283d2211fadf0e46947b36b2e029a0473d762ecab921777a23502cd34f0cb1b0bec9ef1b";
        chainId = 17;
        chainDealId = "Oxdea1";
        chainTaskId = "0x1";
    }

    @Test
    public void isNotAuthorizedToGetResultSinceNoChallengeInMap() {
        when(eip712ChallengeService.containsEip712ChallengeString(challenge)).thenReturn(false);
        Eip712AuthenticationModel auth = Eip712AuthenticationModel.builder()
                .eip712Challenge(new Eip712Challenge("abcd", chainId))
                .eip712ChallengeSignature(challengeSignature)
                .walletAddress("0xa").build();
        assertThat(resultService.isAuthorizedToGetResult(chainTaskId, auth)).isFalse();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceChallengeSignatureIsWrong() {
        String requester = "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E";
        String beneficiary = "0xb";

        Eip712AuthenticationModel auth = Eip712AuthenticationModel.builder()
                .eip712Challenge(new Eip712Challenge(challenge, chainId))
                .eip712ChallengeSignature("0xafb1ac2685f383e253a91387ef87163c6cf90353b480ff99c0d8aeca10283d2211fadf0e46947b36b2e029a0473d762ecab921777a23502cd34f0cb1b0bec9dead")
                .walletAddress(requester).build();

        when(eip712ChallengeService.getEip712ChallengeString(auth.getEip712Challenge())).thenReturn(challengeString);
        when(eip712ChallengeService.containsEip712ChallengeString(challengeString)).thenReturn(true);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));

        assertThat(resultService.isAuthorizedToGetResult(chainTaskId, auth)).isFalse();
    }


    @Test
    public void isNotAuthorizedToGetResultSinceChallengeSignatureIsBadFormat() {
        String requester = "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E";
        String beneficiary = "0xb";
        String badChallengeSignature = "0xbad";

        Eip712AuthenticationModel auth = Eip712AuthenticationModel.builder()
                .eip712Challenge(new Eip712Challenge(challenge, chainId))
                .eip712ChallengeSignature(badChallengeSignature)
                .walletAddress(requester).build();

        when(eip712ChallengeService.getEip712ChallengeString(auth.getEip712Challenge())).thenReturn(badChallengeSignature);
        when(eip712ChallengeService.containsEip712ChallengeString(badChallengeSignature)).thenReturn(true);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));

        assertThat(resultService.isAuthorizedToGetResult(chainTaskId, auth)).isFalse();
    }



    @Test
    public void isNotAuthorizedToGetResultSinceChallengeSignatureIsBadFormat2() {
        String requester = "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E";
        String beneficiary = "0xb";
        String badChallengeSignature = "0x1b0b90d9f17a30d42492c8a2f98a24374600729a98d4e0b663a44ed48b589cab0e445eec300245e590150c7d88340d902c27e0d8673f3257cb8393f647d6c7FAKE";

        Eip712AuthenticationModel auth = Eip712AuthenticationModel.builder()
                .eip712Challenge(new Eip712Challenge(challenge, chainId))
                .eip712ChallengeSignature(badChallengeSignature)
                .walletAddress(requester).build();

        when(eip712ChallengeService.getEip712ChallengeString(auth.getEip712Challenge())).thenReturn(badChallengeSignature);
        when(eip712ChallengeService.containsEip712ChallengeString(badChallengeSignature)).thenReturn(true);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));

        assertThat(resultService.isAuthorizedToGetResult(chainTaskId, auth)).isFalse();
    }


    @Test
    public void isNotAuthorizedToGetResultSinceWalletAddressDifferentFromRequester() {
        String requester = "0xa";
        String beneficiary = BytesUtils.EMPTY_ADDRESS;

        Eip712AuthenticationModel auth = Eip712AuthenticationModel.builder()
                .eip712Challenge(new Eip712Challenge(challenge, chainId))
                .eip712ChallengeSignature(challengeSignature)
                .walletAddress("0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E").build();

        when(eip712ChallengeService.getEip712ChallengeString(auth.getEip712Challenge())).thenReturn(challengeString);
        when(eip712ChallengeService.containsEip712ChallengeString(challengeString)).thenReturn(true);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));

        assertThat(resultService.isAuthorizedToGetResult(chainTaskId, auth)).isFalse();
    }



    @Test
    public void isNotAuthorizedToGetResultSinceWalletAddressDifferentFromBeneficiary() {
        String requester = "0xa";
        String beneficiary = "0xb";

        Eip712AuthenticationModel auth = Eip712AuthenticationModel.builder()
                .eip712Challenge(new Eip712Challenge(challenge, chainId))
                .eip712ChallengeSignature(challengeSignature)
                .walletAddress("0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E").build();

        when(eip712ChallengeService.getEip712ChallengeString(auth.getEip712Challenge())).thenReturn(challengeString);
        when(eip712ChallengeService.containsEip712ChallengeString(challengeString)).thenReturn(true);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));

        assertThat(resultService.isAuthorizedToGetResult(chainTaskId, auth)).isFalse();
    }


    @Test
    public void isNotAuthorizedToGetResultSinceWalletAddressShouldBeBeneficiaryWhenSet() {
        String requester = "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E";
        String beneficiary = "0xb";

        Eip712AuthenticationModel auth = Eip712AuthenticationModel.builder()
                .eip712Challenge(new Eip712Challenge(challenge, chainId))
                .eip712ChallengeSignature(challengeSignature)
                .walletAddress("0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E").build();

        when(eip712ChallengeService.getEip712ChallengeString(auth.getEip712Challenge())).thenReturn(challengeString);
        when(eip712ChallengeService.containsEip712ChallengeString(challengeString)).thenReturn(true);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));

        assertThat(resultService.isAuthorizedToGetResult(chainTaskId, auth)).isFalse();
    }

    @Test
    public void isAuthorizedToGetResult() {
        String requester = "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E";
        String beneficiary = BytesUtils.EMPTY_ADDRESS;

        Eip712AuthenticationModel auth = Eip712AuthenticationModel.builder()
                .eip712Challenge(new Eip712Challenge(challenge, chainId))
                .eip712ChallengeSignature(challengeSignature)
                .walletAddress("0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E").build();

        when(eip712ChallengeService.getEip712ChallengeString(auth.getEip712Challenge())).thenReturn(challengeString);
        when(eip712ChallengeService.containsEip712ChallengeString(challengeString)).thenReturn(true);
        when(iexecHubService.getChainTask(chainTaskId)).thenReturn(Optional.of(ChainTask.builder().dealid(chainDealId).build()));
        when(iexecHubService.getChainDeal(chainDealId)).thenReturn(Optional.of(ChainDeal.builder().requester(requester).beneficiary(beneficiary).build()));

        assertThat(resultService.isAuthorizedToGetResult(chainTaskId, auth)).isTrue();
    }


}