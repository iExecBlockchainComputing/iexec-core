package com.iexec.core.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

@Slf4j
@Service
public class WorkerService {

    private  WorkerRepository workerRepository;

    public WorkerService(WorkerRepository workerRepository) {
        this.workerRepository = workerRepository;
    }

    public Worker addWorker(String name) {
        // TODO: check if the worker already exists or not
        Worker worker = workerRepository.save(Worker.builder()
                .name(name)
                .lastAliveDate(new Date())
                .build());
        log.info("A new worker has been registered [workerId:{}]", worker.getId());
        return worker;
    }

    public Optional<Worker> updateLastAlive(String name) {
        Optional<Worker> optional = workerRepository.findByName(name);
        if (optional.isPresent()) {
            Worker worker = optional.get();
            worker.setLastAliveDate(new Date());
            workerRepository.save(worker);
            return Optional.of(worker);
        }

        return Optional.empty();
    }

}
