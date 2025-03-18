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

package com.iexec.core.metric;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.iexec.core.task.TaskStatus;
import lombok.Builder;
import lombok.Value;

import java.math.BigInteger;
import java.util.LinkedHashMap;

@Value
@Builder
@JsonDeserialize(builder = PlatformMetric.PlatformMetricBuilder.class)
public class PlatformMetric {
    int aliveWorkers;
    int aliveComputingCpu;
    int aliveRegisteredCpu;
    int aliveComputingGpu;
    int aliveRegisteredGpu;
    LinkedHashMap<TaskStatus, Long> currentTaskStatusesCount;
    LatestBlockMetric latestBlockMetric;
    long dealEventsCount;
    long dealsCount;
    long replayDealsCount;
    BigInteger latestBlockNumberWithDeal;

    // region backward compatibility
    /**
     * @deprecated Use aliveComputingCpu = aliveRegisteredCpu - aliveComputingCpu instead
     */
    @Deprecated(forRemoval = true, since = "9.0.0")
    int aliveAvailableCpu;
    /**
     * @deprecated Use aliveRegisteredCpu instead
     */
    @Deprecated(forRemoval = true, since = "9.0.0")
    int aliveTotalCpu;
    /**
     * @deprecated Use aliveComputingGpu = aliveRegisteredGpu - aliveComputingGpu instead
     */
    @Deprecated(forRemoval = true, since = "9.0.0")
    int aliveAvailableGpu;
    /**
     * @deprecated Use aliveRegisteredGpu instead
     */
    @Deprecated(forRemoval = true, since = "9.0.0")
    int aliveTotalGpu;
    // endregion

    @JsonPOJOBuilder(withPrefix = "")
    public static class PlatformMetricBuilder {
    }

    public record LatestBlockMetric(long blockNumber, String blockHash, long blockTimestamp) {
    }
}
