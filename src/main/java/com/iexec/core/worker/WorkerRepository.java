package com.iexec.core.worker;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

interface WorkerRepository extends MongoRepository<Worker, String> {

    Optional<Worker> findByName(String name);

}
