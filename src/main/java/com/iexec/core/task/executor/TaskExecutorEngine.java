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

package com.iexec.core.task.executor;

import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Component;

/**
 * This class is used to perform updates on a task one by one.
 * It also ensures that no extra update is performed for no reason
 * (in the case of multiple replicate updates in a short time,
 * the task update will only be called once)
 */
@Component
public class TaskExecutorEngine {

    private final TaskExecutorFactory taskExecutorFactory;

    public TaskExecutorEngine(TaskExecutorFactory taskExecutorFactory) {
        this.taskExecutorFactory = taskExecutorFactory;
    }

    /**
     * Execute task update runnable in a dedicated executor.
     * 
     * @param chainTaskId
     * @return completableFuture to follow task execution.
     */
    public CompletableFuture<Void> run(
        String chainTaskId, long expiration, Runnable taskUpdate
    ) {
        return taskExecutorFactory
                .getOrCreate(chainTaskId, expiration)
                .map(executor -> CompletableFuture.runAsync(taskUpdate, executor))
                .orElse(CompletableFuture.completedFuture(null));
    }

    /**
     * Remove executor by chainTaskId.
     * 
     * @param chainTaskId
     */
    public void removeExecutor(String chainTaskId) {
        taskExecutorFactory.remove(chainTaskId);
    }
}
