package com.iexec.core.configuration;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class CronConfiguration {

    @Value("${cron.deal.replay}")
    private int dealReplay;

    @Value("${cron.detector.worker-lost}")
    private int workerLost;

    @Value("${cron.detector.chain.unstarted-tx}")
    private int unstartedTx;

    @Value("${cron.detector.chain.initialize}")
    private int initialize;

    @Value("${cron.detector.chain.contribute}")
    private int contribute;

    @Value("${cron.detector.chain.reveal}")
    private int reveal;

    @Value("${cron.detector.chain.finalize}")
    private int finalize;

    @Value("${cron.detector.chain.final-deadline}")
    private int finalDeadline;

    @Value("${cron.detector.timeout.contribute}")
    private int contributeTimeout;

    @Value("${cron.detector.timeout.reveal}")
    private int revealTimeout;

    @Value("${cron.detector.timeout.result-upload}")
    private int resultUploadTimeout;  
}
