package com.iexec.core.worker;

import com.iexec.common.config.WorkerConfigurationModel;
import com.iexec.common.config.PublicConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.Optional;

import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

@RestController
public class WorkerController {

    private WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @PostMapping("/workers/ping")
    public ResponseEntity ping(@RequestParam(name = "workerName") String workerName) {
        Optional<Worker> optional = workerService.updateLastAlive(workerName);
        return optional.
                <ResponseEntity>map(ResponseEntity::ok)
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    @PostMapping("/workers/register")
    public ResponseEntity registerWorker(@RequestBody WorkerConfigurationModel model){

        Worker worker = Worker.builder()
                .name(model.getName())
                .os(model.getOs())
                .cpu(model.getCpu())
                .cpuNb(model.getCpuNb())
                .lastAliveDate(new Date())
                .build();

        return ok(workerService.addWorker(worker));
    }

    @GetMapping("/workers/config")
    public ResponseEntity getCoreConfiguration(){
        PublicConfiguration config = PublicConfiguration.builder()
                .blockchainAddress("dummyAddress")
                .build();

        return ok(config);
    }



}
