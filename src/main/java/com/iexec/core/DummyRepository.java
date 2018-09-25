package com.iexec.core;


import com.iexec.common.dummy.DummyClass;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DummyRepository extends MongoRepository<DummyClass, String> {

    DummyClass findByDummyField(String dummyField);

}