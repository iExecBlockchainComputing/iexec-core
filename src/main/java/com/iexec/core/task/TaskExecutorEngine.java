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

import java.util.concurrent.Future;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

/**
 * This class is used to perform updates on a task one by one. It also ensures that no extra update is
 * performed for no reason (in the case of multiple replicate updates in a short time, the task update will only be called
 * once)
 */
@Service
public class TaskExecutorEngine {

    private final TaskService taskService;
    private final TaskExecutorFactory taskExecutorFactory;

    public TaskExecutorEngine(TaskService taskService,
            TaskExecutorFactory taskExecutorFactory) {
        this.taskService = taskService;
        this.taskExecutorFactory = taskExecutorFactory;
    }

    /**
     * Trigger task update in a separate executor.
     * 
     * @param chainTaskId
     * @return
     */
    @Async("@taskExecutorFactory.getOrCreate(#chainTaskId)")
    public Future<Boolean> updateTask(String chainTaskId) {
        return new AsyncResult<Boolean>(taskService.tryUpgradeTaskStatus(chainTaskId));
    }

    /**
     * Remove the thread pool executor
     * dedicated to this task.
     * 
     * @param task
     */
    public void removeTaskExecutor(Task task) {
        this.taskExecutorFactory.removeTaskExecutor(task);
    }
}
