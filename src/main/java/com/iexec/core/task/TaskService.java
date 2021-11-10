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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.iexec.core.task.TaskStatus.*;

@Slf4j
@Service
public class TaskService {

    private final ConcurrentHashMap<String, Boolean>
            taskAccessForNewReplicateLock = new ConcurrentHashMap<>();

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
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
        return taskRepository
                .findByChainDealIdAndTaskIndex(chainDealId, taskIndex)
                .<Optional<Task>>map((task) -> {
                        log.info("Task already added [chainDealId:{}, taskIndex:{}, " +
                                "imageName:{}, commandLine:{}, trust:{}]", chainDealId,
                                taskIndex, imageName, commandLine, trust);
                        return Optional.empty();
                })
                .orElseGet(() -> {
                        Task newTask = new Task(chainDealId, taskIndex, imageName,
                                commandLine, trust, maxExecutionTime, tag);
                        newTask.setDealBlockNumber(dealBlockNumber);
                        newTask.setFinalDeadline(finalDeadline);
                        newTask.setContributionDeadline(contributionDeadline);
                        newTask = taskRepository.save(newTask);
                        log.info("Added new task [chainDealId:{}, taskIndex:{}, imageName:{}, " +
                                "commandLine:{}, trust:{}, chainTaskId:{}]", chainDealId,
                                taskIndex, imageName, commandLine, trust, newTask.getChainTaskId());
                        return Optional.of(newTask);
                });
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

    public List<Task> getInitializedOrRunningTasks() {
        return taskRepository.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING));
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

    public void initializeTaskAccessForNewReplicateLock(String chainTaskId) {
        taskAccessForNewReplicateLock.putIfAbsent(chainTaskId, false);
    }

    public Boolean isTaskBeingAccessedForNewReplicate(String chainTaskId) {
        return taskAccessForNewReplicateLock.get(chainTaskId);
    }

    public void lockTaskAccessForNewReplicate(String chainTaskId) {
        setTaskAccessForNewReplicateLock(chainTaskId, true);
    }

    public void unlockTaskAccessForNewReplicate(String chainTaskId) {
        setTaskAccessForNewReplicateLock(chainTaskId, false);
    }

    private void setTaskAccessForNewReplicateLock(String chainTaskId, boolean isTaskBeingAccessedForNewReplicate) {
        taskAccessForNewReplicateLock.replace(chainTaskId, isTaskBeingAccessedForNewReplicate);
    }


}
