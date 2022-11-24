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

import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class JwtTokenProviderTests {

    private static final String WALLET_ADDRESS = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";

    @TempDir
    private static Path tmpDir;

    @Mock
    private ChallengeService challengeService;

    private JwtConfig jwtConfig;

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void init() throws IOException {
        jwtConfig = new JwtConfig(String.join(File.separator, tmpDir.toString(), ".key"));
        MockitoAnnotations.openMocks(this);
        jwtTokenProvider = new JwtTokenProvider(challengeService, jwtConfig);
    }

    //region key persistence
    @Test
    void shouldValidateTokenWhenKeyFileExists() throws IOException {
        final String token = jwtTokenProvider.createToken(WALLET_ADDRESS);
        final JwtTokenProvider newService = new JwtTokenProvider(challengeService, jwtConfig);
        assertAll(
                () -> assertEquals(WALLET_ADDRESS, jwtTokenProvider.getWalletAddress(token)),
                () -> assertEquals(WALLET_ADDRESS, newService.getWalletAddress(token))
        );
    }

    @Test
    void shouldNotValidateTokenWhenKeyFileRecreated() throws IOException {
        final String token = jwtTokenProvider.createToken(WALLET_ADDRESS);
        Files.deleteIfExists(Path.of(jwtConfig.getKeyPath()));
        final JwtTokenProvider newService = new JwtTokenProvider(challengeService, jwtConfig);
        assertAll(
                () -> assertEquals(WALLET_ADDRESS, jwtTokenProvider.getWalletAddress(token)),
                () -> assertThrows(SignatureException.class,
                        () -> newService.getWalletAddress(token))
        );
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
        String notBearerToken = "Not Bearer eb604db8eba185df03ea4f5";
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
        when(challengeService.getChallenge(WALLET_ADDRESS)).thenReturn("challenge");
        String token = jwtTokenProvider.createToken(WALLET_ADDRESS);
        boolean isValidToken = jwtTokenProvider.isValidToken(token);
        assertThat(isValidToken).isTrue();
    }

    @Test
    void isValidTokenFalseSinceNotSameChallenge() {
        when(challengeService.getChallenge(WALLET_ADDRESS)).thenReturn("challenge1", "challenge2");
        String token = jwtTokenProvider.createToken(WALLET_ADDRESS);
        boolean isValidToken = jwtTokenProvider.isValidToken(token);
        assertThat(isValidToken).isFalse();
    }

    @Test
    void isValidTokenFalseSinceNotValidOne() {
        when(challengeService.getChallenge(WALLET_ADDRESS)).thenReturn("challenge");
        jwtTokenProvider.createToken(WALLET_ADDRESS);
        boolean isValidToken = jwtTokenProvider.isValidToken("non.valid.token");
        assertThat(isValidToken).isFalse();
    }
    //endregion

    //region getWalletAddress
    @Test
    void shouldGetCorrectWalletAddress() {
        when(challengeService.getChallenge(WALLET_ADDRESS)).thenReturn("challenge");
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
        when(challengeService.getChallenge(WALLET_ADDRESS)).thenReturn("challenge");
        String token = jwtTokenProvider.createToken(WALLET_ADDRESS);
        String bearerToken = "Bearer " + token;
        String walletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        assertThat(walletAddress).isEqualTo(WALLET_ADDRESS);
    }

    @Test
    void shouldNotGetWalletAddressSinceNotValidBearerToken() {
        when(challengeService.getChallenge(WALLET_ADDRESS)).thenReturn("challenge");
        String token = jwtTokenProvider.createToken(WALLET_ADDRESS);
        String notBearerToken = "Not Bearer " + token;
        String walletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(notBearerToken);
        assertThat(walletAddress).isEmpty();
    }
    //endregion

}