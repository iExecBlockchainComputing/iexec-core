package com.iexec.core.sms;

import com.iexec.common.utils.BytesUtils;
import com.iexec.core.feign.SmsClientWrapper;

import org.springframework.stereotype.Service;

@Service
public class SmsService {

    private SmsClientWrapper smsClientWrapper;

    public SmsService(SmsClientWrapper smsClientWrapper) {
        this.smsClientWrapper = smsClientWrapper;
    }

    public String getEnclaveChallenge(String chainTaskId, boolean isTeeEnabled) {
        return isTeeEnabled
            ? smsClientWrapper.generateEnclaveChallenge(chainTaskId)
            : BytesUtils.EMPTY_ADDRESS;
    }
}