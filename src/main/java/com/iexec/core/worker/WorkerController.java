package com.iexec.core.worker;


import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerConfigurationModel;
import com.iexec.common.core.WorkerInterface;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.Optional;

import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

@RestController
public class WorkerController implements WorkerInterface {

    private WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @Override
    public ResponseEntity<String> getCoreVersion() {
        return ResponseEntity.ok().build();
    }

    public ResponseEntity<String> ping(String workerName) {
        Optional<Worker> optional = workerService.updateLastAlive(workerName);
        return optional.
                <ResponseEntity>map(ResponseEntity::ok)
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    @Override
    public ResponseEntity registerWorker(WorkerConfigurationModel model) {

        Worker worker = Worker.builder()
                .name(model.getName())
                .os(model.getOs())
                .cpu(model.getCpu())
                .cpuNb(model.getCpuNb())
                .lastAliveDate(new Date())
                .build();

        return ok(workerService.addWorker(worker));
    }

    @Override
    public ResponseEntity<PublicConfiguration> getPublicConfiguration() {
        PublicConfiguration config = PublicConfiguration.builder()
                .blockchainAddress("dummyAddress")
                .build();

        return ok(config);
    }


}
