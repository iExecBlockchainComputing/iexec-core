package com.iexec.core.worker;


import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerConfigurationModel;
import com.iexec.core.chain.ChainConfig;
import com.iexec.core.chain.CredentialsService;
import lombok.extern.slf4j.Slf4j;
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

    private WorkerService workerService;
    private ChainConfig chainConfig;
    private CredentialsService credentialsService;

    public WorkerController(WorkerService workerService,
                            ChainConfig chainConfig,
                            CredentialsService credentialsService) {
        this.workerService = workerService;
        this.chainConfig = chainConfig;
        this.credentialsService = credentialsService;
    }

    @RequestMapping(method = RequestMethod.POST, path = "/workers/ping")
    public ResponseEntity ping(@RequestParam(name = "walletAddress") String walletAddress) {
        Optional<Worker> optional = workerService.updateLastAlive(walletAddress);
        return optional.
                <ResponseEntity>map(ResponseEntity::ok)
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    @RequestMapping(method = RequestMethod.POST, path = "/workers/register")
    public ResponseEntity registerWorker(@RequestBody WorkerConfigurationModel model) {

        Worker worker = Worker.builder()
                .name(model.getName())
                .walletAddress(model.getWalletAddress())
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
                .blockchainURL(chainConfig.getPublicChainAddress())
                .iexecHubAddress(chainConfig.getHubAddress())
                .workerPoolAddress(chainConfig.getPoolAddress())
                .schedulerPublicAddress(credentialsService.getCredentials().getAddress())
                .build();

        return ok(config);
    }


}
