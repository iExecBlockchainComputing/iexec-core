package com.iexec.core.task;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

interface TaskRepository extends MongoRepository<Task, String> {

    Optional<Task> findByChainTaskId(String id);

    List<Task> findByChainDealIdAndTaskIndex(String chainDealId, int taskIndex);

    @Query("{ 'chainTaskId': {$in: ?0} }")
    List<Task> findByChainTaskId(List<String> ids);

    @Query("{ 'id': {$in: ?0} }")
    List<Task> findById(List<String> ids);

    List<Task> findByCurrentStatus(TaskStatus status);

    @Query("{ 'currentStatus': {$in: ?0} }")
    List<Task> findByCurrentStatus(List<TaskStatus> statuses);

    @Query("{ 'currentStatus': {$nin: ?0} }")
    List<Task> findByCurrentStatusNotIn(List<TaskStatus> statuses);    
}
