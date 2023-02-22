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

import io.jsonwebtoken.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final long TOKEN_VALIDITY_DURATION = 1000L * 60 * 60;
    private final ChallengeService challengeService;
    private final ConcurrentHashMap<String, String> jwTokensMap = new ConcurrentHashMap<>();
    private final String secretKey;

    public JwtTokenProvider(ChallengeService challengeService) {
        this.challengeService = challengeService;
        SecureRandom secureRandom = new SecureRandom();
        byte[] seed = new byte[32];
        secureRandom.nextBytes(seed);
        this.secretKey = Base64.getEncoder().encodeToString(seed);
    }

    /**
     * Creates a signed JWT with expiration date for a given ethereum address.
     * <p>
     * The token is cached until it expires.
     * @param walletAddress worker address for which the token is created
     * @return A signed JWT for a given ethereum address
     */
    public String createToken(String walletAddress) {
        // Do not try to check if JWT is valid here, it introduces too many questions on challenge validity,
        // concurrency of operations and potential race conditions.
        // When a token is presented, scheduler answers UNAUTHORIZED if the JWT is invalid and purges caches
        // on expiration of a known JWT.
        return jwTokensMap.computeIfAbsent(walletAddress, address -> {
            Date now = new Date();
            return Jwts.builder()
                    .setAudience(address)
                    .setIssuedAt(now)
                    .setExpiration(new Date(now.getTime() + TOKEN_VALIDITY_DURATION))
                    .setSubject(challengeService.getChallenge(address))
                    .signWith(SignatureAlgorithm.HS256, secretKey)
                    .compact();
        });
    }

    public String resolveToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return null;
    }

    /**
     * Checks if a JWT is valid.
     * <p>
     * A valid JWT must:
     * <ul>
     * <li>be signed with the scheduler private key
     * <li>not be expired
     * <li>contain valid address and challenge values respectively in audience and subject claims
     * <p>
     * On expiration, the JWT and the challenge are removed from their respective cache at the condition
     * that each cache still holds the expired value. An expired JWT will enforce the creation of a new challenge
     * and a new JWT on next login (get new challenge -> sign challenge -> check signed challenge -> create new JWT).
     * <p>
     * All other invalid JWTs will produce a retrieval from cache on next login.
     *
     * @param token The token whose validity must be established
     * @return true if the token is valid, false otherwise
     */
    public boolean isValidToken(String token) {
        try {
            if (!jwTokensMap.containsValue(token)) {
                throw new JwtException("Unknown JWT");
            }
            Claims claims = Jwts.parser()
                    .setSigningKey(secretKey)
                    .parseClaimsJws(token)
                    .getBody();

            // check the content of the challenge
            String walletAddress = claims.getAudience();
            return challengeService.getChallenge(walletAddress).equals(claims.getSubject());
        } catch (ExpiredJwtException e) {
            log.warn("JWT has expired");
            String walletAddress = e.getClaims().getAudience();
            jwTokensMap.remove(walletAddress, token);
            challengeService.removeChallenge(walletAddress, e.getClaims().getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT is invalid [{}: {}]", e.getClass().getSimpleName(), e.getMessage());
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
