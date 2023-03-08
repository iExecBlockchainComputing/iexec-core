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

import org.springframework.data.domain.Sort;
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

    @Query("{ 'currentStatus': {$in: ?0} }")
    List<Task> findByCurrentStatus(List<TaskStatus> statuses, Sort sort);

    /**
     * Retrieves the prioritized task matching with given criteria:
     * <ul>
     *     <li>Task is in one of given {@code statuses};</li>
     *     <li>Task has not given {@code excludedTag}
     *          - this is mainly used to exclude TEE tasks;
     *     </li>
     *     <li>Chain task ID is not one of the given {@code excludedChainTaskIds};</li>
     *     <li>Tasks are prioritized according to the {@code sort} parameter.</li>
     * </ul>
     *
     * @param statuses             The task status should be one of this list.
     * @param excludedTags         The task tag should not be one this tag list
     *                             - use {@literal null} if no tag should be excluded.
     * @param excludedChainTaskIds The chain task ID should not be one of this list.
     * @param sort                 How to prioritize tasks.
     * @return The first task matching with the criteria, according to the {@code sort} parameter.
     */
    Optional<Task> findFirstByCurrentStatusInAndTagNotInAndChainTaskIdNotIn(List<TaskStatus> statuses, List<String> excludedTags, List<String> excludedChainTaskIds, Sort sort);
    
    @Query("{ 'currentStatus': {$nin: ?0} }")
    List<Task> findByCurrentStatusNotIn(List<TaskStatus> statuses);

    @Query(value = "{ finalDeadline: {$lt : ?0} }", fields = "{ chainTaskId: true }")
    List<Task> findChainTaskIdsByFinalDeadlineBefore(Date date);

    List<Task> findByCurrentStatusInAndContributionDeadlineAfter(List<TaskStatus> status, Date date);
}
