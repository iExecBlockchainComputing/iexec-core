package com.iexec.core.feign;

import com.iexec.common.result.eip712.Eip712Challenge;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import feign.FeignException;

@FeignClient(
    name = "ResultClient",
    url = "#{feignURLs.resultRepositoryURL}"
)

public interface ResultRepoClient {

    @GetMapping("/results/challenge?chainId={chainId}")
    Eip712Challenge getChallenge(@RequestParam("chainId") Integer chainId) throws FeignException;

    @RequestMapping(value =  "/results/{chainTaskId}", method = RequestMethod.HEAD)
    ResponseEntity<String> isResultUploaded(@RequestHeader("Authorization") String authorizationToken,
                                            @RequestParam("chainTaskId") String chainTaskId);

}