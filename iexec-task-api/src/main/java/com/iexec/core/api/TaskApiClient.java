/*
 * Copyright 2024 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.api;

import com.iexec.common.replicate.ComputeLogs;
import com.iexec.commons.poco.eip712.entity.EIP712Challenge;
import com.iexec.core.logs.TaskLogsModel;
import com.iexec.core.metric.PlatformMetric;
import com.iexec.core.task.TaskModel;
import feign.Headers;
import feign.Param;
import feign.RequestLine;

public interface TaskApiClient {
    @RequestLine("GET /metrics")
    PlatformMetric getMetrics();

    // region /tasks
    @RequestLine("GET /tasks/{chainTaskId}")
    TaskModel getTask(@Param("chainTaskId") String chainTaskId);

    @RequestLine("GET /tasks/logs/challenge?address={address}")
    EIP712Challenge getTaskLogsChallenge(@Param("address") String address);

    @Headers("Authorization: {authorization}")
    @RequestLine("GET /tasks/{chainTaskId}/logs")
    TaskLogsModel getTaskLogs(
            @Param("chainTaskId") String chainTaskId,
            @Param("authorization") String authorization);

    @Headers("Authorization: {authorization}")
    @RequestLine("GET /tasks/{chainTaskId}/replicates/{walletAddress}/logs")
    ComputeLogs getComputeLogs(
            @Param("chainTaskId") String chainTaskId,
            @Param("walletAddress") String walletAddress,
            @Param("authorization") String authorization);
    // endregion
}
