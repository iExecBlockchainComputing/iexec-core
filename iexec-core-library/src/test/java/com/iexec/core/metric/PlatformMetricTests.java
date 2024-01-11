/*
 * Copyright 2023-2023 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.metric;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iexec.core.task.TaskStatus;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformMetricTests {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldSerializeAndDeserialize() throws JsonProcessingException {
        final PlatformMetric platformMetric = PlatformMetric.builder()
                .aliveWorkers(4)
                .aliveTotalCpu(12)
                .aliveAvailableCpu(7)
                .aliveTotalGpu(0)
                .aliveAvailableGpu(0)
                .currentTaskStatusesCount(createCurrentTaskStatusesCount())
                .dealEventsCount(3000)
                .dealsCount(1100)
                .latestBlockNumberWithDeal(BigInteger.valueOf(1_000_000L))
                .build();
        assertEquals(platformMetric, mapper.readValue(mapper.writeValueAsString(platformMetric), PlatformMetric.class));
    }

    private LinkedHashMap<TaskStatus, Long> createCurrentTaskStatusesCount() {
        final LinkedHashMap<TaskStatus, Long> expectedCurrentTaskStatusesCount = new LinkedHashMap<>(TaskStatus.values().length);
        expectedCurrentTaskStatusesCount.put(TaskStatus.RECEIVED, 1L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.INITIALIZING, 2L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.INITIALIZED, 3L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.INITIALIZE_FAILED, 4L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.RUNNING, 5L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.RUNNING_FAILED, 6L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.CONTRIBUTION_TIMEOUT, 7L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.CONSENSUS_REACHED, 8L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.REOPENING, 9L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.REOPENED, 10L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.REOPEN_FAILED, 11L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.AT_LEAST_ONE_REVEALED, 12L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.RESULT_UPLOADING, 13L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.RESULT_UPLOADED, 14L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.RESULT_UPLOAD_TIMEOUT, 15L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.FINALIZING, 16L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.FINALIZED, 17L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.FINALIZE_FAILED, 18L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.FINAL_DEADLINE_REACHED, 19L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.COMPLETED, 20L);
        expectedCurrentTaskStatusesCount.put(TaskStatus.FAILED, 21L);

        return expectedCurrentTaskStatusesCount;
    }
}
