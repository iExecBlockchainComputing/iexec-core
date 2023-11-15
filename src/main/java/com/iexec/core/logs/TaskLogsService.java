/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.logs;

import com.iexec.common.replicate.ComputeLogs;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TaskLogsService {

    private final TaskLogsRepository taskLogsRepository;

    public TaskLogsService(TaskLogsRepository taskLogsRepository) {
        this.taskLogsRepository = taskLogsRepository;
    }

    public void addComputeLogs(String chainTaskId, ComputeLogs computeLogs) {
        if (computeLogs == null) {
            return;
        }

        TaskLogs taskLogs = getTaskLogs(chainTaskId).orElse(new TaskLogs(chainTaskId));
        if (taskLogs.containsWalletAddress(computeLogs.getWalletAddress())) {
            return;
        }
        taskLogs.getComputeLogsList().add(computeLogs);
        taskLogsRepository.save(taskLogs);
    }

    public Optional<TaskLogs> getTaskLogs(String chainTaskId) {
        return taskLogsRepository.findOneByChainTaskId(chainTaskId);
    }

    public Optional<ComputeLogs> getComputeLogs(String chainTaskId, String walletAddress) {
        return taskLogsRepository.findByChainTaskIdAndWalletAddress(chainTaskId, walletAddress)
                .map(taskLogs -> taskLogs.getComputeLogsList().get(0));
    }

    public void delete(List<String> chainTaskIds) {
        taskLogsRepository.deleteByChainTaskIdIn(chainTaskIds);
    }
}
