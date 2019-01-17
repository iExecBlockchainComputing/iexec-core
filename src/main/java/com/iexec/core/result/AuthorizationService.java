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
public class AuthorizationService {


    private Eip712ChallengeService challengeService;

    AuthorizationService(Eip712ChallengeService challengeService) {
        this.challengeService = challengeService;
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
        if (!challengeService.containsEip712ChallengeString(eip712ChallengeString)) {
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
