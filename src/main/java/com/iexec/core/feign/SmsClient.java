package com.iexec.core.feign;


import com.iexec.common.security.Attestation;
import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
        name = "SmsClient",
        url = "#{feignURLs.smsURL}"
)
public interface SmsClient {

    @PostMapping("/attestations/generate/{chainTaskId}")
    Attestation generateEnclaveChallenge(@PathVariable("chainTaskId") String chainTaskId)
            throws FeignException;
}