/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.core.task;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.Date;
import java.util.List;
import java.util.Optional;

interface TaskRepository extends MongoRepository<Task, String> {

    Optional<Task> findByChainTaskId(String id);

    Optional<Task> findByChainDealIdAndTaskIndex(String chainDealId, int taskIndex);

    @Query("{ 'chainTaskId': {$in: ?0} }")
    List<Task> findByChainTaskId(List<String> ids);

    @Query("{ 'id': {$in: ?0} }")
    List<Task> findById(List<String> ids);

    List<Task> findByCurrentStatus(TaskStatus status);

    @Query("{ 'currentStatus': {$in: ?0} }")
    List<Task> findByCurrentStatus(List<TaskStatus> statuses);

    @Query("{ 'currentStatus': {$nin: ?0} }")
    List<Task> findByCurrentStatusNotIn(List<TaskStatus> statuses);

    @Query(value = "{ finalDeadline: {$lt : ?0} }", fields = "{ chainTaskId: true }")
    List<Task> findChainTaskIdsByFinalDeadlineBefore(Date date);

    List<Task> findByCurrentStatusInAndContributionDeadlineAfter(List<TaskStatus> status, Date date);
}
