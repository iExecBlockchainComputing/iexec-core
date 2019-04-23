package com.iexec.core.feign;

import com.iexec.core.configuration.ResultRepositoryConfiguration;
import com.iexec.core.configuration.SmsConfiguration;

import org.springframework.stereotype.Component;

import lombok.Getter;

/* 
 * This bean is a work around to let feign clients get URLs only after
 * spring context is initialized
 */
@Component
@Getter
public class FeignURLs {

    private String smsURL;
    private String resultRepositoryURL;

    public FeignURLs(ResultRepositoryConfiguration resultRepoConfig,
                     SmsConfiguration smsConfiguration) {
        this.resultRepositoryURL = resultRepoConfig.getResultRepositoryURL();
        this.smsURL = smsConfiguration.getSmsURL();
    }
}