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

package com.iexec.core.logs;

import java.util.List;
import java.util.Optional;

import com.iexec.common.replicate.ReplicateLogs;
import org.springframework.stereotype.Service;

@Service
public class ReplicateLogsService {

    private final ReplicateLogsRepository replicateLogsRepository;

    public ReplicateLogsService(ReplicateLogsRepository replicateLogsRepository) {
        this.replicateLogsRepository = replicateLogsRepository;
    }

    public void addReplicateLogs(String chainTaskId, ReplicateLogs replicateLogs) {
        TaskLogs taskLogs = getTaskLogs(chainTaskId).orElse(new TaskLogs(chainTaskId));
        if (taskLogs.containsWalletAddress(replicateLogs.getWalletAddress())) {
            return;
        }
        taskLogs.getReplicateLogsList().add(replicateLogs);
        replicateLogsRepository.save(taskLogs);
    }

    public Optional<TaskLogs> getTaskLogs(String chainTaskId) {
        return replicateLogsRepository.findOneByChainTaskId(chainTaskId);
    }

    public Optional<ReplicateLogs> getReplicateLogs(String chainTaskId, String walletAddress) {
        return replicateLogsRepository.findByChainTaskIdAndWalletAddress(chainTaskId, walletAddress)
                .map(taskLogs -> taskLogs.getReplicateLogsList().get(0));
    }

    public void delete(List<String> chainTaskIds) {
        replicateLogsRepository.deleteByChainTaskIdIn(chainTaskIds);
    }
}
