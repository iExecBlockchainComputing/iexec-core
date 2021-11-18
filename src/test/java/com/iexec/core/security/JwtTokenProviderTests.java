/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.security;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.jsonwebtoken.MalformedJwtException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class JwtTokenProviderTests {

    private final static String WALLET_WORKER = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";

    @Mock
    private ChallengeService challengeService;

    @InjectMocks
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
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
        boolean isValidToken = jwtTokenProvider.isValidToken(token);

        assertThat(isValidToken).isTrue();
    }

    @Test
    public void isValidTokenFalseSinceNotSameChallenge() {
        when(challengeService.getChallenge(WALLET_WORKER)).thenReturn("challenge1", "challenge2");
        
        jwtTokenProvider.init();
        String token = jwtTokenProvider.createToken(WALLET_WORKER);
        boolean isValidToken = jwtTokenProvider.isValidToken(token);

        assertThat(isValidToken).isFalse();
    }

    @Test
    public void isValidTokenFalseSinceNotValidOne() {
        when(challengeService.getChallenge(WALLET_WORKER)).thenReturn("challenge");

        jwtTokenProvider.init();
        jwtTokenProvider.createToken(WALLET_WORKER);
        boolean isValidToken = jwtTokenProvider.isValidToken("non.valid.token");

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

    @Test
    public void shouldThrowJwtExceptionSinceNotValidToken() {
        jwtTokenProvider.init();
        Assertions.assertThrows(MalformedJwtException.class, () -> jwtTokenProvider.getWalletAddress("non.valid.token"));
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
    public void shouldNotGetWalletAddressSinceNotValidBearerToken() {
        when(challengeService.getChallenge(WALLET_WORKER)).thenReturn("challenge");

        jwtTokenProvider.init();
        String token = jwtTokenProvider.createToken(WALLET_WORKER);
        String notBearerToken = "Not Bearer " + token;

        String walletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(notBearerToken);

        assertThat(walletAddress).isEmpty();
    }

}