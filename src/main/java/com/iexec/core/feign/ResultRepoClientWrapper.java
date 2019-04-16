package com.iexec.core.feign;

import java.util.Optional;

import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.core.chain.ChainConfig;

import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class ResultRepoClientWrapper {

    private ChainConfig chainConfig;
    private ResultRepoClient resultClient;

    public ResultRepoClientWrapper(ChainConfig chainConfig, ResultRepoClient resultClient) {
        this.chainConfig = chainConfig;
        this.resultClient = resultClient;
    }

    @Retryable (value = FeignException.class)
    public Optional<Eip712Challenge> getChallenge() {
        return Optional.of(resultClient.getChallenge(chainConfig.getChainId()));
    }

    @Recover
    public Optional<Eip712Challenge> getChallenge(FeignException e) {
        log.error("Failed to get challenge from resultRepo [attempts:3]");
        e.printStackTrace();
        return Optional.empty();
    }

    @Retryable (value = FeignException.class)
    public boolean isResultUploaded(String authorizationToken, String chainTaskId) {
        return resultClient.isResultUploaded(authorizationToken, chainTaskId)
                .getStatusCode()
                .is2xxSuccessful();
    }

    @Recover
    public boolean isResultUploaded(FeignException e, String authorizationToken, String chainTaskId) {
        return false;
    }
}