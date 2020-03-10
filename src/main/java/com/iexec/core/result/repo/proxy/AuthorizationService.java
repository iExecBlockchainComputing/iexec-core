package com.iexec.core.result.repo.proxy;

import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.SignatureUtils;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.web3j.utils.Numeric;

import java.util.Date;
import java.util.Optional;

@Service
@Slf4j
public class AuthorizationService {
    /**
     * TODO:
     * Rename AuthorizationService to JwtService
     * Rename Authorization to SignedChallenge
     */

    private Eip712ChallengeService challengeService;
    private JwtRepository jwtRepository;

    AuthorizationService(Eip712ChallengeService challengeService, JwtRepository jwtRepository) {
        this.challengeService = challengeService;
        this.jwtRepository = jwtRepository;
    }

    //TODO Make it private
    protected boolean isAuthorizationValid(Authorization authorization) {
        if (authorization == null) {
            log.error("Authorization should not be null [authorization:{}]", authorization);
            return false;
        }

        String eip712ChallengeString = authorization.getChallenge();
        String challengeSignature = authorization.getChallengeSignature();
        String walletAddress = authorization.getWalletAddress();

        challengeSignature = Numeric.cleanHexPrefix(challengeSignature);

        if (challengeSignature.length() < 130) {
            log.error("Eip712ChallengeString has a bad signature format [downloadRequester:{}]", walletAddress);
            return false;
        }
        String r = challengeSignature.substring(0, 64);
        String s = challengeSignature.substring(64, 128);
        String v = challengeSignature.substring(128, 130);

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

    protected Authorization getAuthorizationFromToken(String token) {
        if ((token == null) || (token.split("_").length != 3)) {
            return null;
        }

        String[] parts = token.split("_");
        return Authorization.builder()
                .challenge(parts[0])
                .challengeSignature(parts[1])
                .walletAddress(parts[2])
                .build();
    }

    public String getOrCreateJwt(String signedChallenge) {
        Authorization auth = getAuthorizationFromToken(signedChallenge);

        boolean isSignedChallengeValid = isAuthorizationValid(auth);

        if (!isSignedChallengeValid){
            return "";
        }

        String jwtString ;
        Optional<Jwt> oExistingJwt = jwtRepository.findByWalletAddress(auth.getWalletAddress());

        if (oExistingJwt.isPresent()){
            jwtString = oExistingJwt.get().getJwtString(); //TODO generate new token
        } else {
            jwtString = Jwts.builder()
                    .setAudience(auth.getWalletAddress())
                    .setIssuedAt(new Date())
                    .setSubject(RandomStringUtils.randomAlphanumeric(64))
                    .compact();
            jwtRepository.save(new Jwt(auth.getWalletAddress(), jwtString));
        }

        challengeService.invalidateEip712ChallengeString(auth.getChallenge());

        return jwtString;
    }

    public boolean isValidJwt(String jwtString) {
        String claimedWalletAddress = getWalletAddressFromJwtString(jwtString);

        Optional<Jwt> oExistingJwt = jwtRepository.findByWalletAddress(claimedWalletAddress);

        if (!oExistingJwt.isPresent()){
            return false;
        }

        if (jwtString.equals(oExistingJwt.get().getJwtString())){
            return true;
        }

        return false;
    }

    public String getWalletAddressFromJwtString(String jwtString) {
        return Jwts.parser().parseClaimsJwt(jwtString).getBody().getAudience();
    }


}
