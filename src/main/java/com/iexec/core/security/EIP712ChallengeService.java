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

import com.iexec.common.chain.eip712.EIP712Domain;
import com.iexec.common.chain.eip712.entity.Challenge;
import com.iexec.common.chain.eip712.entity.EIP712Challenge;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Keys;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

@Service
public class EIP712ChallengeService {

    private final EIP712Domain domain;
    private final ExpiringMap<String, EIP712Challenge> challenges;
    private final SecureRandom secureRandom = new SecureRandom();

    EIP712ChallengeService(int chainId) {
        this.domain = new EIP712Domain("iExec Core", "1", chainId, null);
        this.challenges = ExpiringMap.builder()
                .expiration(60, TimeUnit.MINUTES)
                .expirationPolicy(ExpirationPolicy.CREATED)
                .build();
    }

    public EIP712Challenge generateChallenge() {
        byte[] token = new byte[32];
        secureRandom.nextBytes(token);
        String challenge = Base64.getEncoder().encodeToString(token);
        return new EIP712Challenge(domain, Challenge.builder().challenge(challenge).build());
    }

    public EIP712Challenge getChallenge(String walletAddress) {
        walletAddress = Keys.toChecksumAddress(walletAddress);
        challenges.computeIfAbsent(walletAddress, s -> this.generateChallenge());
        return challenges.get(walletAddress);
    }

}
