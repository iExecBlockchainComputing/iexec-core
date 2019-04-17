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
    private String protocol;

    @Value("${sms.host}")
    private String host;

    @Value("${sms.port}")
    private String port;

    public String getSmsURL() {
        return protocol + "://" + host + ":" + port;
    }
}
