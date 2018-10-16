package com.iexec.core.task;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

interface TaskRepository extends MongoRepository<Task, String> {

    Optional<Task> findById(String id);

    List<Task> findByCurrentStatus(TaskStatus status);

    @Query("{ 'currentStatus': {$in: ?0} }")
    List<Task> findByCurrentStatus(List<TaskStatus> status);

}
