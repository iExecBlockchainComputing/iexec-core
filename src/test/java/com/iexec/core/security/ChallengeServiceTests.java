package com.iexec.core.security;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class ChallengeServiceTests {

    private final static String WALLET_WORKER_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private final static String WALLET_WORKER_2 = "0x2a69b2eb604db8eba185df03ea4f5288dcbbd248";

    private ChallengeService challengeService = new ChallengeService();

    @Before
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