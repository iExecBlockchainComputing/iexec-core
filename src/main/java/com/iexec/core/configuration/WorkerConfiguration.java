package com.iexec.core.configuration;

import lombok.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
public class WorkerConfiguration {

    @Value("${workers.askForReplicatePeriod}")
    private long askForReplicatePeriod;
}
