package com.iexec.core.metric;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.ResponseEntity.ok;

@Slf4j
@RestController
public class MetricController {


    private MetricService metricService;

    public MetricController(MetricService metricService) {
        this.metricService = metricService;
    }


    @RequestMapping(method = RequestMethod.GET, path = "/metrics")
    public ResponseEntity<PlatformMetric> getAvailableCpu() {
        return ok(metricService.getPlatformMetrics());
    }

}
