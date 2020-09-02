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

package com.iexec.core.stdout;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

@Service
public class StdoutService {

    private StdoutRepository stdoutRepository;

    public StdoutService(StdoutRepository stdoutRepository) {
        this.stdoutRepository = stdoutRepository;
    }

    public void addReplicateStdout(String chainTaskId, String walletAddress, String stdout) {
        ReplicateStdout newReplicateStdout = new ReplicateStdout(walletAddress, stdout);
        TaskStdout taskStdout = getTaskStdout(chainTaskId).orElse(new TaskStdout(chainTaskId));
        if (taskStdout.containsWalletAddress(walletAddress)) {
            return;
        }
        taskStdout.getReplicateStdoutList().add(newReplicateStdout);
        stdoutRepository.save(taskStdout);
    }

    public Optional<TaskStdout> getTaskStdout(String chainTaskId) {
        return stdoutRepository.findOneByChainTaskId(chainTaskId);
    }

    public Optional<ReplicateStdout> getReplicateStdout(String chainTaskId, String walletAddress) {
        return stdoutRepository.findByChainTaskIdAndWalletAddress(chainTaskId, walletAddress)
                .map(taskStdout -> taskStdout.getReplicateStdoutList().get(0));
    }

    public void delete(List<String> chainTaskIds) {
        stdoutRepository.deleteByChainTaskIdIn(chainTaskIds);
    }
}
