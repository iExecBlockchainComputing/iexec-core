/*
 * Copyright 2022 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.chain.eip712.entity.Challenge;
import com.iexec.common.chain.eip712.entity.EIP712Challenge;
import com.iexec.common.chain.eip712.EIP712Domain;
import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class EIP712ChallengeService {

    private final EIP712Domain domain;
    private final ExpiringMap<String, EIP712Challenge> challenges;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecureRandom secureRandom = new SecureRandom();

    EIP712ChallengeService(int chainId, JwtTokenProvider jwtTokenProvider) {
        this.domain = new EIP712Domain("iExec Core", "1", chainId, null);
        this.challenges = ExpiringMap.builder()
                .expiration(60, TimeUnit.MINUTES)
                .expirationPolicy(ExpirationPolicy.CREATED)
                .build();
        this.jwtTokenProvider = jwtTokenProvider;
    }

    public EIP712Challenge generateChallenge() {
        byte[] token = new byte[32];
        secureRandom.nextBytes(token);
        String challenge = Base64.getEncoder().encodeToString(token);
        return new EIP712Challenge(domain, Challenge.builder().challenge(challenge).build());
    }

    public EIP712Challenge getChallenge(String walletAddress) {
        challenges.computeIfAbsent(walletAddress, s -> this.generateChallenge());
        return challenges.get(walletAddress);
    }

    public String createToken(String walletAddress) {
        EIP712Challenge challenge = challenges.get(walletAddress);
        return jwtTokenProvider.createToken(walletAddress, challenge.getMessage().getChallenge());
    }

    public String getWalletAddressFromBearerToken(String bearerToken) {
        return jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
    }

    public boolean isValidToken(String bearerToken) {
        String walletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        if (walletAddress.isEmpty()) {
            return false;
        }
        EIP712Challenge challenge = challenges.get(walletAddress);
        return challenge != null
                && jwtTokenProvider.isValidToken(jwtTokenProvider.resolveToken(bearerToken), challenge.getMessage().getChallenge());
    }

}
