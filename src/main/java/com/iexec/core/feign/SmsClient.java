package com.iexec.core.feign;


import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
        name = "SmsClient",
        url = "#{feignURLs.smsURL}"
)
public interface SmsClient {

    @PostMapping("/executions/challenge/generate/{chainTaskId}")
    String generateEnclaveChallenge(@PathVariable("chainTaskId") String chainTaskId)
            throws FeignException;
}