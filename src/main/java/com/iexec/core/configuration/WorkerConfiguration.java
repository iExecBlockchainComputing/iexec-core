package com.iexec.core.configuration;

import lombok.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Setter
@Component
public class WorkerConfiguration {

    @Value("${workers.askForReplicatePeriod}")
    private long askForReplicatePeriod;

    @Value("${workers.requiredWorkerVersion}")
    private String requiredWorkerVersion;

    @Value("${workers.whitelist}")
    private String[] whitelist;

    // getters are overridden since the whitelist should return a list, not an array
    public long getAskForReplicatePeriod() {
        return askForReplicatePeriod;
    }

    public String getRequiredWorkerVersion() {
        return requiredWorkerVersion;
    }

    public List<String> getWhitelist() {
        return Arrays.asList(whitelist);
    }
}
