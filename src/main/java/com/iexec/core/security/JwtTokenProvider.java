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
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class JwtTokenProvider {

    private static final long TOKEN_VALIDITY_DURATION = 1000L * 60 * 60;
    private final ConcurrentHashMap<String, String> jwTokensMap = new ConcurrentHashMap<>();
    private final String applicationId;
    private final String secretKey;

    public JwtTokenProvider(BuildProperties buildProperties) {
        this.applicationId = "iExec Scheduler v" + buildProperties.getVersion();
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
    public String getOrCreateToken(String walletAddress) {
        // Do not try to check if JWT is valid here, it introduces too many questions on challenge validity,
        // concurrency of operations and potential race conditions.
        // When a token is presented, scheduler answers UNAUTHORIZED if the JWT is invalid and purges caches
        // on expiration of a known JWT.
        return jwTokensMap.computeIfAbsent(walletAddress, address -> {
            Date now = new Date();
            return Jwts.builder()
                    .setAudience(applicationId)
                    .setIssuedAt(now)
                    .setExpiration(new Date(now.getTime() + TOKEN_VALIDITY_DURATION))
                    .setSubject(address)
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
     * <li>contain the scheduler application ID in the audience claim
     * <p>
     * An invalid JWT will return an UNAUTHORIZED status and require to perform a full authentication loop
     * with a new signed challenge (get new challenge -> sign challenge -> check signed challenge -> get or create JWT).
     * <p>
     * If the JWT was expired, the cache will have been purged and a new JWT will be generated.
     * For other invalid JWTs, the cached JWT will be returned on next login.
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
            return applicationId.equals(claims.getAudience());
        } catch (ExpiredJwtException e) {
            log.warn("JWT has expired");
            String walletAddress = e.getClaims().getSubject();
            jwTokensMap.remove(walletAddress, token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT is invalid [{}: {}]", e.getClass().getSimpleName(), e.getMessage());
        }
        return false;
    }

    public String getWalletAddress(String token) {
        return Jwts.parser()
                .setSigningKey(secretKey)
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public String getWalletAddressFromBearerToken(String bearerToken) {
        String token = resolveToken(bearerToken);
        if (token != null && isValidToken(token)) {
            return getWalletAddress(token);
        }
        return "";
    }
}
