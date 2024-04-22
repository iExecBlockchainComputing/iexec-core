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

import com.iexec.commons.poco.task.TaskAbortCause;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskNotificationTests {

    @Test
    void shouldGetAbortNotificationCause() {
        TaskNotification notif1 = TaskNotification.builder()
                .chainTaskId("chainTaskId")
                .taskNotificationExtra(TaskNotificationExtra.builder()
                        .taskAbortCause(TaskAbortCause.CONTRIBUTION_TIMEOUT)
                        .build())
                .build();
        assertEquals(TaskAbortCause.CONTRIBUTION_TIMEOUT, notif1.getTaskAbortCause());

        TaskNotification notif2 = TaskNotification.builder()
                .chainTaskId("chainTaskId")
                // Reason not specified
                .build();
        assertEquals(TaskAbortCause.UNKNOWN, notif2.getTaskAbortCause());
    }
}
