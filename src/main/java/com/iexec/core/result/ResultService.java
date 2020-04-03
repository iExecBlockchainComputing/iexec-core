package com.iexec.core.result;

import java.util.Optional;

import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.core.chain.ChainConfig;
import com.iexec.core.feign.ResultRepoClient;

import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class ResultService {

    private ChainConfig chainConfig;
    private ResultRepoClient resultRepoClient;

    public ResultService(ChainConfig chainConfig, ResultRepoClient resultRepoClient) {
        this.chainConfig = chainConfig;
        this.resultRepoClient = resultRepoClient;
    }

    @Retryable (value = FeignException.class)
    public Optional<Eip712Challenge> getChallenge() {
        return Optional.of(resultRepoClient.getChallenge(chainConfig.getChainId()));
    }

    @Recover
    public Optional<Eip712Challenge> getChallenge(FeignException e) {
        log.error("Failed to get challenge from resultRepo [attempts:3]");
        e.printStackTrace();
        return Optional.empty();
    }

    @Retryable (value = FeignException.class)
    public boolean isResultUploaded(String authorizationToken, String chainTaskId) {
        return resultRepoClient.isResultUploaded(authorizationToken, chainTaskId)
                .getStatusCode()
                .is2xxSuccessful();
    }

    @Recover
    public boolean isResultUploaded(FeignException e, String authorizationToken, String chainTaskId) {
        return false;
    }
}