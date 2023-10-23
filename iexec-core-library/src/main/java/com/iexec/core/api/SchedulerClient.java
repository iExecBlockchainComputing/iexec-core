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

package com.iexec.core.api;

import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerModel;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.common.replicate.ReplicateTaskSummary;
import com.iexec.commons.poco.notification.TaskNotification;
import com.iexec.commons.poco.notification.TaskNotificationType;
import feign.Headers;
import feign.Param;
import feign.RequestLine;

import java.security.Signature;
import java.util.List;

public interface SchedulerClient {

    @RequestLine("GET /version")
    String getCoreVersion();

    // region /workers
    @RequestLine("GET /workers/challenge?walletAddress={walletAddress}")
    String getChallenge(@Param("walletAddress") String walletAddress);

    @RequestLine("POST /workers/login?walletAddress={walletAddress}")
    String login(@Param("walletAddress") String walletAddress, Signature signature);

    @RequestLine("POST /workers/ping")
    @Headers("Authorization: {authorization}")
    String ping(@Param("authorization") String authorization);

    @RequestLine("POST /workers/register")
    @Headers("Authorization: {authorization}")
    void registerWorker(@Param("authorization") String authorization, WorkerModel model);

    @RequestLine("GET /workers/config")
    PublicConfiguration getPublicConfiguration();

    @RequestLine("GET /workers/computing")
    @Headers("Authorization: {authorization}")
    List<String> getComputingTasks(@Param("authorization") String authorization);
    // endregion

    // region /replicates
    @RequestLine("GET /replicates/available?blockNumber={blockNumber}")
    @Headers("Authorization: {authorization}")
    ReplicateTaskSummary getAvailableReplicateTaskSummary(
            @Param("authorization") String authorization, @Param("blockNumber") long blockNumber);

    @RequestLine("GET /replicates/interrupted?blockNumber={blockNumber}")
    @Headers("Authorization: {authorization}")
    List<TaskNotification> getMissedTaskNotifications(
            @Param("authorization") String authorization, @Param("blockNumber") long blockNumber);

    @RequestLine("POST /replicates/{chainTaskId}/updateStatus")
    @Headers("Authorization: {authorization}")
    TaskNotificationType updateReplicateStatus(
            @Param("authorization") String authorization,
            @Param("chainTaskId") String chainTaskId,
            ReplicateStatusUpdate replicateStatusUpdate
    );
    // endregion
}
