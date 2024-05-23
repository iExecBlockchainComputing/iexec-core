/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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

public interface TaskRepository extends MongoRepository<Task, String> {

    Optional<Task> findByChainTaskId(String id);

    @Query("{ 'chainTaskId': {$in: ?0} }")
    List<Task> findByChainTaskId(List<String> ids);

    List<Task> findByCurrentStatus(TaskStatus status);

    @Query("{ 'currentStatus': {$in: ?0} }")
    List<Task> findByCurrentStatus(List<TaskStatus> statuses);

    /**
     * Retrieves the prioritized task matching with given criteria:
     * <ul>
     *     <li>Task is in one of given {@code statuses}
     *     <li>Task contribution deadline is after a provided {@code timestamp}
     *     <li>Task has not given {@code excludedTags}, this is mainly used to exclude TEE tasks
     *     <li>Chain task ID is not one of the given {@code excludedChainTaskIds}
     *     <li>Tasks are prioritized according to the {@code sort} parameter
     * </ul>
     *
     * @param statuses             The task status should be one of this list.
     * @param timestamp            The task contribution deadline should be after the provided timestamp.
     * @param excludedTags         The task tag should not be one this tag list
     *                             - use {@literal null} if no tag should be excluded.
     * @param excludedChainTaskIds The chain task ID should not be one of this list.
     * @param sort                 How to prioritize tasks.
     * @return The first task matching with the criteria, according to the {@code sort} parameter.
     */
    Optional<Task> findFirstByCurrentStatusInAndContributionDeadlineAfterAndTagNotInAndChainTaskIdNotIn(
            List<TaskStatus> statuses, Date timestamp, List<String> excludedTags, List<String> excludedChainTaskIds, Sort sort);

    @Query(value = "{ finalDeadline: {$lt : ?0} }", fields = "{ chainTaskId: true }")
    List<Task> findChainTaskIdsByFinalDeadlineBefore(Date date);

    List<Task> findByCurrentStatusInAndContributionDeadlineAfter(List<TaskStatus> status, Date date);

    long countByCurrentStatus(TaskStatus currentStatus);
}
