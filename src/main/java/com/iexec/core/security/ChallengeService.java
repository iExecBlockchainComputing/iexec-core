package com.iexec.core.security;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChallengeService {

    // Map <WorkerWalletAdress, Challenge>
    private ConcurrentHashMap<String, String> challengeMap;

    ChallengeService() {
        this.challengeMap = new ConcurrentHashMap<>();
    }

    public String getChallenge(String workerWallet) {
        String challenge = RandomStringUtils.randomAlphabetic(10);
        challengeMap.putIfAbsent(workerWallet, challenge);
        return challengeMap.get(workerWallet);
    }
}
