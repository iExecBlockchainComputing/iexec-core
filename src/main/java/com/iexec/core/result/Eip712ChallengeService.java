package com.iexec.core.result;

import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.HashUtils;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.core.result.eip712.Domain;
import com.iexec.core.result.eip712.Eip712Challenge;
import com.iexec.core.result.eip712.Message;
import com.iexec.core.result.eip712.Types;
import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.tomcat.util.codec.binary.Base64;
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

    private static String generateRandomToken() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] token = new byte[32];
        secureRandom.nextBytes(token);
        return Base64.encodeBase64URLSafeString(token);
    }

    public String getEip712ChallengeString(Eip712Challenge eip712Challenge) {
        String domainSeparator = getDomainSeparator(eip712Challenge);
        String messageHash = getMessageHash(eip712Challenge);
        /*
        REMINDER : Arrays.concatenate(byteArray) similar to Numeric.hexStringToByteArray("0x1901")
        abi.encode should take bytes32 (padding could be required) while abi.encodePacked takes 0x12+1a2b
        System.out.println(Numeric.toHexString(Arrays.concatenate(Numeric.hexStringToByteArray("1901"),
                Numeric.hexStringToByteArray(domainSeparator) ,
                Numeric.hexStringToByteArray(messageHash))).equals(Numeric.toHexString(Numeric.hexStringToByteArray(
                "0x1901" + Numeric.cleanHexPrefix(domainSeparator) + Numeric.cleanHexPrefix(messageHash)))));*/

        return HashUtils.concatenateAndHash("0x1901", domainSeparator, messageHash);
    }

    String getDomainSeparator(Eip712Challenge eip712Challenge) {
        Domain domain = eip712Challenge.getDomain();

        String domainTypesParams = Types.typeParamsToString(eip712Challenge.getTypes().getDomainTypeParams());
        String domainType = "EIP712Domain(" + domainTypesParams + ")";//EIP712Domain(string name,string version,uint256 chainId)
        String domainTypeHash = Numeric.toHexString(Hash.sha3(domainType.getBytes()));
        String appNameDomainSeparator = Numeric.toHexString(Hash.sha3(domain.getName().getBytes()));
        String versionDomainSeparator = Numeric.toHexString(Hash.sha3(domain.getVersion().getBytes()));
        String chainIdDomainSeparator = Numeric.toHexString(Numeric.toBytesPadded(BigInteger.valueOf(domain.getChainId()), 32));

        return HashUtils.concatenateAndHash(domainTypeHash,
                appNameDomainSeparator,
                versionDomainSeparator,
                chainIdDomainSeparator);
    }

    String getMessageHash(Eip712Challenge eip712Challenge) {
        Message message = eip712Challenge.getMessage();

        String messageTypesParams = Types.typeParamsToString(eip712Challenge.getTypes().getChallengeTypeParams());
        String messageType = eip712Challenge.getPrimaryType() + "(" + messageTypesParams + ")";//Challenge(string challenge)
        String messageTypeHash = Numeric.toHexString(Hash.sha3(messageType.getBytes()));
        String challengeMessage = Numeric.toHexString(Hash.sha3(message.getChallenge().getBytes()));

        return HashUtils.concatenateAndHash(messageTypeHash, challengeMessage);
    }

    Eip712Challenge generateEip712Challenge(Integer chainId) {
        Eip712Challenge eip712Challenge = new Eip712Challenge(generateRandomToken(), chainId);
        this.saveEip712ChallengeString(getEip712ChallengeString(eip712Challenge));
        return eip712Challenge;
    }

    private void saveEip712ChallengeString(String eip712ChallengeString) {
        challengeId++;
        challengeMap.put(challengeId, eip712ChallengeString);
    }

    boolean containsEip712ChallengeString(String eip712ChallengeString) {
        return challengeMap.containsValue(eip712ChallengeString);
    }

    void invalidateEip712ChallengeString(String eip712ChallengeString) {
        for (Map.Entry<Integer, String> entry : challengeMap.entrySet()) {
            if (entry.getValue().equals(eip712ChallengeString)) {
                challengeMap.remove(entry.getKey());
            }
        }
    }

    boolean isAuthorizationValid(String eip712ChallengeString, String challengeSignature, String walletAddress) {
        challengeSignature = Numeric.cleanHexPrefix(challengeSignature);

        if (challengeSignature.length() < 130) {
            log.error("Eip712ChallengeString has a bad signature format [downloadRequester:{}]", walletAddress);
            return false;
        }
        String v = challengeSignature.substring(128, 130);
        String s = challengeSignature.substring(64, 128);
        String r = challengeSignature.substring(0, 64);

        //ONE: check if eip712Challenge is in eip712Challenge map
        if (!this.containsEip712ChallengeString(eip712ChallengeString)) {
            log.error("Eip712ChallengeString provided doesn't match any challenge [downloadRequester:{}]", walletAddress);
            return false;
        }

        //TWO: check if ecrecover on eip712Challenge & signature match address
        if (!SignatureUtils.doesSignatureMatchesAddress(BytesUtils.stringToBytes(r), BytesUtils.stringToBytes(s),
                eip712ChallengeString, StringUtils.lowerCase(walletAddress))) {
            log.error("Signature provided doesn't match walletAddress [downloadRequester:{}, sign.r:{}, sign.s:{}, eip712ChallengeString:{}]",
                    walletAddress, r, s, eip712ChallengeString);
            return false;
        }

        return true;
    }



}
