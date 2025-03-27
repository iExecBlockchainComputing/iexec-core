/*
 * Copyright 2022-2025 IEXEC BLOCKCHAIN TECH
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

import com.iexec.commons.poco.eip712.entity.EIP712Challenge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Keys;

import static com.iexec.core.TestUtils.WALLET_WORKER_1;
import static com.iexec.core.TestUtils.WALLET_WORKER_2;
import static org.assertj.core.api.Assertions.assertThat;

class EIP712ChallengeServiceTests {

    private EIP712ChallengeService challengeService;

    @BeforeEach
    void preflight() {
        challengeService = new EIP712ChallengeService(1);
    }

    @Test
    void shouldGetSameChallengeForSameWallet() {
        final EIP712Challenge challenge1 = challengeService.getChallenge(WALLET_WORKER_1);
        final EIP712Challenge challenge2 = challengeService.getChallenge(WALLET_WORKER_1);
        assertThat(challenge1).usingRecursiveComparison().isEqualTo(challenge2);
    }

    @Test
    void shouldGetSameChallengeWithChecksumWallet() {
        final String checksumWallet = Keys.toChecksumAddress(WALLET_WORKER_1);
        assertThat(checksumWallet).isNotEqualTo(WALLET_WORKER_1);
        assertThat(challengeService.getChallenge(checksumWallet))
                .usingRecursiveComparison()
                .isEqualTo(challengeService.getChallenge(WALLET_WORKER_1));
    }

    @Test
    void shouldGetSameChallengeWithUppercaseWallet() {
        final String upperCaseWalletAddress = WALLET_WORKER_1.substring(2).toUpperCase(); // 0x prefix removal
        assertThat(upperCaseWalletAddress).isNotEqualTo(WALLET_WORKER_1);
        assertThat(challengeService.getChallenge(upperCaseWalletAddress))
                .usingRecursiveComparison()
                .isEqualTo(challengeService.getChallenge(WALLET_WORKER_1));
    }

    @Test
    void shouldGetDifferentChallengesForDifferentWallets() {
        final EIP712Challenge challenge1 = challengeService.getChallenge(WALLET_WORKER_1);
        final EIP712Challenge challenge2 = challengeService.getChallenge(WALLET_WORKER_2);
        assertThat(challenge1).usingRecursiveComparison().isNotEqualTo(challenge2);
    }

}
