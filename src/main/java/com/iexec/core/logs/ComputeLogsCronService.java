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

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.iexec.core.task.TaskService;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ComputeLogsCronService {

    @Value("${logs.availability-period-in-days}")
    private int availabilityDays;

    private final ComputeLogsService computeLogsService;
    private final TaskService taskService;

    public ComputeLogsCronService(
        ComputeLogsService computeLogsService,
        TaskService taskService
    ) {
        this.computeLogsService = computeLogsService;
        this.taskService = taskService;
    }

    @Scheduled(
            fixedRateString = "${logs.purge-rate-in-days}",
            timeUnit = TimeUnit.DAYS)
    void purgeLogs() {
        Date someDaysAgo = DateUtils.addDays(new Date(), -availabilityDays);
        List<String> chainTaskIds = taskService
                .getChainTaskIdsOfTasksExpiredBefore(someDaysAgo);
        computeLogsService.delete(chainTaskIds);
    }

}