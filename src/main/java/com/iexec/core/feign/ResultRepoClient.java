package com.iexec.core.feign;

import com.iexec.common.result.eip712.Eip712Challenge;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import feign.FeignException;

@FeignClient(
    name = "ResultClient",
    url = "#{feignURLs.resultRepositoryURL}"
)

public interface ResultRepoClient {

    @GetMapping("/results/challenge?chainId={chainId}")
    ResponseEntity<Eip712Challenge> getChallenge(@RequestParam("chainId") Integer chainId);

    @PostMapping(value = "/results/login")
    ResponseEntity<String> login(@RequestParam(name = "chainId") Integer chainId,
                                        @RequestBody String token);

    @RequestMapping(value =  "/results/{chainTaskId}", method = RequestMethod.HEAD)
    ResponseEntity<String> isResultUploaded(@RequestHeader("Authorization") String authorizationToken,
                                            @RequestParam("chainTaskId") String chainTaskId) throws FeignException;

}