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
public class CoreConfiguration {

    @Value("${detector.contribution.unnotified.period}")
    private int unnotifiedContributionDetectorPeriod;

}
