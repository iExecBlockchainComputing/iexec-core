package com.iexec.core.tasks;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

interface TaskRepository extends MongoRepository<Task, String> {

    Optional<Task> findById(String id);

    List<Task> findByCurrentStatus(TaskStatus status);

}
