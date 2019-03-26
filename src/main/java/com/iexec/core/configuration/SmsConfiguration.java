package com.iexec.core.configuration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SmsConfiguration {

    @Value("${sms.protocol}")
    private String smsProtocol;

    @Value("${sms.ip}")
    private String smsIp;

    @Value("${sms.port}")
    private String smsPort;

    public String getSmsURL() {
        return smsProtocol + "://" + smsIp + ":" + smsProtocol;
    }
}
