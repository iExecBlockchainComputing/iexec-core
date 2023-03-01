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
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.info.BuildProperties;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import static com.iexec.core.security.JwtTokenProvider.KEY_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class JwtTokenProviderTests {

    private static final String JWT_AUDIENCE = "iExec Scheduler vX.Y.Z";
    private static final String WALLET_ADDRESS = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private static final SecureRandom secureRandom = new SecureRandom();
    private JwtTokenProvider jwtTokenProvider;
    private final byte[] secretKey = new byte[KEY_SIZE];

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        secureRandom.nextBytes(secretKey);
        BuildProperties buildProperties = mock(BuildProperties.class);
        when(buildProperties.getVersion()).thenReturn("X.Y.Z");
        jwtTokenProvider = spy(new JwtTokenProvider(buildProperties));
        ReflectionTestUtils.setField(jwtTokenProvider, "secretKey", secretKey);
    }

    //region createToken
    @Test
    void shouldReturnExistingTokenIfValid() {
        String token1 = jwtTokenProvider.getOrCreateToken(WALLET_ADDRESS);
        String token2 = jwtTokenProvider.getOrCreateToken(WALLET_ADDRESS);
        assertThat(token1).isEqualTo(token2);
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
        String notBearerToken = "Not " + jwtTokenProvider.getOrCreateToken(WALLET_ADDRESS);
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
        String token = jwtTokenProvider.getOrCreateToken(WALLET_ADDRESS);
        boolean isValidToken = jwtTokenProvider.isValidToken(token);
        assertThat(isValidToken).isTrue();
    }

    @Test
    void isValidTokenFalseSinceBadAudience() {
        Date now = new Date();
        String token = Jwts.builder()
                .setAudience("iExec Scheduler")
                .setSubject(WALLET_ADDRESS)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 10000L))
                .signWith(Keys.hmacShaKeyFor(secretKey), SignatureAlgorithm.HS256)
                .compact();
        ConcurrentHashMap<String, String> tokensMap = new ConcurrentHashMap<>();
        tokensMap.put(WALLET_ADDRESS, token);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwTokensMap", tokensMap);
        boolean isValidToken = jwtTokenProvider.isValidToken(token);
        assertThat(isValidToken).isFalse();
    }

    @Test
    void isValidTokenFalseSinceExpired() {
        Date now = new Date();
        String token = Jwts.builder()
                .setAudience(JWT_AUDIENCE)
                .setSubject(WALLET_ADDRESS)
                .setIssuedAt(now)
                .setExpiration(now)
                .signWith(Keys.hmacShaKeyFor(secretKey), SignatureAlgorithm.HS256)
                .compact();
        ConcurrentHashMap<String, String> tokensMap = new ConcurrentHashMap<>();
        tokensMap.put(WALLET_ADDRESS, token);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwTokensMap", tokensMap);
        boolean isValidToken = jwtTokenProvider.isValidToken(token);
        assertThat(isValidToken).isFalse();
    }

    @Test
    void isValidTokenFalseSinceNotSigned() {
        String token = Jwts.builder()
                .setAudience(JWT_AUDIENCE)
                .setIssuedAt(new Date())
                .setSubject(WALLET_ADDRESS)
                .compact();
        boolean isTokenValid = jwtTokenProvider.isValidToken(token);
        assertThat(isTokenValid).isFalse();
    }

    @Test
    void isValidTokenFalseSinceWronglySigned() {
        final byte[] newKey = new byte[KEY_SIZE];
        secureRandom.nextBytes(newKey);
        Date now = new Date();
        String token = Jwts.builder()
                .setAudience(JWT_AUDIENCE)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + 10000L))
                .setSubject(WALLET_ADDRESS)
                .signWith(Keys.hmacShaKeyFor(newKey), SignatureAlgorithm.HS256)
                .compact();
        boolean isTokenValid = jwtTokenProvider.isValidToken(token);
        assertThat(isTokenValid).isFalse();
    }

    @Test
    void isValidTokenFalseSinceNotValidOne() {
        jwtTokenProvider.getOrCreateToken(WALLET_ADDRESS);
        boolean isValidToken = jwtTokenProvider.isValidToken("non.valid.token");
        assertThat(isValidToken).isFalse();
    }
    //endregion

    //region getWalletAddress
    @Test
    void shouldGetCorrectWalletAddress() {
        String token = jwtTokenProvider.getOrCreateToken(WALLET_ADDRESS);
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
        String token = jwtTokenProvider.getOrCreateToken(WALLET_ADDRESS);
        String bearerToken = "Bearer " + token;
        String walletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        assertThat(walletAddress).isEqualTo(WALLET_ADDRESS);
    }

    @Test
    void shouldNotGetWalletAddressSinceNotValidBearerToken() {
        String token = jwtTokenProvider.getOrCreateToken(WALLET_ADDRESS);
        String notBearerToken = "Not Bearer " + token;
        String walletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(notBearerToken);
        assertThat(walletAddress).isEmpty();
    }
    //endregion

}