/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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

import com.iexec.commons.poco.chain.ChainTask;
import com.iexec.commons.poco.chain.ChainTaskStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.replicate.ReplicatesList;
import com.iexec.core.task.event.TaskCreatedEvent;
import com.iexec.core.task.event.TaskStatusesCountUpdatedEvent;
import com.mongodb.client.result.UpdateResult;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.iexec.core.task.Task.*;
import static com.iexec.core.task.TaskStatus.*;

@Slf4j
@Service
public class TaskService {

    public static final String METRIC_TASKS_STATUSES_COUNT = "iexec.core.tasks.count";
    private final MongoTemplate mongoTemplate;
    private final TaskRepository taskRepository;
    private final IexecHubService iexecHubService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final LinkedHashMap<TaskStatus, AtomicLong> currentTaskStatusesCount;

    public TaskService(MongoTemplate mongoTemplate,
                       TaskRepository taskRepository,
                       IexecHubService iexecHubService,
                       ApplicationEventPublisher applicationEventPublisher) {
        this.mongoTemplate = mongoTemplate;
        this.taskRepository = taskRepository;
        this.iexecHubService = iexecHubService;
        this.applicationEventPublisher = applicationEventPublisher;

        this.currentTaskStatusesCount = Arrays.stream(TaskStatus.values())
                .collect(Collectors.toMap(
                        Function.identity(),
                        status -> new AtomicLong(),
                        (a, b) -> b,
                        LinkedHashMap::new));

        for (TaskStatus status : TaskStatus.values()) {
            Gauge.builder(METRIC_TASKS_STATUSES_COUNT, () -> currentTaskStatusesCount.get(status).get())
                    .tags(
                            "period", "current",
                            "status", status.name()
                    ).register(Metrics.globalRegistry);
        }
    }

    @PostConstruct
    void init() {
        final ExecutorService taskStatusesCountExecutor = Executors.newSingleThreadExecutor();
        taskStatusesCountExecutor.submit(this::initializeCurrentTaskStatusesCount);
        taskStatusesCountExecutor.shutdown();
    }

    /**
     * The following could take a bit of time, depending on how many tasks are in DB.
     * It is expected to take ~1.7s for 1,000,000 tasks and to be linear (so, ~17s for 10,000,000 tasks).
     * As we use AtomicLongs, the final count should be accurate - no race conditions to expect,
     * even though new deals are detected during the count.
     */
    private void initializeCurrentTaskStatusesCount() {
        currentTaskStatusesCount
                .entrySet()
                .parallelStream()
                .forEach(entry -> entry.getValue().addAndGet(countByCurrentStatus(entry.getKey())));
        publishTaskStatusesCountUpdate();
    }

    /**
     * Save task in database if it does not already exist.
     *
     * @param chainDealId          on-chain deal id
     * @param taskIndex            task index in deal
     * @param dealBlockNumber      block number when orders were matched to produce the current deal
     * @param imageName            OCI image to use for replicates computation
     * @param commandLine          command that will be executed during replicates computation
     * @param trust                trust level, impacts replication
     * @param maxExecutionTime     execution time
     * @param tag                  deal tag describing additional features like TEE framework
     * @param contributionDeadline date after which a worker cannot contribute
     * @param finalDeadline        date after which a task cannot be updated
     * @return {@code Optional} containing the saved task, {@link Optional#empty()} otherwise.
     */
    public Optional<Task> addTask(
            String chainDealId,
            int taskIndex,
            long dealBlockNumber,
            String imageName,
            String commandLine,
            int trust,
            long maxExecutionTime,
            String tag,
            Date contributionDeadline,
            Date finalDeadline
    ) {
        Task newTask = new Task(chainDealId, taskIndex, imageName, commandLine, trust, maxExecutionTime, tag);
        newTask.setDealBlockNumber(dealBlockNumber);
        newTask.setFinalDeadline(finalDeadline);
        newTask.setContributionDeadline(contributionDeadline);
        final String taskLogDetails = String.format("chainDealId:%s, taskIndex:%s, imageName:%s, commandLine:%s, trust:%s, contributionDeadline:%s, finalDeadline:%s",
                chainDealId, taskIndex, imageName, commandLine, trust, contributionDeadline, finalDeadline);
        try {
            newTask = taskRepository.save(newTask);
            log.info("Added new task [{}}, chainTaskId:{}]", taskLogDetails, newTask.getChainTaskId());
            return Optional.of(newTask);
        } catch (DuplicateKeyException e) {
            log.info("Task already added [{}]", taskLogDetails);
            return Optional.empty();
        }
    }

    /**
     * Updates the status of a single task in the collection
     *
     * @param chainTaskId   On-chain ID of the task to update
     * @param currentStatus Expected {@code currentStatus} of the task when executing the update
     * @param targetStatus  Wished {@code currentStatus} the task should be updated to
     * @param statusChanges List of {@code TaskStatusChange} to append to the {@code dateStatusList} field
     * @return The number of updated documents in the task collection, should be {@literal 0} or {@literal 1} due to task ID uniqueness
     */
    public long updateTaskStatus(String chainTaskId, TaskStatus currentStatus, TaskStatus targetStatus, List<TaskStatusChange> statusChanges) {
        final Update update = Update.update(CURRENT_STATUS_FIELD_NAME, targetStatus)
                .push(DATE_STATUS_LIST_FIELD_NAME).each(statusChanges);
        final UpdateResult result = updateTask(chainTaskId, currentStatus, update);
        return result.getModifiedCount();
    }

    /**
     * Update a single task in the collection
     *
     * @param chainTaskId   On-chain ID of the task to update
     * @param currentStatus Expected {@code currentStatus} of the task when executing the update
     * @param update        Update to execute on the task if criteria are respected
     * @return The result of the update execution on the task collection
     */
    public UpdateResult updateTask(String chainTaskId, TaskStatus currentStatus, Update update) {
        final Criteria criteria = Criteria.where(CHAIN_TASK_ID_FIELD_NAME).is(chainTaskId)
                .and(CURRENT_STATUS_FIELD_NAME).is(currentStatus);
        // chainTaskId and currentStatus are part of the criteria, no need to add them explicitly
        log.debug("Update request [criteria:{}, update:{}]",
                criteria.getCriteriaObject(), update.getUpdateObject());
        final UpdateResult result = mongoTemplate.updateFirst(Query.query(criteria), update, Task.class);
        log.debug("Update execution result [chainTaskId:{}, result:{}]", chainTaskId, result);
        if (result.getModifiedCount() == 0L) {
            log.warn("The task was not updated [chainTaskId:{}]", chainTaskId);
        } else if (isTaskCurrentStatusUpdated(update)) {
            // A single document has been updated (chainTaskId uniqueness) and the currentStatus has been modified
            updateMetricsAfterStatusUpdate(currentStatus, update.getUpdateObject().get("$set", Document.class)
                    .get(CURRENT_STATUS_FIELD_NAME, TaskStatus.class));
        }
        return result;
    }

    /**
     * Checks if provided MongoDB update has modified the {@code currentStatus} field of the task
     *
     * @param update The MongoDB request to check
     * @return {@literal true} if the {@code currentStatus} was updated, {@literal false} otherwise
     */
    private boolean isTaskCurrentStatusUpdated(Update update) {
        return update.getUpdateObject().containsKey("$set")
                && update.getUpdateObject().get("$set", Document.class).containsKey(CURRENT_STATUS_FIELD_NAME);
    }

    public Optional<Task> getTaskByChainTaskId(String chainTaskId) {
        return taskRepository.findByChainTaskId(chainTaskId);
    }

    public List<Task> getTasksByChainTaskIds(List<String> chainTaskIds) {
        return taskRepository.findByChainTaskId(chainTaskIds);
    }

    public List<Task> findByCurrentStatus(TaskStatus status) {
        return taskRepository.findByCurrentStatus(status);
    }

    public List<Task> findByCurrentStatus(List<TaskStatus> statusList) {
        return taskRepository.findByCurrentStatus(statusList);
    }

    /**
     * Retrieves the first {@link TaskStatus#INITIALIZED}
     * or {@link TaskStatus#RUNNING} task from the DB,
     * depending on current statuses and contribution deadlines.
     * <p>
     * If {@code shouldExcludeTeeTasks} is {@literal true},
     * then only standard tasks are retrieved.
     * Otherwise, all tasks are retrieved.
     * <p>
     * Tasks can be excluded with {@code excludedChainTaskIds}.
     *
     * @param excludedTags         Whether some tags should not be eligible, it is focused on TEE tags at the moment
     * @param excludedChainTaskIds Tasks to exclude from retrieval.
     * @return The first task which is {@link TaskStatus#INITIALIZED}
     * or {@link TaskStatus#RUNNING},
     * or {@link Optional#empty()} if no task meets the requirements.
     */
    public Optional<Task> getPrioritizedInitializedOrRunningTask(
            final List<String> excludedTags,
            final List<String> excludedChainTaskIds) {
        return findPrioritizedTask(
                List.of(INITIALIZED, RUNNING),
                excludedTags,
                excludedChainTaskIds,
                Sort.by(Sort.Order.desc(CURRENT_STATUS_FIELD_NAME),
                        Sort.Order.asc(CONTRIBUTION_DEADLINE_FIELD_NAME)));
    }

    /**
     * Shortcut for {@link TaskRepository#findFirstByCurrentStatusInAndContributionDeadlineAfterAndTagNotInAndChainTaskIdNotIn}.
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
     * @param excludedTags         The task tag should not be these tags
     *                             - use {@literal null} if no tag should be excluded.
     * @param excludedChainTaskIds The chain task ID should not be one of this list.
     * @param sort                 How to prioritize tasks.
     * @return The first task matching with the criteria, according to the {@code sort} parameter.
     */
    private Optional<Task> findPrioritizedTask(List<TaskStatus> statuses,
                                               List<String> excludedTags,
                                               List<String> excludedChainTaskIds,
                                               Sort sort) {
        return taskRepository.findFirstByCurrentStatusInAndContributionDeadlineAfterAndTagNotInAndChainTaskIdNotIn(
                statuses,
                new Date(),
                excludedTags,
                excludedChainTaskIds,
                sort
        );
    }

    /**
     * Updates task on a given MongoDB query.
     *
     * @param query  The query to perform to lookup for tasks in the collection
     * @param update The update to execute on the tasks returned by the query
     * @return The list of modified chain task ids
     */
    public List<String> updateMultipleTasksByQuery(Query query, Update update) {
        return mongoTemplate.find(query, Task.class).stream()
                .map(task -> updateSingleTask(task, update))
                .toList();
    }

    /**
     * Updates a single task in the task collection
     *
     * @param task   The task to update
     * @param update The update to perform
     * @return The chain task id of the task
     */
    private String updateSingleTask(Task task, Update update) {
        final UpdateResult updateResult = updateTask(task.getChainTaskId(), task.getCurrentStatus(), update);
        return updateResult.getModifiedCount() == 0L ? "" : task.getChainTaskId();
    }

    public List<String> getChainTaskIdsOfTasksExpiredBefore(Date expirationDate) {
        return taskRepository.findChainTaskIdsByFinalDeadlineBefore(expirationDate)
                .stream()
                .map(Task::getChainTaskId)
                .toList();
    }

    /**
     * An initializable task is in RECEIVED or
     * INITIALIZED status and has a contribution
     * deadline that is still in the future.
     *
     * @return list of initializable tasks
     */
    public List<Task> getInitializableTasks() {
        return taskRepository
                .findByCurrentStatusInAndContributionDeadlineAfter(
                        List.of(RECEIVED, INITIALIZING), new Date());
    }

    public boolean isExpired(String chainTaskId) {
        Date finalDeadline = getTaskFinalDeadline(chainTaskId);
        return finalDeadline != null && finalDeadline.before(new Date());
    }

    public Date getTaskFinalDeadline(String chainTaskId) {
        return getTaskByChainTaskId(chainTaskId)
                .map(Task::getFinalDeadline)
                .orElse(null);
    }

    public boolean isConsensusReached(ReplicatesList replicatesList) {
        final ChainTask chainTask = iexecHubService.getChainTask(replicatesList.getChainTaskId()).orElse(null);
        if (chainTask == null) {
            log.error("Consensus not reached, task not found on-chain [chainTaskId:{}]", replicatesList.getChainTaskId());
            return false;
        }

        boolean isChainTaskRevealing = chainTask.getStatus() == ChainTaskStatus.REVEALING;
        if (!isChainTaskRevealing) {
            log.debug("Consensus not reached, on-chain task is not in REVEALING status [chainTaskId:{}]",
                    replicatesList.getChainTaskId());
            return false;
        }

        int onChainWinners = chainTask.getWinnerCounter();
        int offChainWinners = replicatesList.getNbValidContributedWinners(chainTask.getConsensusValue());
        log.debug("Returning off-chain and on-chain winners [offChainWinners:{}, onChainWinners:{}]",
                offChainWinners, onChainWinners);
        return offChainWinners >= onChainWinners;
    }

    public long countByCurrentStatus(TaskStatus status) {
        return taskRepository.countByCurrentStatus(status);
    }

    void updateMetricsAfterStatusUpdate(TaskStatus previousStatus, TaskStatus newStatus) {
        log.debug("updateMetricsAfterStatusUpdate [prev:{}, next:{}]", previousStatus, newStatus);
        currentTaskStatusesCount.get(previousStatus).decrementAndGet();
        currentTaskStatusesCount.get(newStatus).incrementAndGet();
        publishTaskStatusesCountUpdate();
    }

    @EventListener(TaskCreatedEvent.class)
    void onTaskCreatedEvent() {
        currentTaskStatusesCount.get(RECEIVED).incrementAndGet();
        publishTaskStatusesCountUpdate();
    }

    private void publishTaskStatusesCountUpdate() {
        // Copying the map here ensures the original values can't be updated from outside this class.
        // As this data should be read only, no need for any atomic class.
        final LinkedHashMap<TaskStatus, Long> currentTaskStatusesCountToPublish = currentTaskStatusesCount
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entrySet -> entrySet.getValue().get(),
                        (a, b) -> b,
                        LinkedHashMap::new
                ));
        final TaskStatusesCountUpdatedEvent event = new TaskStatusesCountUpdatedEvent(currentTaskStatusesCountToPublish);
        applicationEventPublisher.publishEvent(event);
    }
}
