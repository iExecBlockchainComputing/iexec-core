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
public class ResultRepositoryConfiguration {

    @Value("${resultRepository.protocol}")
    private String protocol;

    @Value("${resultRepository.host}")
    private String host;

    @Value("${resultRepository.port}")
    private String port;

    public String getResultRepositoryURL() {
        return protocol + "://" + host + ":" + port;
    }
}
