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
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.TaskUpdateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;


@Slf4j
@Service
public class TaskResultUploadTimeoutDetector implements Detector {

    private final TaskService taskService;
    private final TaskUpdateManager taskUpdateManager;

    public TaskResultUploadTimeoutDetector(TaskService taskService, TaskUpdateManager taskUpdateManager) {
        this.taskService = taskService;
        this.taskUpdateManager = taskUpdateManager;
    }

    @Scheduled(fixedRateString = "#{@cronConfiguration.getResultUploadTimeout()}")
    @Override
    public void detect() {
        log.debug("Trying to detect tasks with upload timeout");

        List<Task> inUploadStatus = taskService.findByCurrentStatus(TaskStatus.RESULT_UPLOADING);

        for (Task task : inUploadStatus) {
            String chainTaskId = task.getChainTaskId();

            boolean isNowAfterFinalDeadline = task.getFinalDeadline() != null && new Date().after(task.getFinalDeadline());

            if (isNowAfterFinalDeadline) {
                log.info("found task in status {} after final deadline [chainTaskId:{}]",
                        task.getCurrentStatus(), chainTaskId);
                taskUpdateManager.publishUpdateTaskRequest(task.getChainTaskId());
            }
        }
    }
}
