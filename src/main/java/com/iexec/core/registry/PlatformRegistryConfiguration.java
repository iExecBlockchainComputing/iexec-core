package com.iexec.core.registry;

import lombok.Getter;
import org.hibernate.validator.constraints.URL;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class PlatformRegistryConfiguration {

    @URL
    @Value("${sms.scone}")
    private String sconeSms;

    @URL
    @Value("${sms.gramine}")
    private String gramineSms;
    
}
