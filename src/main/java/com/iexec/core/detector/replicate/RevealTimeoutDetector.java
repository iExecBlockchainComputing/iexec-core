/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.detector.replicate;

import com.iexec.core.detector.Detector;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.iexec.core.task.TaskStatus.*;

@Slf4j
@Service
public class RevealTimeoutDetector implements Detector {

    private final TaskService taskService;
    private final ReplicatesService replicatesService;

    public RevealTimeoutDetector(final TaskService taskService,
                                 final ReplicatesService replicatesService) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
    }

    @Scheduled(fixedRateString = "#{@cronConfiguration.getRevealTimeout()}")
    @Override
    public void detect() {
        log.debug("Trying to detect reveal timeout");
        detectTaskAfterRevealDealLineWithZeroReveal(); // can be reopened
        detectTaskAfterRevealDealLineWithAtLeastOneReveal(); // can be finalized
    }

    private void detectTaskAfterRevealDealLineWithAtLeastOneReveal() {
        List<Task> tasks = new ArrayList<>(taskService.findByCurrentStatus(Arrays.asList(AT_LEAST_ONE_REVEALED,
                RESULT_UPLOADING, RESULT_UPLOADED)));

        for (Task task : tasks) {
            Date now = new Date();
            if (now.after(task.getRevealDeadline())) {
                for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                    replicatesService.setRevealTimeoutStatusIfNeeded(task.getChainTaskId(), replicate);
                }
                log.info("Found task after revealDeadline with at least one reveal, could be finalized [chainTaskId:{}]", task.getChainTaskId());
            }
        }
    }

    private void detectTaskAfterRevealDealLineWithZeroReveal() {
        for (Task task : taskService.findByCurrentStatus(TaskStatus.CONSENSUS_REACHED)) {
            Date now = new Date();
            if (now.after(task.getRevealDeadline())) {
                // update all replicates status attached to this task
                for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                    replicatesService.setRevealTimeoutStatusIfNeeded(task.getChainTaskId(), replicate);
                }
                log.info("Found task after revealDeadline with zero reveal, could be reopened [chainTaskId:{}]", task.getChainTaskId());
            }
        }
    }
}