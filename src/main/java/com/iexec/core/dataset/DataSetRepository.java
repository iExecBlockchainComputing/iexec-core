package com.iexec.core.dataset;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

interface DataSetRepository extends MongoRepository<DataSet, String> {

    Optional<DataSet> findByHash(String checksum);

}
