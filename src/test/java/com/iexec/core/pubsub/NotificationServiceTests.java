/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.pubsub;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.iexec.common.notification.TaskNotification;

public class NotificationServiceTests {

    @Mock
    private SimpMessagingTemplate sender;

    @Spy
    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    public void init() { MockitoAnnotations.initMocks(this); }

    @Test
    public void shouldSendTaskNotification() {
        String chainTaskId = "chainTaskId";
        TaskNotification taskNotification = TaskNotification.builder()
            .chainTaskId(chainTaskId)
            .build();

        notificationService.sendTaskNotification(taskNotification);

        String destination = "/topic/task/" + taskNotification.getChainTaskId();

        Mockito.verify(sender, Mockito.times(1))
            .convertAndSend(destination, taskNotification);
    }
}