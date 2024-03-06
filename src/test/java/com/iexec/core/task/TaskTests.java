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

package com.iexec.core.task;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;

import static com.iexec.core.task.TaskStatus.CONSENSUS_REACHED;
import static org.assertj.core.api.Assertions.assertThat;

class TaskTests {

    private final static String DAPP_NAME = "dappName";
    private final static String COMMAND_LINE = "commandLine";

    @Test
    void shouldInitializeProperly() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2);

        assertThat(task.getDateStatusList()).hasSize(1);
        assertThat(task.getDateStatusList().get(0).getStatus()).isEqualTo(TaskStatus.RECEIVED);
    }

    @Test
    void shouldSetCurrentStatus() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2);
        assertThat(task.getDateStatusList()).hasSize(1);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RECEIVED);

        task.changeStatus(TaskStatus.INITIALIZED);
        assertThat(task.getDateStatusList()).hasSize(2);
        assertThat(task.getDateStatusList().get(0).getStatus()).isEqualTo(TaskStatus.RECEIVED);
        assertThat(task.getDateStatusList().get(1).getStatus()).isEqualTo(TaskStatus.INITIALIZED);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.INITIALIZED);

        task.changeStatus(TaskStatus.RUNNING);
        assertThat(task.getDateStatusList()).hasSize(3);
        assertThat(task.getDateStatusList().get(2).getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void shouldGetCorrectLastStatusChange() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2);
        Date oneMinuteAgo = Date.from(Instant.now().minus(1L, ChronoUnit.MINUTES));

        TaskStatusChange latestChange = task.getLatestStatusChange();
        assertThat(latestChange.getStatus()).isEqualTo(TaskStatus.RECEIVED);

        task.changeStatus(TaskStatus.INITIALIZED);
        latestChange = task.getLatestStatusChange();
        assertThat(latestChange.getDate().after(oneMinuteAgo)).isTrue();
        assertThat(latestChange.getStatus()).isEqualTo(TaskStatus.INITIALIZED);

        task.changeStatus(TaskStatus.RUNNING);
        latestChange = task.getLatestStatusChange();
        assertThat(latestChange.getDate().after(oneMinuteAgo)).isTrue();
        assertThat(latestChange.getStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void shouldReturnTrueWhenConsensusReachedSinceAWhile() {
        final long maxExecutionTime = 60;
        Task task = new Task();
        task.setMaxExecutionTime(maxExecutionTime);
        TaskStatusChange taskStatusChange = TaskStatusChange.builder()
                .status(CONSENSUS_REACHED)
                .date(Date.from(Instant.now().minus(2 * maxExecutionTime, ChronoUnit.MILLIS)))
                .build();
        task.setDateStatusList(List.of(taskStatusChange));

        assertThat(task.isConsensusReachedSinceMultiplePeriods(1)).isTrue();
    }

    @Test
    void shouldReturnFalseWhenConsensusReachedSinceNotLong() {
        final long maxExecutionTime = 60;
        Task task = new Task();
        task.setMaxExecutionTime(maxExecutionTime);
        TaskStatusChange taskStatusChange = TaskStatusChange.builder()
                .status(CONSENSUS_REACHED)
                .date(Date.from(Instant.now().minus(10L, ChronoUnit.MILLIS)))
                .build();
        task.setDateStatusList(List.of(taskStatusChange));

        assertThat(task.isConsensusReachedSinceMultiplePeriods(1)).isFalse();
    }

    @Test
    void shouldContributionDeadlineBeReached() {
        Task task = new Task();
        // contribution deadline in the past
        task.setContributionDeadline(Date.from(Instant.now().minus(60L, ChronoUnit.MINUTES)));
        assertThat(task.isContributionDeadlineReached()).isTrue();
    }

    @Test
    void shouldContributionDeadlineNotBeReached() {
        Task task = new Task();
        // contribution deadline in the future
        task.setContributionDeadline(Date.from(Instant.now().plus(60L, ChronoUnit.MINUTES)));
        assertThat(task.isContributionDeadlineReached()).isFalse();
    }
}
