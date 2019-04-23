package com.iexec.core.feign;

import com.iexec.common.sms.SmsEnclaveChallengeResponse;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import feign.FeignException;

@FeignClient(
    name = "SmsClient",
    url = "#{feignURLs.smsURL}"
)

public interface SmsClient {

    @GetMapping("/attestation/generate/{chainTaskId}")
    SmsEnclaveChallengeResponse generateEnclaveChallenge(@PathVariable("chainTaskId") String chainTaskId)
            throws FeignException;
}