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

package com.iexec.core.task.update;

import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpiringMap;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.iexec.common.chain.CategoriesUtils.LONGEST_TASK_TIMEOUT;

/**
 * This class is used to perform updates on a task one by one.
 * It also ensures that no extra update is performed for no reason
 * (in the case of multiple replicate updates in a short time,
 * the task update will only be called once)
 */
@Slf4j
@Component
public class TaskUpdateRequestManager {
    /**
     * Max number of threads to update task for each core.
     */
    private static final int TASK_UPDATE_THREADS_POOL_SIZE = 32;

    // Working with semaphore to guarantee at most 1 item in queue and 1 running thread
    private final ExpiringMap<String, Semaphore> taskExecutionLockRunner = ExpiringMap.builder()
            .expiration(LONGEST_TASK_TIMEOUT.getSeconds(), TimeUnit.SECONDS)
            .build();

    final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    // Both `corePoolSize` and `maximumPoolSize` should be set to `TASK_UPDATE_THREADS_POOL_SIZE`.
    // Otherwise, `taskUpdateExecutor` won't pop `maximumPoolSize` threads
    // as new threads are popped only if the queue is full
    // - which never happens with an unbounded queue.
    final ThreadPoolExecutor taskUpdateExecutor = new ThreadPoolExecutor(
            TASK_UPDATE_THREADS_POOL_SIZE,
            TASK_UPDATE_THREADS_POOL_SIZE,
            0,
            TimeUnit.MILLISECONDS,
            queue
    );

    private final TaskService taskService;
    private final TaskUpdateManager taskUpdateManager;

    public TaskUpdateRequestManager(TaskService taskService,
                                    TaskUpdateManager taskUpdateManager) {
        this.taskService = taskService;
        this.taskUpdateManager = taskUpdateManager;
    }

    /**
     * Publish a TaskUpdateRequest if no request is already waiting for this task.
     * This request will be dealt with asynchronously.
     * <p>
     * As of now, we do sequential requests to the DB which can cause a big load.
     * We should aim to have some batch requests to unload the scheduler.
     *
     * @param chainTaskId ID of the task to publish the request for.
     * @return {@literal true} if request has been published,
     * {@literal false} otherwise.
     */
    public synchronized boolean publishRequest(String chainTaskId) {
        if (chainTaskId.isEmpty()) {
            return false;
        }
        if (queue.stream().map(TaskUpdate.class::cast).anyMatch(
                taskUpdate -> chainTaskId.equals(taskUpdate.getChainTaskId()))) {
            log.debug("Request already published [chainTaskId:{}]", chainTaskId);
            return false;
        }
        final Task task = taskService.getTaskByChainTaskId(chainTaskId).orElse(null);
        if (task == null) {
            log.warn("No such task [chainTaskId: {}]", chainTaskId);
            return false;
        }

        // Add semaphore to expiring map if missing
        taskExecutionLockRunner.putIfAbsent(chainTaskId, new Semaphore(1));

        taskUpdateExecutor.execute(new TaskUpdate(task, this::updateTask));
        log.debug("Published task update request [chainTaskId:{}, currentStatus:{}, contributionDeadline:{}, queueSize:{}]",
                chainTaskId, task.getCurrentStatus(), task.getContributionDeadline(), queue.size());
        return true;
    }

    private void updateTask(String chainTaskId) {
        if (!taskExecutionLockRunner.get(chainTaskId).tryAcquire()) {
            log.debug("Could not acquire lock for task update [chainTaskId:{}]", chainTaskId);
            return;
        }
        try {
            log.debug("Acquire lock for task update [chainTaskId:{}]", chainTaskId);
            taskUpdateManager.updateTask(chainTaskId);
        } finally {
            log.debug("Release lock for task update [chainTaskId:{}]", chainTaskId);
            taskExecutionLockRunner.get(chainTaskId).release();
        }
    }
}
