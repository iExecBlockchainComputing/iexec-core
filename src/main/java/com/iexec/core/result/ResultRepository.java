package com.iexec.core.result;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

interface ResultRepository extends MongoRepository<Result, String> {

    Optional<Result> findById(String id);

    List<Result> findByChainTaskId(String chainTaskId);

}
