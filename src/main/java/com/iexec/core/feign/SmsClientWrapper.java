package com.iexec.core.feign;

import com.iexec.common.sms.SmsEnclaveChallengeResponse;

import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class SmsClientWrapper {

    private SmsClient smsClient;

    public SmsClientWrapper(SmsClient smsClient) {
        this.smsClient = smsClient;
    }

    @Retryable (value = FeignException.class)
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
        log.error("Failed to get enclaveChallenge from SMS even after retrying [chainTaskId:{}, attempts:3]", chainTaskId);
        e.printStackTrace();
        return "";
    }
}