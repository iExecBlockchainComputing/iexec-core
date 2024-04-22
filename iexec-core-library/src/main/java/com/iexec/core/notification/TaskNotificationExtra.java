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

package com.iexec.core.notification;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.task.TaskAbortCause;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = TaskNotificationExtra.TaskNotificationExtraBuilder.class)
public class TaskNotificationExtra {
    WorkerpoolAuthorization workerpoolAuthorization;
    String smsUrl;

    // block number from which this notification makes sense
    // (it is not used for all notification types)
    long blockNumber;

    // The reason behind an "Abort" notification. Used only for
    // Abort notifications.
    TaskAbortCause taskAbortCause;

    @JsonPOJOBuilder(withPrefix = "")
    public static class TaskNotificationExtraBuilder {
    }
}
