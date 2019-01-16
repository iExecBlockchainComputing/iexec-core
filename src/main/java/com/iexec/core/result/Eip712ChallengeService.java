package com.iexec.core.result;

import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.HashUtils;
import com.iexec.core.result.eip712.Domain;
import com.iexec.core.result.eip712.Eip712Challenge;
import com.iexec.core.result.eip712.Message;
import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.bouncycastle.util.Arrays;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;
import org.web3j.utils.Numeric;

import java.io.IOException;
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
        //TODO move equald to tests

        // Domain
        Domain domain = eip712Challenge.getDomain();

        String DOMAIN_TYPEHASH = Numeric.toHexString(Hash.sha3("EIP712Domain(string name,string version,uint256 chainId)".getBytes()));
        String APP_NAME_DOMAIN_SEPARATOR = Numeric.toHexString(Hash.sha3(domain.getName().getBytes()));
        String VERSION_DOMAIN_SEPARATOR = Numeric.toHexString(Hash.sha3(domain.getVersion().getBytes()));
        String CHAIN_ID_DOMAIN_SEPARATOR = Numeric.toHexString(Numeric.toBytesPadded(BigInteger.valueOf(domain.getChainId()), 32));

        String DOMAIN_SEPARATOR = HashUtils.concatenateAndHash(DOMAIN_TYPEHASH,
                APP_NAME_DOMAIN_SEPARATOR,
                VERSION_DOMAIN_SEPARATOR,
                CHAIN_ID_DOMAIN_SEPARATOR);

        System.out.println("DOMAIN_SEPARATOR            " + DOMAIN_SEPARATOR);
        System.out.println("DOMAIN_SEPARATOR FINE       " + DOMAIN_SEPARATOR.equals("0x73b2ffe0e9f80f155eba2ca6ad915b2bd92e0d89c354112dc63b6dd70c30f51e"));

        // Challenge
        Message message = eip712Challenge.getMessage();

        String MESSAGE_TYPEHASH = Numeric.toHexString(Hash.sha3("Challenge(string challenge)".getBytes()));
        String CHALLENGE_MESSAGE = Numeric.toHexString(Hash.sha3(message.getChallenge().getBytes()));

        String MESSAGE_HASH = HashUtils.concatenateAndHash(MESSAGE_TYPEHASH, CHALLENGE_MESSAGE);

        System.out.println("MESSAGE_HASH           " + MESSAGE_HASH);
        System.out.println("MESSAGE_HASH FINE      " + MESSAGE_HASH.equals("0x2ff97da3e19fd11436479ffcec54baa5501c439d8a65f01dec5a217f7bf4bc70"));


        String challengeHashToSign = HashUtils.concatenateAndHash("0x1901", DOMAIN_SEPARATOR , MESSAGE_HASH);

        System.out.println("challengeHashToSign         " + challengeHashToSign);
        System.out.println("challengeHashToSign FINE    " + challengeHashToSign.equals("0x3bb958b947bc47479a7ee767d74a45146e41ac703d989d72f2b9c8f1aaf00a13"));

        /*
        REMINDER : Arrays.concatenate(byteArray) similar to Numeric.hexStringToByteArray("0x1901")
        encode should take bytes32 (padding could be required) while encodePacked takes for 0x12+1a2b
        System.out.println(Numeric.toHexString(Arrays.concatenate(
                Numeric.hexStringToByteArray("1901"),
                Numeric.hexStringToByteArray(DOMAIN_SEPARATOR) ,
                Numeric.hexStringToByteArray(MESSAGE_HASH))).equals(Numeric.toHexString(Numeric.hexStringToByteArray(
                "0x1901" +
                        Numeric.cleanHexPrefix(DOMAIN_SEPARATOR) +
                        Numeric.cleanHexPrefix(MESSAGE_HASH)))));*/

        return challengeHashToSign;
    }

    public static void main(String[] args) {
        Domain domain = Domain.builder()
                .name("iExec Result Repository")
                .version("1")
                .chainId(17L)
                .build();

        Message message = Message.builder()
                .challenge("0x10ff103511e3e233033628dbd641136d4670c16c33a4ce11950ab316ef18bce9")
                .build();

        Eip712Challenge eip712Challenge = Eip712Challenge.builder()
                .domain(domain)
                .message(message)
                .build();


        Eip712ChallengeService.convertEip712ChallengeToString(eip712Challenge);
    }

    Eip712Challenge generateEip712Challenge() throws IOException {
        challengeId++;
        String challenge = generateRandomHexToken();

        Domain domain = Domain.builder()
                .name("iExec Result Repository")
                .version("1")
                .chainId(17L)
                .build();
        Message message = Message.builder()
                .challenge(challenge)
                .build();

        Eip712Challenge eip712Challenge = Eip712Challenge.builder()
                .domain(domain)
                .message(message)
                .build();

        challengeMap.put(challengeId, convertEip712ChallengeToString(eip712Challenge));

        return eip712Challenge;
    }

    boolean containsEip712ChallengeString(String eip712ChallengeString) {
        return challengeMap.containsValue(eip712ChallengeString);
    }


    void invalidateChallenge(String eip712ChallengeString) {
        for (Map.Entry<Integer, String> entry : challengeMap.entrySet()) {
            if (entry.getValue().equals(eip712ChallengeString)) {
                challengeMap.remove(entry.getKey());
            }
        }
    }


}
