package com.iexec.core.worker;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity registerWorker(@RequestParam(name = "workerName") String workerName){
        return ok(workerService.addWorker(workerName));
    }



}
