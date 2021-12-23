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

package com.iexec.core.detector.task;

import com.iexec.core.detector.Detector;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
public class FinalDeadlineTaskDetector implements Detector {

    private final TaskService taskService;
    private final TaskUpdateRequestManager taskUpdateRequestManager;

    public FinalDeadlineTaskDetector(TaskService taskService, TaskUpdateRequestManager taskUpdateRequestManager) {
        this.taskService = taskService;
        this.taskUpdateRequestManager = taskUpdateRequestManager;
    }

    @Scheduled(fixedRateString = "#{@cronConfiguration.getFinalDeadline()}")
    @Override
    public void detect() {
        log.debug("Trying to detect final deadline");
        for (Task task : taskService.getTasksWhereFinalDeadlineIsPossible()) {
            Date now = new Date();
            if (task.getFinalDeadline() != null && now.after(task.getFinalDeadline())) {
                log.info("Task after final deadline found [chainTaskId:{}]", task.getChainTaskId());
                taskUpdateRequestManager.publishRequest(task.getChainTaskId());
            }
        }
    }
}
