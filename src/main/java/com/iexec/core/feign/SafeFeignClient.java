package com.iexec.core.feign;

import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.core.chain.ChainConfig;

import org.springframework.stereotype.Service;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class SafeFeignClient {

    private ChainConfig chainConfig;
    private ResultClient resultClient;
    private SmsClient smsClient;

    public SafeFeignClient(ChainConfig chainConfig) {
        this.chainConfig = chainConfig;
    }

    public Eip712Challenge getResultRepoChallenge() {
        for (int i : new int[]{1, 2, 3}) {
            try {
                return resultClient.getChallenge(this.chainConfig.getChainId());
            } catch (FeignException e) {
                log.error("Failed to get resultRepo challenge, will retry [instance:{}, retry:{}]", i);
            }
        }

        return null;    
    }

    public String generateEnclaveChallenge(String chainTaskId) {
        try {
            return smsClient.generateEnclaveChallenge(chainTaskId).getAddress();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}