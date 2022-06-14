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

import com.iexec.common.chain.eip712.EIP712Domain;
import com.iexec.common.chain.eip712.entity.EIP712Challenge;
import com.iexec.core.chain.ChainConfig;
import com.iexec.core.chain.CredentialsService;
import com.iexec.resultproxy.api.ResultProxyClient;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Objects;
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
        Optional<EIP712Challenge> oEip712Challenge = getChallenge();
        if (oEip712Challenge.isEmpty()) {
            return "";
        }

        EIP712Challenge eip712Challenge = oEip712Challenge.get();

        final EIP712Domain domain = eip712Challenge.getDomain();
        if (domain == null) {
            log.error("Couldn't get a correct domain from EIP712Challenge " +
                            "retrieved from Result Proxy [eip712Challenge:{}]",
                    eip712Challenge);
            return "";
        }

        final String expectedDomainName = "iExec Result Proxy";
        final String actualDomainName = domain.getName();
        if (!Objects.equals(actualDomainName, expectedDomainName)) {
            log.error("Domain name does not match expected name" +
                            " [expected:{}, actual:{}]",
                    expectedDomainName, actualDomainName);
            return "";
        }

        final Integer chainId = chainConfig.getChainId();
        final long domainChainId = domain.getChainId();
        if (!Objects.equals(domainChainId, chainId.longValue())) {
            log.error("Domain chain id does not match expected chain id" +
                            " [expected:{}, actual:{}]",
                    chainId, domainChainId);
            return "";
        }
        String signedEip712Challenge = credentialsService.signEIP712EntityAndBuildToken(eip712Challenge);

        if (signedEip712Challenge.isEmpty()) {
            return "";
        }

        String token = login(signedEip712Challenge);

        if (token.isEmpty()) {
            return "";
        }
        return token;
    }

    private Optional<EIP712Challenge> getChallenge() {
        try {
            EIP712Challenge challenge = resultProxyClient.getChallenge(chainConfig.getChainId());
            if (challenge != null) {
                return Optional.of(challenge);
            }
        } catch (RuntimeException e) {
            log.error("Failed to get challenge", e);
        }
        return Optional.empty();
    }

    private String login(String token) {
        try {
            return resultProxyClient.login(chainConfig.getChainId(), token);
        } catch (RuntimeException e) {
            log.error("Login failed", e);
        }
        return "";
    }
}
