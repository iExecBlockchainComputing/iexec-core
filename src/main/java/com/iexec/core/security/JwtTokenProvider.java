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

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Base64;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    private ChallengeService challengeService;
    private String secretKey;

    public JwtTokenProvider(ChallengeService challengeService) {
        this.challengeService = challengeService;
        this.secretKey = RandomStringUtils.randomAlphanumeric(10);
    }

    @PostConstruct
    protected void init() {
        secretKey = Base64.getEncoder().encodeToString(secretKey.getBytes());
    }

    public String createToken(String walletAddress) {
        Date now = new Date();

        return Jwts.builder()
                .setAudience(walletAddress)
                .setIssuedAt(now)
                .setSubject(challengeService.getChallenge(walletAddress))
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    public String resolveToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7, token.length());
        }
        return null;
    }

    /*
     * IMPORTANT /!\
     * Having the same validity duration for both challenge
     * and jwtoken can cause a problem. The latter should be
     * slightly longer (in minutes). In this case the challenge
     * is valid for 60 minutes while jwtoken stays valid
     * for 65 minutes.
     * 
     * Problem description:
     *  1) jwtString expires
     *  2) worker gets old challenge
     *  3) old challenge expires
     *  4) worker tries logging with old challenge
     */
    public boolean isValidToken(String token) {

        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(secretKey)
                    .parseClaimsJws(token).getBody();

            // check the expiration date
            Date now = new Date();
            long validityInMilliseconds = 1000L * 60 * 65; // 65 minutes
            Date tokenExpiryDate = new Date(claims.getIssuedAt().getTime() + validityInMilliseconds);

            // check the content of the challenge
            String walletAddress = claims.getAudience();
            boolean isChallengeCorrect = challengeService.getChallenge(walletAddress).equals(claims.getSubject());

            return tokenExpiryDate.after(now) && isChallengeCorrect;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Expired or invalid JWT token [exception:{}]", e.getMessage());
        }
        return false;
    }

    public String getWalletAddress(String token) {
        return Jwts.parser()
                .setSigningKey(secretKey)
                .parseClaimsJws(token).getBody().getAudience();
    }

    public String getWalletAddressFromBearerToken(String bearerToken) {
        String token = resolveToken(bearerToken);
        if (token != null && isValidToken(token)) {
            return getWalletAddress(token);
        }
        return "";
    }
}
