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

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class JwtTokenProviderTests {

    private static final String WALLET_ADDRESS = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";

    private final ChallengeService challengeService;
    private final JwtTokenProvider jwtTokenProvider;
    private String secretKey;

    JwtTokenProviderTests() {
        challengeService = spy(new ChallengeService());
        jwtTokenProvider = spy(new JwtTokenProvider(challengeService));
    }

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        SecureRandom secureRandom = new SecureRandom();
        byte[] seed = new byte[32];
        secureRandom.nextBytes(seed);
        secretKey = Base64.getEncoder().encodeToString(seed);
        ReflectionTestUtils.setField(challengeService, "challengesMap", new ConcurrentHashMap<>());
        ReflectionTestUtils.setField(jwtTokenProvider, "jwTokensMap", new ConcurrentHashMap<>());
        ReflectionTestUtils.setField(jwtTokenProvider, "secretKey", Base64.getEncoder().encodeToString(seed));
    }

    //region createToken
    @Test
    void shouldReturnExistingTokenIfValid() {
        String token1 = jwtTokenProvider.createToken(WALLET_ADDRESS);
        String token2 = jwtTokenProvider.createToken(WALLET_ADDRESS);
        assertThat(token1).isEqualTo(token2);
        verify(challengeService).removeChallenge(WALLET_ADDRESS);
    }

    @Test
    void shouldReturnNewTokenIfExpired() {
        Date now = new Date();
        String token = Jwts.builder()
                .setAudience(WALLET_ADDRESS)
                .setIssuedAt(now)
                .setExpiration(now)
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
        ConcurrentHashMap<String, String> tokensMap = new ConcurrentHashMap<>();
        tokensMap.put(WALLET_ADDRESS, token);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwTokensMap", tokensMap);
        String newToken = jwtTokenProvider.createToken(WALLET_ADDRESS);
        assertThat(newToken).isNotEqualTo(WALLET_ADDRESS);
    }
    //endregion

    //region resolveToken
    @Test
    void shouldResolveToken() {
        String bearerToken = "Bearer eb604db8eba185df03ea4f5";
        String resolvedToken = jwtTokenProvider.resolveToken(bearerToken);
        assertThat(resolvedToken).isEqualTo(bearerToken.substring(7));
    } 

    @Test
    void shouldNotResolveTokenSinceNotValidOne() {
        String notBearerToken = "Not " + jwtTokenProvider.createToken(WALLET_ADDRESS);
        String resolvedToken = jwtTokenProvider.resolveToken(notBearerToken);
        assertThat(resolvedToken).isNull();
    } 

    @Test
    void shouldNotResolveTokenSinceNullOne() {
        String resolvedToken = jwtTokenProvider.resolveToken(null);
        assertThat(resolvedToken).isNull();
    }
    //endregion

    //region isValidToken
    @Test
    void isValidTokenTrue() {
        String token = jwtTokenProvider.createToken(WALLET_ADDRESS);
        boolean isValidToken = jwtTokenProvider.isValidToken(token);
        assertThat(isValidToken).isTrue();
    }

    @Test
    void isValidTokenFalseSinceExpired() {
        Date now = new Date();
        String token = Jwts.builder()
                .setAudience(WALLET_ADDRESS)
                .setIssuedAt(now)
                .setExpiration(now)
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
        boolean isValidToken = jwtTokenProvider.isValidToken(token);
        assertThat(isValidToken).isFalse();
    }

    @Test
    void isValidTokenFalseSinceNotSameChallenge() {
        String token = jwtTokenProvider.createToken(WALLET_ADDRESS);
        String challenge1 = challengeService.getChallenge(WALLET_ADDRESS);
        challengeService.removeChallenge(WALLET_ADDRESS);
        String challenge2 = challengeService.getChallenge(WALLET_ADDRESS);
        boolean isValidToken = jwtTokenProvider.isValidToken(token);
        assertThat(challenge1).isNotEqualTo(challenge2);
        assertThat(isValidToken).isFalse();
    }

    @Test
    void isValidTokenFalseSinceNotSigned() {
        String token = Jwts.builder()
                .setAudience(WALLET_ADDRESS)
                .setIssuedAt(new Date())
                .setSubject(challengeService.getChallenge(WALLET_ADDRESS))
                .compact();
        boolean isTokenValid = jwtTokenProvider.isValidToken(token);
        assertThat(isTokenValid).isFalse();
    }

    @Test
    void isValidTokenFalseSinceNotValidOne() {
        jwtTokenProvider.createToken(WALLET_ADDRESS);
        boolean isValidToken = jwtTokenProvider.isValidToken("non.valid.token");
        assertThat(isValidToken).isFalse();
    }
    //endregion

    //region getWalletAddress
    @Test
    void shouldGetCorrectWalletAddress() {
        String token = jwtTokenProvider.createToken(WALLET_ADDRESS);
        String walletAddress = jwtTokenProvider.getWalletAddress(token);
        assertThat(walletAddress).isEqualTo(WALLET_ADDRESS);
    }

    @Test
    void shouldThrowJwtExceptionSinceNotValidToken() {
        Assertions.assertThrows(MalformedJwtException.class, () -> jwtTokenProvider.getWalletAddress("non.valid.token"));
    }
    //endregion

    //region getWalletAddressFromBearerToken
    @Test
    void shouldGetCorrectWalletAddressFromBearerToken() {
        String token = jwtTokenProvider.createToken(WALLET_ADDRESS);
        String bearerToken = "Bearer " + token;
        String walletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        assertThat(walletAddress).isEqualTo(WALLET_ADDRESS);
    }

    @Test
    void shouldNotGetWalletAddressSinceNotValidBearerToken() {
        String token = jwtTokenProvider.createToken(WALLET_ADDRESS);
        String notBearerToken = "Not Bearer " + token;
        String walletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(notBearerToken);
        assertThat(walletAddress).isEmpty();
    }
    //endregion

}