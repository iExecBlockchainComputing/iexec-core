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

import static com.iexec.core.utils.ThreadPoolExecutorUtils.singleThreadExecutorWithFixedSizeQueue;

import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;

/**
 * This class is used to perform updates on a task one by one. It also ensures that no extra update is
 * performed for no reason (in the case of multiple replicate updates in a short time, the task update will only be called
 * once)
 */
@Slf4j
@Service
public class TaskExecutorEngine {

    private final TaskService taskService;
    private final TaskExecutorFactory taskExecutorFactory;

    public TaskExecutorEngine(TaskService taskService) {
        this.taskService = taskService;
        this.taskExecutorFactory = new TaskExecutorFactory();
    }

    /**
     * Trigger task update in a separate executor.
     * 
     * @param chainTaskId
     * @return
     */
    public CompletableFuture<Boolean> updateTask(String chainTaskId) {        
        Executor executor = this.taskExecutorFactory.getOrCreate(chainTaskId);
        return CompletableFuture
                .supplyAsync(
                        () -> taskService.tryUpgradeTaskStatus(chainTaskId),
                        executor
                )
                .handle((res, err) -> {
                        if (err != null) {
                            err.printStackTrace();
                        }
                        return res && err == null;
                });
    }

    /**
     * Remove the thread pool executor
     * dedicated to this task.
     * 
     * @param task
     */
    public void removeTaskExecutor(Task task) {
        if (!TaskStatus.isFinalStatus(task.getCurrentStatus())) {
            log.error("Cannot remove executor for unfinished " +
                    "task [chainTaskId:{}]", task.getChainTaskId());
            return;
        }
        this.taskExecutorFactory.expire(task.getChainTaskId());
    }

    /**
     * A factory that manages expiring thread
     * executors. Each executor has its own
     * expiration period.
     */
    private class TaskExecutorFactory {

        // this map is thread-safe
        private final ExpiringMap<String, ThreadPoolExecutor> map =
                ExpiringMap.builder()
                        .expirationPolicy(ExpirationPolicy.CREATED)
                        .variableExpiration()
                        // shutdown thread executor when an entry expires
                        .expirationListener(shutdownExecutorWhenExpired())
                        .build();

        /**
         * Get a task's executor or create a new
         * one if needed.
         * 
         * @param chainTaskId id associated to executor
         * @param maxTtl max time to live for this executor
         * @return
         */
        public Executor getOrCreate(String chainTaskId) {
            if (map.containsKey(chainTaskId)) {
                return map.get(chainTaskId);
            }
            String threadPoolName = "0x" + chainTaskId.substring(0, 7);
            map.put(chainTaskId, singleThreadExecutorWithFixedSizeQueue(1, threadPoolName));
            Date deadline = taskService.getTaskFinalDeadline(chainTaskId);
            map.setExpiration(chainTaskId, deadline.getTime(), TimeUnit.MILLISECONDS);
            return map.get(chainTaskId);
        }

        /**
         * Set the expiration period of an executor
         * to 0 and the ExpirationListener will do
         * the rest.
         * @param chainTaskId
         */
        public void expire(String chainTaskId) {
            map.setExpiration(chainTaskId, 0, TimeUnit.MILLISECONDS);
        }

        /**
         * The max TTL of an executor is the max duration
         * of an iExec task in a given iExec category.
         * The executor can expire before the max duration
         * once the task is COMPLETED.
         * 
         * @return a listener that shuts down the executor.
         */
        private ExpirationListener<String,ThreadPoolExecutor> shutdownExecutorWhenExpired() {
            return new ExpirationListener<String,ThreadPoolExecutor>() {
                @Override
                public void expired(String chainTaskId, ThreadPoolExecutor executor) {
                    executor.shutdown();
                }
            };
        }
    }
}
