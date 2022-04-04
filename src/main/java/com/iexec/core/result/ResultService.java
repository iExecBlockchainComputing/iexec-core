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

package com.iexec.core.result;

import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.common.result.eip712.Eip712ChallengeUtils;
import com.iexec.core.chain.ChainConfig;
import com.iexec.core.chain.CredentialsService;
import com.iexec.resultproxy.api.ResultProxyClient;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.web3j.crypto.ECKeyPair;

import java.util.Optional;


@Slf4j
@Service
public class ResultService {

    private final ChainConfig chainConfig;
    private final CredentialsService credentialsService;
    private final ResultProxyClient resultProxyClient;

    public ResultService(ChainConfig chainConfig, CredentialsService credentialsService,
                         ResultProxyClient resultProxyClient) {
        this.chainConfig = chainConfig;
        this.credentialsService = credentialsService;
        this.resultProxyClient = resultProxyClient;
    }

    @Retryable(value = FeignException.class)
    public boolean isResultUploaded(String chainTaskId) {
        String resultProxyToken = getResultProxyToken();
        if (resultProxyToken.isEmpty()) {
            log.error("isResultUploaded failed (getResultProxyToken) [chainTaskId:{}]", chainTaskId);
            return false;
        }

        resultProxyClient.isResultUploaded(resultProxyToken, chainTaskId);
        return true;
    }

    @Recover
    private boolean isResultUploaded(FeignException e, String chainTaskId) {
        log.error("Cant check isResultUploaded after multiple retries [chainTaskId:{}]", chainTaskId, e);
        return false;
    }

    // TODO Move this to iexec-result-proxy-library since widely used by all iexec services
    private String getResultProxyToken() {
        Optional<Eip712Challenge> oEip712Challenge = getChallenge();
        if (oEip712Challenge.isEmpty()) {
            return "";
        }

        Eip712Challenge eip712Challenge = oEip712Challenge.get();
        ECKeyPair ecKeyPair = credentialsService.getCredentials().getEcKeyPair();
        String walletAddress = credentialsService.getCredentials().getAddress();

        String signedEip712Challenge = Eip712ChallengeUtils.buildAuthorizationToken(
                eip712Challenge,
                walletAddress,
                ecKeyPair);

        if (signedEip712Challenge.isEmpty()) {
            return "";
        }

        String token = login(signedEip712Challenge);

        if (token.isEmpty()) {
            return "";
        }
        return token;
    }

    private Optional<Eip712Challenge> getChallenge() {
        try {
            Eip712Challenge challenge = resultProxyClient.getChallenge(chainConfig.getChainId());
            return Optional.of(challenge);
        } catch (RuntimeException e) {
            log.error("Failed to get challenge", e);
            return Optional.empty();
        }
    }

    private String login(String token) {
        try {
            return resultProxyClient.login(chainConfig.getChainId(), token);
        } catch (RuntimeException e) {
            return "";
        }
    }
}
