package com.iexec.core.security;

import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class ChallengeService {

    // Map <WorkerWalletAdress, challenge>
    // this map will automatically delete entries older than one hour, ExpiringMap is thread-safe
    private ExpiringMap<String, String> challengeMap;

    ChallengeService() {
        this.challengeMap = ExpiringMap.builder()
                .expiration(60, TimeUnit.MINUTES)
                .expirationPolicy(ExpirationPolicy.CREATED)
                .build();
    }

    public String getChallenge(String workerWallet) {
        String challenge = RandomStringUtils.randomAlphabetic(10);
        challengeMap.putIfAbsent(workerWallet, challenge);
        return challengeMap.get(workerWallet);
    }
}
