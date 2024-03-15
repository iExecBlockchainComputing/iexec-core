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

import com.iexec.commons.poco.chain.ChainTask;
import com.iexec.commons.poco.chain.ChainTaskStatus;
import com.iexec.commons.poco.tee.TeeUtils;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.replicate.ReplicatesList;
import com.iexec.core.task.event.TaskCreatedEvent;
import com.iexec.core.task.event.TaskStatusesCountUpdatedEvent;
import com.mongodb.client.result.UpdateResult;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.iexec.core.task.TaskStatus.*;

@Slf4j
@Service
public class TaskService {

    private static final String CHAIN_TASK_ID_FIELD = "chainTaskId";
    public static final String METRIC_TASKS_COMPLETED_COUNT = "iexec.core.tasks.completed";
    public static final String METRIC_TASKS_STATUSES_COUNT = "iexec.core.tasks.count";
    private final MongoTemplate mongoTemplate;
    private final TaskRepository taskRepository;
    private final IexecHubService iexecHubService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Counter completedTasksCounter;
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
        this.completedTasksCounter = Metrics.counter(METRIC_TASKS_COMPLETED_COUNT);
    }

    @PostConstruct
    void init() {
        completedTasksCounter.increment(findByCurrentStatus(TaskStatus.COMPLETED).size());
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
     * Save task in database if it does not
     * already exist.
     *
     * @param chainDealId
     * @param taskIndex
     * @param dealBlockNumber
     * @param imageName
     * @param commandLine
     * @param trust
     * @param maxExecutionTime
     * @param tag
     * @param contributionDeadline
     * @param finalDeadline
     * @return optional containing the saved
     * task, {@link Optional#empty()} otherwise.
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
        Task newTask = new Task(chainDealId, taskIndex, imageName,
                commandLine, trust, maxExecutionTime, tag);
        newTask.setDealBlockNumber(dealBlockNumber);
        newTask.setFinalDeadline(finalDeadline);
        newTask.setContributionDeadline(contributionDeadline);
        try {
            newTask = taskRepository.save(newTask);
            log.info("Added new task [chainDealId:{}, taskIndex:{}, imageName:{}, commandLine:{}, trust:{}, chainTaskId:{}]",
                    chainDealId, taskIndex, imageName, commandLine, trust, newTask.getChainTaskId());
            return Optional.of(newTask);
        } catch (DuplicateKeyException e) {
            log.info("Task already added [chainDealId:{}, taskIndex:{}, imageName:{}, commandLine:{}, trust:{}]",
                    chainDealId, taskIndex, imageName, commandLine, trust);
            return Optional.empty();
        }
    }

    /**
     * Updates a task if it already exists in DB.
     * Otherwise, will not do anything.
     *
     * @param task Task to update.
     * @return An {@link Optional<Task>} if task exists, {@link Optional#empty()} otherwise.
     */
    public Optional<Task> updateTask(Task task) {

        Optional<Task> optionalTask = taskRepository
                .findByChainTaskId(task.getChainTaskId())
                .map(existingTask -> taskRepository.save(task));

        if (optionalTask.isPresent() && optionalTask.get().getCurrentStatus() == TaskStatus.COMPLETED) {
            completedTasksCounter.increment();
        }

        return optionalTask;
    }

    public long updateTaskStatus(Task task, TaskStatus currentStatus, List<TaskStatusChange> statusChanges) {
        Update update = Update.update("currentStatus", task.getCurrentStatus());
        update.push("dateStatusList").each(statusChanges);
        UpdateResult result = mongoTemplate.updateFirst(
                Query.query(Criteria.where(CHAIN_TASK_ID_FIELD).is(task.getChainTaskId())),
                update,
                Task.class);
        log.debug("Updated chainTaskId [chainTaskId:{},  result:{}]", task.getChainTaskId(), result);
        updateMetricsAfterStatusUpdate(currentStatus, task.getCurrentStatus());
        return result.getModifiedCount();
    }

    public void updateTask(String chainTaskId, Update update) {
        UpdateResult result = mongoTemplate.updateFirst(
                Query.query(Criteria.where(CHAIN_TASK_ID_FIELD).is(chainTaskId)),
                update, Task.class);
        log.debug("Updated chainTaskId [chainTaskId:{},  result{}]", chainTaskId, result);
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
     * @param shouldExcludeTeeTasks Whether TEE tasks should be retrieved
     *                              as well as standard tasks.
     * @param excludedChainTaskIds  Tasks to exclude from retrieval.
     * @return The first task which is {@link TaskStatus#INITIALIZED}
     * or {@link TaskStatus#RUNNING},
     * or {@link Optional#empty()} if no task meets the requirements.
     */
    public Optional<Task> getPrioritizedInitializedOrRunningTask(
            boolean shouldExcludeTeeTasks,
            List<String> excludedChainTaskIds) {
        final List<String> excludedTags = shouldExcludeTeeTasks
                ? List.of(TeeUtils.TEE_SCONE_ONLY_TAG, TeeUtils.TEE_GRAMINE_ONLY_TAG)
                : null;
        return findPrioritizedTask(
                Arrays.asList(INITIALIZED, RUNNING),
                excludedTags,
                excludedChainTaskIds,
                Sort.by(Sort.Order.desc(Task.CURRENT_STATUS_FIELD_NAME),
                        Sort.Order.asc(Task.CONTRIBUTION_DEADLINE_FIELD_NAME)));
    }

    /**
     * Shortcut for {@link TaskRepository#findFirstByCurrentStatusInAndTagNotInAndChainTaskIdNotIn}.
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
        return taskRepository.findFirstByCurrentStatusInAndTagNotInAndChainTaskIdNotIn(
                statuses,
                excludedTags,
                excludedChainTaskIds,
                sort
        );
    }

    public List<Task> getTasksInNonFinalStatuses() {
        return taskRepository.findByCurrentStatusNotIn(TaskStatus.getFinalStatuses());
    }

    public List<Task> getTasksWhereFinalDeadlineIsPossible() {
        return taskRepository.findByCurrentStatusNotIn(TaskStatus.getStatusesWhereFinalDeadlineIsImpossible());
    }

    public List<String> getChainTaskIdsOfTasksExpiredBefore(Date expirationDate) {
        return taskRepository.findChainTaskIdsByFinalDeadlineBefore(expirationDate)
                .stream()
                .map(Task::getChainTaskId)
                .collect(Collectors.toList());
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

    public long getCompletedTasksCount() {
        return (long) completedTasksCounter.count();
    }

    public long countByCurrentStatus(TaskStatus status) {
        return taskRepository.countByCurrentStatus(status);
    }

    void updateMetricsAfterStatusUpdate(TaskStatus previousStatus, TaskStatus newStatus) {
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
