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

package com.iexec.core.task.update;

import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpiringMap;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Supplier;

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
     * An XL task timeout happens after 100 hours.
     */
    private static final long LONGEST_TASK_TIMEOUT = 100;
    /**
     * Max number of threads to update task for each core.
     */
    private static final int TASK_UPDATE_THREADS_POOL_SIZE = Runtime.getRuntime().availableProcessors() * 2;

    private final ExecutorService executorService = Executors.newFixedThreadPool(1);
    private final ConcurrentMap<String, Object> locks = ExpiringMap.builder()
            .expiration(LONGEST_TASK_TIMEOUT, TimeUnit.HOURS)
            .build();

    final TaskUpdatePriorityBlockingQueue queue = new TaskUpdatePriorityBlockingQueue();
    final ThreadPoolExecutor taskUpdateExecutor = new ThreadPoolExecutor(
            TASK_UPDATE_THREADS_POOL_SIZE,
            TASK_UPDATE_THREADS_POOL_SIZE,
            60,
            TimeUnit.SECONDS,
            queue
    );
    private TaskUpdateRequestConsumer consumer;

    private final TaskService taskService;

    public TaskUpdateRequestManager(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * Publish TaskUpdateRequest async
     * @param chainTaskId
     * @return
     */
    public CompletableFuture<Boolean> publishRequest(String chainTaskId) {
        Supplier<Boolean> publishRequest = () -> {
            if (chainTaskId.isEmpty()){
                return false;
            }
            if (queue.containsTask(chainTaskId)){
                log.warn("Request already published [chainTaskId:{}]", chainTaskId);
                return false;
            }
            final Optional<Task> task = taskService.getTaskByChainTaskId(chainTaskId);
            if (task.isEmpty()) {
                log.warn("No such task. [chainTaskId: {}]", chainTaskId);
                return false;
            }

            taskUpdateExecutor.execute(new TaskUpdate(task.get(), locks, consumer));
            log.info("Published task update request [chainTaskId:{}, queueSize:{}]", chainTaskId, queue.size());
            return true;
        };
        // TODO: find a better way to publish request.
        // As of now, we do sequential requests to the DB which can cause a big load.
        // We should aim to have some batch requests to unload the scheduler.
        return CompletableFuture.supplyAsync(publishRequest, executorService);
    }

    /**
     * Authorize one TaskUpdateRequest consumer subscription at a time.
     * @param consumer
     * @return
     */
    public void setRequestConsumer(final TaskUpdateRequestConsumer consumer) {
        this.consumer = consumer;
    }
}
