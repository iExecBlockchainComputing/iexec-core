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
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

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
                .completedTasks(1000)
                .dealEventsCount(3000)
                .dealsCount(1100)
                .latestBlockNumberWithDeal(BigInteger.valueOf(1_000_000L))
                .build();
        assertEquals(platformMetric, mapper.readValue(mapper.writeValueAsString(platformMetric), PlatformMetric.class));
    }
}
