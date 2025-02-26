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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class TaskNotificationTests {
    @ParameterizedTest
    @EnumSource(value = TaskNotificationType.class)
    void shouldBuildTaskNotification(TaskNotificationType notificationType) {
        final TaskNotification notification = TaskNotification.builder()
                .taskNotificationType(notificationType)
                .build();
        assertThat(notification.getTaskNotificationType()).isEqualTo(notificationType);
    }

    @Test
    void shouldGetAbortNotificationCause() {
        TaskNotification notif1 = TaskNotification.builder()
                .chainTaskId("chainTaskId")
                .taskNotificationExtra(TaskNotificationExtra.builder()
                        .taskAbortCause(TaskAbortCause.CONTRIBUTION_TIMEOUT)
                        .build())
                .build();
        assertThat(notif1.getTaskAbortCause()).isEqualTo(TaskAbortCause.CONTRIBUTION_TIMEOUT);

        TaskNotification notif2 = TaskNotification.builder()
                .chainTaskId("chainTaskId")
                // Reason not specified
                .build();
        assertThat(notif2.getTaskAbortCause()).isEqualTo(TaskAbortCause.UNKNOWN);
    }
}
