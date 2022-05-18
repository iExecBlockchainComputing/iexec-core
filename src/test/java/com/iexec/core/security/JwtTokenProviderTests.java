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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTests {

    private static final String WALLET_ADDRESS = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";

    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider();

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
        String token = jwtTokenProvider.createToken(WALLET_ADDRESS, "challenge");
        boolean isValidToken = jwtTokenProvider.isValidToken(token, "challenge");
        assertThat(isValidToken).isTrue();
    }

    @Test
    void isValidTokenFalseSinceNotSameChallenge() {
        String token = jwtTokenProvider.createToken(WALLET_ADDRESS, "challenge1");
        boolean isValidToken = jwtTokenProvider.isValidToken(token, "challenge2");
        assertThat(isValidToken).isFalse();
    }

    @Test
    void isValidTokenFalseSinceNotValidOne() {
        jwtTokenProvider.createToken(WALLET_ADDRESS, "challenge");
        boolean isValidToken = jwtTokenProvider.isValidToken("non.valid.token", "challenge");
        assertThat(isValidToken).isFalse();
    }
    //endregion

    //region getWalletAddress
    @Test
    void shouldGetCorrectWalletAddress() {
        String token = jwtTokenProvider.createToken(WALLET_ADDRESS, "challenge");
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
        String token = jwtTokenProvider.createToken(WALLET_ADDRESS, "challenge");
        String bearerToken = "Bearer " + token;
        String walletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        assertThat(walletAddress).isEqualTo(WALLET_ADDRESS);
    }

    @Test
    void shouldNotGetWalletAddressSinceNotValidBearerToken() {
        String token = jwtTokenProvider.createToken(WALLET_ADDRESS, "challenge");
        String notBearerToken = "Not Bearer " + token;
        String walletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(notBearerToken);
        assertThat(walletAddress).isEmpty();
    }
    //endregion

}