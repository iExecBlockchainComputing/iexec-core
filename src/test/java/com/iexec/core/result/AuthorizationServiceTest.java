package com.iexec.core.result;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.when;

public class AuthorizationServiceTest {

    @Mock
    private Eip712ChallengeService eip712ChallengeService;

    @InjectMocks
    private AuthorizationService authorizationService;

    private String challenge;
    private String challengeSignature;
    private String address;


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        challenge = "0xb7a099c5998bb07a9e30ad6faaa79ddfc70c3475134957de7343ddb13f4c382a";
        challengeSignature = "0x1b0b90d9f17a30d42492c8a2f98a24374600729a98d4e0b663a44ed48b589cab0e445eec300245e590150c7d88340d902c27e0d8673f3257cb8393f647d6c75c1b";
        address = "0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E";
    }

    @Test
    public void isNotAuthorizedToGetResultSinceNullAuthorization() {
        assertThat(authorizationService.isAuthorizationValid(null)).isFalse();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceNoChallengeInMap() {
        when(eip712ChallengeService.containsEip712ChallengeString(challenge)).thenReturn(false);
        Authorization authorization = Authorization.builder()
                .challenge(challenge)
                .challengeSignature(challengeSignature)
                .walletAddress("0xa")
                .build();
        assertThat(authorizationService.isAuthorizationValid(authorization)).isFalse();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceChallengeSignatureIsWrong() {
        when(eip712ChallengeService.containsEip712ChallengeString(challenge)).thenReturn(true);
        Authorization authorization = Authorization.builder()
                .challenge(challenge)
                .challengeSignature("0x1b0b90d9f17a30d42492c8a2f98a24374600729a98d4e0b663a44ed48b589cab0e445eec300245e590150c7d88340d902c27e0d8673f3257cb8393f647d6c7dead")
                .walletAddress(address)
                .build();
        assertThat(authorizationService.isAuthorizationValid(authorization)).isFalse();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceChallengeSignatureIsBadFormat() {
        when(eip712ChallengeService.containsEip712ChallengeString(challenge)).thenReturn(true);
        Authorization authorization = Authorization.builder()
                .challenge(challenge)
                .challengeSignature("0xbad")
                .walletAddress(address)
                .build();
        assertThat(authorizationService.isAuthorizationValid(authorization)).isFalse();
    }

    @Test
    public void isNotAuthorizedToGetResultSinceChallengeSignatureIsBadFormat2() {
        when(eip712ChallengeService.containsEip712ChallengeString(challenge)).thenReturn(true);
        Authorization authorization = Authorization.builder()
                .challenge(challenge)
                .challengeSignature("1b0b90d9f17a30d42492c8a2f98a24374600729a98d4e0b663a44ed48b589cab0e445eec300245e590150c7d88340d902c27e0d8673f3257cb8393f647d6c7FAKE")
                .walletAddress("0xabcd1339Ec7e762e639f4887E2bFe5EE8023E23E")
                .build();
        assertThat(authorizationService.isAuthorizationValid(authorization)).isFalse();
    }

    @Test
    public void shouldNotGetAuthorizationFromTokenSinceNullToken() {
        assertThat(authorizationService.getAuthorizationFromToken(null)).isNull();
    }

    @Test
    public void shouldNotGetAuthorizationFromTokenSinceTokenNotValid() {
        String token = "bad_token";

        assertThat(authorizationService.getAuthorizationFromToken(token)).isNull();
    }

    @Test
    public void shouldGetAuthorizationFromToken() {
        String token = "not_bad_token";

        assertThat(authorizationService.getAuthorizationFromToken(token)).isNotNull();
    }
}