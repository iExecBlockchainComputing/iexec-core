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

import com.iexec.core.utils.ThreadPoolExecutorUtils;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * This class is used to perform updates on a task one by one. It also ensures that no extra update is
 * performed for no reason (in the case of multiple replicate updates in a short time, the task update will only be called
 * once)
 */
@Slf4j
@Service
public class TaskExecutorEngine {

    private TaskService taskService;
    private Map<String, ThreadPoolExecutor> executorMap;

    public TaskExecutorEngine(TaskService taskService) {
        this.taskService = taskService;
        executorMap = new ConcurrentHashMap<>();
    }

    public CompletableFuture<Boolean> updateTask(String chainTaskId) {

        executorMap.putIfAbsent(chainTaskId, ThreadPoolExecutorUtils.singleThreadExecutorWithFixedSizeQueue(1));

        Executor executor = executorMap.get(chainTaskId);

        return CompletableFuture.supplyAsync(() -> taskService.tryUpgradeTaskStatus(chainTaskId), executor)
        .handle((res, err) -> {
            if (err != null) {
                err.printStackTrace();
                return false;
            }
            return res;
        });

    }

    public void removeTaskExecutor(Task task){
        String chainTaskId = task.getChainTaskId();

        if (task.getCurrentStatus().equals(TaskStatus.COMPLETED)) {
            executorMap.get(chainTaskId).shutdown();
            executorMap.remove(chainTaskId);
        }
    }
}
