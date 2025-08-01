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

package com.iexec.core.task;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static com.iexec.core.TestUtils.COMMAND_LINE;
import static com.iexec.core.TestUtils.DAPP_NAME;
import static org.assertj.core.api.Assertions.assertThat;

class TaskTests {

    @Test
    void shouldInitializeProperly() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2);

        assertThat(task.getDateStatusList()).hasSize(1);
        assertThat(task.getDateStatusList().get(0).getStatus()).isEqualTo(TaskStatus.RECEIVED);
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
