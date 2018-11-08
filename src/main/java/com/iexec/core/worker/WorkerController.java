package com.iexec.core.worker;


import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerConfigurationModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Optional;

import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

@Slf4j
@RestController
public class WorkerController {

    @Value("${chain.publicAddress}")
    private String chainAddress;

    @Value("${chain.hubAddress}")
    private String hubAddress;

    @Value("${chain.poolAddress}")
    private String poolAddress;

    private WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @RequestMapping(method = RequestMethod.POST, path = "/workers/ping")
    public ResponseEntity ping(@RequestParam(name = "workerName") String workerName) {
        Optional<Worker> optional = workerService.updateLastAlive(workerName);
        return optional.
                <ResponseEntity>map(ResponseEntity::ok)
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    @RequestMapping(method = RequestMethod.POST, path = "/workers/register")
    public ResponseEntity registerWorker(@RequestBody WorkerConfigurationModel model) {

        Worker worker = Worker.builder()
                .name(model.getName())
                .os(model.getOs())
                .cpu(model.getCpu())
                .cpuNb(model.getCpuNb())
                .lastAliveDate(new Date())
                .build();

        Worker savedWorker = workerService.addWorker(worker);
        log.info("Worker has been registered [worker:{}]", savedWorker);
        return ok(savedWorker);
    }

    @RequestMapping(method = RequestMethod.GET, path = "/workers/config")
    public ResponseEntity getPublicConfiguration() {
        PublicConfiguration config = PublicConfiguration.builder()
                .blockchainURL(chainAddress)
                .iexecHubAddress(hubAddress)
                .workerPoolAddress(poolAddress)
                .build();

        return ok(config);
    }


}
