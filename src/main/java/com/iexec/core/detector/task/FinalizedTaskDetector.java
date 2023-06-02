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

package com.iexec.core.detector.task;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.commons.poco.chain.ChainTask;
import com.iexec.commons.poco.chain.ChainTaskStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.detector.Detector;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class FinalizedTaskDetector implements Detector {

    private final TaskService taskService;
    private final TaskUpdateRequestManager taskUpdateRequestManager;
    private final IexecHubService iexecHubService;
    private final ReplicatesService replicatesService;

    public FinalizedTaskDetector(TaskService taskService,
                                 TaskUpdateRequestManager taskUpdateRequestManager,
                                 IexecHubService iexecHubService,
                                 ReplicatesService replicatesService) {
        this.taskService = taskService;
        this.taskUpdateRequestManager = taskUpdateRequestManager;
        this.iexecHubService = iexecHubService;
        this.replicatesService = replicatesService;
    }

    @Scheduled(fixedRateString = "#{@cronConfiguration.getFinalize()}")
    @Override
    public void detect() {
        detectFinalizedTasks();
        detectContributeAndFinalizeDoneTasks();
    }

    /**
     * Detect tasks that are finalizing but are not finalized yet.
     */
    void detectFinalizedTasks() {
        log.debug("Trying to detect finalized tasks");
        taskService.findByCurrentStatus(TaskStatus.FINALIZING)
                .stream()
                .filter(this::isChainTaskCompleted)
                .forEach(this::publishTaskUpdateRequest);
    }

    /**
     * Detect tasks that are contributed and finalized by worker
     * but are not off-chain finalized yet.
     */
    void detectContributeAndFinalizeDoneTasks() {
        log.debug("Trying to detect contributed and finalized tasks");
        taskService.findByCurrentStatus(TaskStatus.RUNNING)
                .stream()
                .filter(this::isTaskContributeAndFinalizeDone)
                .forEach(this::publishTaskUpdateRequest);
    }

    boolean isChainTaskCompleted(Task task) {
        final Optional<ChainTask> chainTask = iexecHubService.getChainTask(task.getChainTaskId());
        return chainTask.isPresent() && chainTask.get().getStatus() == ChainTaskStatus.COMPLETED;
    }

    boolean isTaskContributeAndFinalizeDone(Task task) {
        // Only TEE tasks can follow ContributeAndFinalize workflow
        if (!task.isTeeTask()) {
            return false;
        }

        final List<Replicate> replicates = replicatesService.getReplicates(task.getChainTaskId());
        return replicates.size() == 1
                && replicates.get(0).containsStatus(ReplicateStatus.CONTRIBUTE_AND_FINALIZE_DONE)
                && isChainTaskCompleted(task);
    }

    void publishTaskUpdateRequest(Task task) {
        log.info("Detected confirmed missing update (task) [is:{}, should:{}, taskId:{}]",
                task.getCurrentStatus(), TaskStatus.FINALIZED, task.getChainTaskId());
        taskUpdateRequestManager.publishRequest(task.getChainTaskId());
    }
}
