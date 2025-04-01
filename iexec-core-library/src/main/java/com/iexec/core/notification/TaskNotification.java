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

package com.iexec.core.notification;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
@JsonDeserialize(builder = TaskNotification.TaskNotificationBuilder.class)
public class TaskNotification {
    // Id of the task concerned by the notification.
    String chainTaskId;

    // List of workers targeted by the notification.
    List<String> workersAddress;

    // Type of the notification.
    TaskNotificationType taskNotificationType;

    // Optional extra metadata provided with the notification
    TaskNotificationExtra taskNotificationExtra;

    /**
     * Gets the abort cause of this task.
     *
     * @return the cause from the notification details if defined, {@code TaskAbortCause.UNKNOWN otherwise}
     */
    public TaskAbortCause getTaskAbortCause() {
        return taskNotificationExtra != null && taskNotificationExtra.getTaskAbortCause() != null
                ? taskNotificationExtra.getTaskAbortCause()
                : TaskAbortCause.UNKNOWN;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class TaskNotificationBuilder {
    }
}
