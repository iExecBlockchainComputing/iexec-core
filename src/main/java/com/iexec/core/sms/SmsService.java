package com.iexec.core.sms;

import com.iexec.common.utils.BytesUtils;
import com.iexec.core.feign.SafeFeignClient;

import org.springframework.stereotype.Service;

@Service
public class SmsService {

    private SafeFeignClient safeFeignClient;

    public SmsService(SafeFeignClient safeFeignClient) {
        this.safeFeignClient = safeFeignClient;
    }

    public String getEnclaveChallenge(String chainTaskId, boolean isTeeEnabled) {
        return isTeeEnabled ? safeFeignClient.generateEnclaveChallenge(chainTaskId) : BytesUtils.EMPTY_ADDRESS;
    }
}