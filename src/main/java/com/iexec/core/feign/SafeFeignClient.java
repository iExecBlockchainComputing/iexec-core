package com.iexec.core.feign;

import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.common.sms.SmsEnclaveChallengeResponse;
import com.iexec.core.chain.ChainConfig;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class SafeFeignClient {

    private static final int BACKOFF = 2000; // 2s
    private static final int MAX_ATTEMPS = 5;

    private ChainConfig chainConfig;
    private ResultClient resultClient;
    private SmsClient smsClient;

    public SafeFeignClient(ChainConfig chainConfig,
                           ResultClient resultClient,
                           SmsClient smsClient) {
        this.chainConfig = chainConfig;
        this.resultClient = resultClient;
        this.smsClient = smsClient;
    }

    @Retryable (value = {FeignException.class},
                maxAttempts = MAX_ATTEMPS,
                backoff = @Backoff(delay = BACKOFF))
    public Eip712Challenge getResultRepoChallenge() {
        return resultClient.getChallenge(this.chainConfig.getChainId());
    }

    @Recover
    public Eip712Challenge getResultRepoChallenge(FeignException e) {
        log.error("Failed to get challenge from resultRepo [attempts:{}]", MAX_ATTEMPS);
        return null;
    }

    @Retryable (value = {FeignException.class},
                maxAttempts = MAX_ATTEMPS,
                backoff = @Backoff(delay = BACKOFF))
    public String generateEnclaveChallenge(String chainTaskId) {
        SmsEnclaveChallengeResponse smsResponse = smsClient.generateEnclaveChallenge(chainTaskId);

        if (smsResponse == null || !smsResponse.isOk() || smsResponse.getData() == null) {
            log.error("An error occured while getting enclaveChallenge [chainTaskId:{}, erroMsg:{}]",
                    chainTaskId, smsResponse.getErrorMessage());
            return "";
        }

        return smsResponse.getData().getAddress();
    }

    @Recover
    public String generateEnclaveChallenge(FeignException e, String chainTaskId) {
        log.error("Failed to get enclaveChallenge from SMS [chainTaskId:{}, attempts:{}]", chainTaskId, MAX_ATTEMPS);
        e.printStackTrace();
        return "";
    }

}