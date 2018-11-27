package com.iexec.core.replicate;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

interface ReplicatesRepository extends MongoRepository<ReplicatesList, String> {

    Optional<ReplicatesList> findByChainTaskId(String chainTaskId);
}
