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
    private String resultRepoProtocol;

    @Value("${resultRepository.ip}")
    private String resultRepoIP;

    @Value("${resultRepository.port}")
    private String resultRepoPort;

    public String getResultRepositoryURL() {
        return resultRepoProtocol + "://" + resultRepoIP + ":" + resultRepoPort;
    }
}
