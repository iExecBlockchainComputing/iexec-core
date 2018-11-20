package com.iexec.core.worker;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Date;
import java.util.List;
import java.util.Optional;

interface WorkerRepository extends MongoRepository<Worker, String> {

    Optional<Worker> findByWalletAddress(String walletAddress);

    @Query("{'lastAliveDate': {$lt: ?0}}")
    List<Worker> findByLastAliveDateBefore(Date date);

    @Query("{'lastAliveDate': {$gt: ?0}}")
    List<Worker> findByLastAliveDateAfter(Date date);
}
