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

import com.iexec.common.utils.FileHelper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {
    private static final int KEY_SIZE = 32;

    private final ChallengeService challengeService;
    private final String secretKey;

    public JwtTokenProvider(ChallengeService challengeService, JwtConfig jwtConfig) throws IOException {
        this.challengeService = challengeService;
        this.secretKey = initKey(jwtConfig.getKeyPath());
    }

    /**
     * Reads the key used to sign JWT tokens from a file.
     * <p>
     * If the file does not exist, it is created.
     * <p>
     * There does not seem to be a best practice between hosting a key within a file or a database,
     * for an easier implementation, the key is currently hosted in a file for the moment.
     *
     * @param jwtKeyPath Path to the file hosting the key.
     * @return The key as a Base64-encoded {@link String}.
     * @throws IOException if an error occurs during file system interactions
     */
    private String initKey(String jwtKeyPath) throws IOException {
        Path path = Path.of(jwtKeyPath);
        if (!path.toFile().exists()) {
            SecureRandom random = new SecureRandom();
            byte[] seed = new byte[KEY_SIZE];
            random.nextBytes(seed);
            String content = Base64.getEncoder().encodeToString(seed);
            FileHelper.createFileWithContent(jwtKeyPath, content);
        }
        return Files.readString(path);
    }

    public String createToken(String walletAddress) {
        return Jwts.builder()
                .setAudience(walletAddress)
                .setIssuedAt(new Date())
                .setSubject(challengeService.getChallenge(walletAddress))
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    public String resolveToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
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
