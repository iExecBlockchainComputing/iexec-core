package com.iexec.core.feign;


import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
        name = "SmsClient",
        url = "#{feignURLs.smsURL}"
)
public interface SmsClient {

    @PostMapping("/tee/challenges/{chainTaskId}")
    String generateTeeChallenge(@PathVariable("chainTaskId") String chainTaskId);

}