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
public class CoreConfigurationService {

    @Value("${cron.detector.contribution.unnotified.period}")
    private int unnotifiedContributionDetectorPeriod;

    @Value("${cron.detector.reveal.unnotified.period}")
    private int unnotifiedRevealDetectorPeriod;

}
