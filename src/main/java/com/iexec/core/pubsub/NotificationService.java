/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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

import com.iexec.commons.poco.notification.TaskNotification;
import com.iexec.core.chain.BlockchainConnectionHealthIndicator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Status;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationService {

    private final SimpMessagingTemplate sender;
    private final BlockchainConnectionHealthIndicator blockchainConnectionHealthIndicator;

    public NotificationService(SimpMessagingTemplate sender,
                               BlockchainConnectionHealthIndicator blockchainConnectionHealthIndicator) {
        this.sender = sender;
        this.blockchainConnectionHealthIndicator = blockchainConnectionHealthIndicator;
    }

    public void sendTaskNotification(TaskNotification taskNotification) {
        if (!blockchainConnectionHealthIndicator.isUp()) {
            log.debug("Blockchain is down. Task notification not sent [chainTaskId:{}, type:{}, workers:{}]",
                    taskNotification.getChainTaskId(), taskNotification.getTaskNotificationType(), taskNotification.getWorkersAddress());
            return;
        }

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
