/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.TaskStatusChange;
import com.iexec.core.task.event.ContributionTimeoutEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
public class ContributionTimeoutTaskDetector implements Detector {

    private final TaskService taskService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ContributionTimeoutTaskDetector(TaskService taskService, ApplicationEventPublisher applicationEventPublisher) {
        this.taskService = taskService;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Scheduled(fixedRateString = "#{@cronConfiguration.getContribute()}")
    @Override
    public void detect() {
        log.debug("Detect tasks after contribution deadline");
        final Instant now = Instant.now();
        final Query query = Query.query(Criteria.where("currentStatus").in(TaskStatus.INITIALIZED, TaskStatus.RUNNING)
                .and("contributionDeadline").lte(now)
                .and("finalDeadline").gt(now));
        final Update update = Update.update("currentStatus", TaskStatus.FAILED)
                .push("dateStatusList").each(
                        TaskStatusChange.builder().status(TaskStatus.CONTRIBUTION_TIMEOUT).build(),
                        TaskStatusChange.builder().status(TaskStatus.FAILED).build());
        taskService.updateMultipleTasksByQuery(query, update)
                .forEach(id -> applicationEventPublisher.publishEvent(new ContributionTimeoutEvent(id)));
    }
}
