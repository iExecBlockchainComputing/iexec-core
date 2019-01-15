package com.iexec.core.security;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.jsonwebtoken.MalformedJwtException;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.when;

public class JwtTokenProviderTests {

    private final static String WALLET_WORKER = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";

    @Mock
    private ChallengeService challengeService;

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    @Before
    public void init() { MockitoAnnotations.initMocks(this); }

    @Test
    public void shouldResolveToken() {
        String bearerToken = "Bearer eb604db8eba185df03ea4f5";

        jwtTokenProvider.init();
        String resolvedToken = jwtTokenProvider.resolveToken(bearerToken);

        assertThat(resolvedToken).isEqualTo(bearerToken.substring(7, bearerToken.length()));
    } 

    @Test
    public void shouldNotResolveTokenSinceNotValidOne() {
        String notBearerToken = "Not Bearer eb604db8eba185df03ea4f5";

        jwtTokenProvider.init();
        String resolvedToken = jwtTokenProvider.resolveToken(notBearerToken);

        assertThat(resolvedToken).isNull();
    } 

    @Test
    public void shouldNotResolveTokenSinceNullOne() {
        String nullToken = null;

        jwtTokenProvider.init();
        String resolvedToken = jwtTokenProvider.resolveToken(nullToken);

        assertThat(resolvedToken).isNull();
    }

    @Test
    public void isValidTokenTrue() {
        when(challengeService.getChallenge(WALLET_WORKER)).thenReturn("challenge");
        
        jwtTokenProvider.init();
        String token = jwtTokenProvider.createToken(WALLET_WORKER);
        boolean isValidToken = jwtTokenProvider.validateToken(token);

        assertThat(isValidToken).isTrue();
    }

    @Test
    public void isValidTokenFalseSinceNotSameChallenge() {
        when(challengeService.getChallenge(WALLET_WORKER)).thenReturn("challenge1", "challenge2");
        
        jwtTokenProvider.init();
        String token = jwtTokenProvider.createToken(WALLET_WORKER);
        boolean isValidToken = jwtTokenProvider.validateToken(token);

        assertThat(isValidToken).isFalse();
    }

    @Test
    public void isValidTokenFalseSinceNotValidOne() {
        when(challengeService.getChallenge(WALLET_WORKER)).thenReturn("challenge");

        jwtTokenProvider.init();
        jwtTokenProvider.createToken(WALLET_WORKER);
        boolean isValidToken = jwtTokenProvider.validateToken("non.valid.token");

        assertThat(isValidToken).isFalse();
    }

    @Test
    public void shouldGetCorrectWalletAddress() {
        when(challengeService.getChallenge(WALLET_WORKER)).thenReturn("challenge");

        jwtTokenProvider.init();
        String token = jwtTokenProvider.createToken(WALLET_WORKER);
        String walletAddress = jwtTokenProvider.getWalletAddress(token);

        assertThat(walletAddress).isEqualTo(WALLET_WORKER);
    }

    @Test(expected=MalformedJwtException.class)
    public void shouldThrowJwtExceptionSinceNotValidToken() {
        jwtTokenProvider.init();
        jwtTokenProvider.getWalletAddress("non.valid.token");
    }

    @Test
    public void shouldGetCorrectWalletAddressFromBearerToken() {
        when(challengeService.getChallenge(WALLET_WORKER)).thenReturn("challenge");

        jwtTokenProvider.init();
        String token = jwtTokenProvider.createToken(WALLET_WORKER);
        String bearerToken = "Bearer " + token;
        String walletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);

        assertThat(walletAddress).isEqualTo(WALLET_WORKER);
    }

    @Test
    public void shouldThrowJwtExceptionSinceNotValidBearerToken() {
        when(challengeService.getChallenge(WALLET_WORKER)).thenReturn("challenge");

        jwtTokenProvider.init();
        String token = jwtTokenProvider.createToken(WALLET_WORKER);
        String notBearerToken = "Not Bearer " + token;

        String walletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(notBearerToken);

        assertThat(walletAddress).isEmpty();
    }

}