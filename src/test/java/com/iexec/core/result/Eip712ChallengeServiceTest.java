package com.iexec.core.result;

import com.iexec.common.chain.ChainDeal;
import com.iexec.common.chain.ChainTask;
import com.iexec.core.result.eip712.Eip712Challenge;
import org.assertj.core.api.Java6Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class Eip712ChallengeServiceTest {

    @InjectMocks
    private Eip712ChallengeService eip712ChallengeService;

    private Eip712Challenge eip712Challenge;
    private String challenge;
    private String challengeSignature;
    private String address;


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        eip712Challenge = new Eip712Challenge("0x10ff103511e3e233033628dbd641136d4670c16c33a4ce11950ab316ef18bce9", 17);
        challenge = "0xb7a099c5998bb07a9e30ad6faaa79ddfc70c3475134957de7343ddb13f4c382a";
        challengeSignature = "0x1b0b90d9f17a30d42492c8a2f98a24374600729a98d4e0b663a44ed48b589cab0e445eec300245e590150c7d88340d902c27e0d8673f3257cb8393f647d6c75c1b";
        address="0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E";
    }

    @Test
    public void shouldGetCorrectDomainSeparator() {
        assertThat(eip712ChallengeService.getDomainSeparator(eip712Challenge))
                .isEqualTo("0x73b2ffe0e9f80f155eba2ca6ad915b2bd92e0d89c354112dc63b6dd70c30f51e");
    }

    @Test
    public void shouldGetCorrectMessageHash() {
        assertThat(eip712ChallengeService.getMessageHash(eip712Challenge))
                .isEqualTo("0x2ff97da3e19fd11436479ffcec54baa5501c439d8a65f01dec5a217f7bf4bc70");
    }

    @Test
    public void shouldGetCorrectChallengeHashToSign() {
        assertThat(eip712ChallengeService.getEip712ChallengeString(eip712Challenge))
                .isEqualTo("0x3bb958b947bc47479a7ee767d74a45146e41ac703d989d72f2b9c8f1aaf00a13");
    }

    @Test
    public void isNotAuthorizedToGetResultSinceNoChallengeInMap() {
        when(eip712ChallengeService.containsEip712ChallengeString(challenge)).thenReturn(false);
        Java6Assertions.assertThat(eip712ChallengeService.isAuthorizationValid(challenge, challengeSignature, "0xa")).isFalse();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceChallengeSignatureIsWrong() {
        String requester = "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E";
        String beneficiary = "0xb";
        when(eip712ChallengeService.containsEip712ChallengeString(challenge)).thenReturn(true);
        Java6Assertions.assertThat(eip712ChallengeService.isAuthorizationValid(challenge,
                "0x1b0b90d9f17a30d42492c8a2f98a24374600729a98d4e0b663a44ed48b589cab0e445eec300245e590150c7d88340d902c27e0d8673f3257cb8393f647d6c7dead"
                , address)).isFalse();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceChallengeSignatureIsBadFormat() {
        String requester = "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E";
        String beneficiary = "0xb";
        when(eip712ChallengeService.containsEip712ChallengeString(challenge)).thenReturn(true);
        Java6Assertions.assertThat(eip712ChallengeService.isAuthorizationValid(challenge,
                "0xbad"
                , address)).isFalse();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceChallengeSignatureIsBadFormat2() {
        String requester = "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E";
        String beneficiary = "0xb";
        when(eip712ChallengeService.containsEip712ChallengeString(challenge)).thenReturn(true);
        Java6Assertions.assertThat(eip712ChallengeService.isAuthorizationValid(challenge,
                "1b0b90d9f17a30d42492c8a2f98a24374600729a98d4e0b663a44ed48b589cab0e445eec300245e590150c7d88340d902c27e0d8673f3257cb8393f647d6c7FAKE"
                , "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E")).isFalse();
    }

}