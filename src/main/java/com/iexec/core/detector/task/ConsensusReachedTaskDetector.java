/*
 * Copyright 2024 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.detector.task;

import com.iexec.core.chain.IexecHubService;
import com.iexec.core.detector.Detector;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import static com.iexec.commons.poco.chain.ChainTaskStatus.REVEALING;
import static com.iexec.core.task.TaskStatus.RUNNING;

@Slf4j
public class ConsensusReachedTaskDetector implements Detector {

    private final IexecHubService iexecHubService;
    private final TaskService taskService;
    private final TaskUpdateRequestManager taskUpdateRequestManager;

    public ConsensusReachedTaskDetector(IexecHubService iexecHubService,
                                        TaskService taskService,
                                        TaskUpdateRequestManager taskUpdateRequestManager) {
        this.iexecHubService = iexecHubService;
        this.taskService = taskService;
        this.taskUpdateRequestManager = taskUpdateRequestManager;
    }

    @Scheduled(fixedRateString = "#{@cronConfiguration.getConsensusReached()}")
    @Override
    public void detect() {
        log.debug("Trying to detect running tasks with on-chain consensus");
        taskService.findByCurrentStatus(RUNNING).stream()
                .filter(this::isConsensusReached)
                .forEach(this::publishTaskUpdateRequest);
    }

    private boolean isConsensusReached(Task task) {
        return iexecHubService.getChainTask(task.getChainTaskId()).stream()
                .allMatch(chainTask -> chainTask.getStatus() == REVEALING);
    }

    private void publishTaskUpdateRequest(Task task) {
        log.info("Detected confirmed missing update (task) [is:{}, should:{}, taskId:{}]",
                task.getCurrentStatus(), TaskStatus.CONSENSUS_REACHED, task.getChainTaskId());
        taskUpdateRequestManager.publishRequest(task.getChainTaskId());
    }
}
