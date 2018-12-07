package com.iexec.core.configuration;

import org.springframework.data.mongodb.repository.MongoRepository;

interface ConfigurationRepository extends MongoRepository<Configuration, String> {

}
