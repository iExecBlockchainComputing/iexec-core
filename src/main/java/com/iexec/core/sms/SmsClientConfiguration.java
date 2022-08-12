package com.iexec.core.sms;

import com.iexec.core.chain.IexecHubService;
import com.iexec.sms.api.SmsClientProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SmsClientConfiguration {
    private final IexecHubService iexecHubService;

    public SmsClientConfiguration(IexecHubService iexecHubService) {
        this.iexecHubService = iexecHubService;
    }

    @Bean
    SmsClientProvider smsClientProvider() {
        return new SmsClientProvider(iexecHubService);
    }
}
