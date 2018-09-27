package com.iexec.core.worker;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkerController {

    @PostMapping("/workers/ping")
    public void ping(@RequestParam(name="workerName") String workerName) {
        System.out.println("Worker " + workerName + " has just pinged");
    }

}
