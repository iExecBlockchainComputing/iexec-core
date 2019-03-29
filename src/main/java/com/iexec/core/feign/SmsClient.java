package com.iexec.core.feign;

import com.iexec.common.sms.SmsEnclaveChallenge;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import feign.FeignException;

@FeignClient(
    name = "SmsClient",
    url = "#{smsConfiguration.smsURL}"
)

public interface SmsClient {

    @GetMapping("/attestation/generate/{chainTaskId}")
    SmsEnclaveChallenge generateEnclaveChallenge(@PathVariable(name = "chainTaskId") String chainTaskId) throws FeignException;
}