package com.iexec.core.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import feign.FeignException;

@FeignClient(
    name = "SmsClient",
    url = "#{smsConfiguration.smsURL}"
)

public interface SmsClient {

    @PostMapping("/secret/{address}")
    String postSecret(@PathVariable(name = "address") String address) throws FeignException;

    @GetMapping("/secret/{address}")
    String getSecret(@PathVariable(name = "address") String address) throws FeignException;

    @GetMapping("/attestation/generate/{chainTaskId}")
    String generateSmsAttestation(@PathVariable(name = "chainTaskId") String chainTaskId) throws FeignException;

    @GetMapping("/attestation/verify/{chainTaskId}")
    String verifySmsAttestation(@PathVariable(name = "chainTaskId") String chainTaskId) throws FeignException;

    @GetMapping("/secure}")
    String secure(@RequestBody String auth) throws FeignException;
}