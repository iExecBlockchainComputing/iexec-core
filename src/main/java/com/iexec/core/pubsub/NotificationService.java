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

import com.iexec.common.notification.TaskNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {

    private SimpMessagingTemplate sender;

    public NotificationService(SimpMessagingTemplate sender) {
        this.sender = sender;
    }

    public void sendTaskNotification(TaskNotification taskNotification) {
        sender.convertAndSend("/topic/task/" + taskNotification.getChainTaskId(), taskNotification);
        log.info("Sent TaskNotification [chainTaskId:{}, type:{}, workers:{}]",
                taskNotification.getChainTaskId(), taskNotification.getTaskNotificationType(), taskNotification.getWorkersAddress());
    }

    /* Test PubSub method
    @Scheduled(fixedRate = 3000)
    public void run(){
        log.info("Check if results need to be uploaded");

        //List<ReplicatesList> uploadableReplicates = taskService.getUploadableReplicates();
        UploadResultMessage uploadResultMessage = UploadResultMessage.builder()
                .taskId(null)
                .workerAddress(null)
                .build();
        sendTaskNotification(uploadResultMessage);

    }*/

}
