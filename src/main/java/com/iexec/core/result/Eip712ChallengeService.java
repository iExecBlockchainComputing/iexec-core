package com.iexec.core.result;

import com.iexec.common.utils.BytesUtils;
import com.iexec.core.result.eip712.Domain;
import com.iexec.core.result.eip712.Eip712Challenge;
import com.iexec.core.result.eip712.Message;
import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class Eip712ChallengeService {

    private int challengeId;
    private ExpiringMap<Integer, String> challengeMap;

    Eip712ChallengeService() {
        this.challengeMap = ExpiringMap.builder()
                .expiration(60, TimeUnit.MINUTES)
                .expirationPolicy(ExpirationPolicy.CREATED)
                .build();
        challengeId = 0;
    }

    private static String generateRandomHexToken() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] token = new byte[32];
        secureRandom.nextBytes(token);
        return BytesUtils.bytesToString(token);
    }

    static String convertEip712ChallengeToString(Eip712Challenge eip712Challenge) {
        byte[] domainSep = Hash.sha3("EIP712Domain(string name,string version,uint256 chainId)".getBytes());
        byte[] challengeType = Hash.sha3("Challenge(string challenge)".getBytes());
        Domain domain = eip712Challenge.getDomain();
        String domainAsString =
                Numeric.toHexString(domainSep) +
                        Numeric.toHexString(Hash.sha3(domain.getName().getBytes())).substring(2) +
                        Numeric.toHexString(Hash.sha3(domain.getVersion().getBytes())).substring(2) +
                        Numeric.toHexStringNoPrefix(Numeric.toBytesPadded(BigInteger.valueOf(domain.getChainId()), 32));

        String eip712ChallengeString = Numeric.toHexStringNoPrefix(Hash.sha3(
                Numeric.hexStringToByteArray(
                        "0x1901" +
                                Numeric.toHexStringNoPrefix(Hash.sha3(Numeric.hexStringToByteArray(domainAsString))) +
                                Numeric.toHexStringNoPrefix(Hash.sha3(Numeric.hexStringToByteArray(
                                        Numeric.toHexStringNoPrefix(challengeType) +
                                                Numeric.toHexStringNoPrefix(eip712Challenge.getMessage().getChallenge().getBytes())
                                ))))
        ));
        log.info("eip712ChallengeString plain:" + eip712ChallengeString);
        return eip712ChallengeString;
    }

    Eip712Challenge generateEip712Challenge() {
        challengeId++;
        String challenge = generateRandomHexToken();

        Domain domain = Domain.builder()
                .name("iExec Result Repository")
                .version("1")
                .chainId(17L)
                .build();
        String primaryType = "Challenge";
        Message message = Message.builder()
                .challenge(challenge)
                .build();

        Eip712Challenge eip712Challenge = Eip712Challenge.builder()
                .domain(domain)
                .primaryType(primaryType)
                .message(message)
                .build();

        challengeMap.put(challengeId, convertEip712ChallengeToString(eip712Challenge));

        return eip712Challenge;
    }

    boolean containsEip712ChallengeString(String eip712ChallengeString) {
        return challengeMap.containsValue(eip712ChallengeString);
    }


    void invalidateChallenge(String eip712ChallengeString) {
        for (Map.Entry<Integer, String> entry : challengeMap.entrySet()){
            if (entry.getValue().equals(eip712ChallengeString)){
                challengeMap.remove(entry.getKey());
            }
        }
    }
}
