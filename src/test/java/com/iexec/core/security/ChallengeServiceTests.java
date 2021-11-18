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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;

public class ChallengeServiceTests {

    private final static String WALLET_WORKER_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private final static String WALLET_WORKER_2 = "0x2a69b2eb604db8eba185df03ea4f5288dcbbd248";

    private ChallengeService challengeService = new ChallengeService();

    @BeforeEach
    public void init() { MockitoAnnotations.initMocks(this); }

    @Test
    public void shouldGetSameChallengeForSameWallet() {
        String challenge1 = challengeService.getChallenge(WALLET_WORKER_1);
        String challenge2 = challengeService.getChallenge(WALLET_WORKER_1);
        assertThat(challenge1).isEqualTo(challenge2);
    }

    @Test
    public void shouldGetDifferentChallengesForDifferentWallets() {
        String challenge1 = challengeService.getChallenge(WALLET_WORKER_1);
        String challenge2 = challengeService.getChallenge(WALLET_WORKER_2);
        assertThat(challenge1).isNotEqualTo(challenge2);
    }
}