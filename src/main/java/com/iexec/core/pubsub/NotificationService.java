package com.iexec.core.pubsub;

import com.iexec.common.result.TaskNotification;
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
        sender.convertAndSend("/topic/task/" + taskNotification.getTaskId(), taskNotification);
        log.info("Sent TaskNotification [taskId:{}, type:{}, worker:{}]", taskNotification.getTaskId(), taskNotification.getTaskNotificationType(), taskNotification.getWorkerAddress());
    }

    /* Test PubSub method
    @Scheduled(fixedRate = 3000)
    public void run(){
        log.info("Check if results need to be uploaded");

        //List<Replicates> uploadableReplicates = taskService.getUploadableReplicates();
        UploadResultMessage uploadResultMessage = UploadResultMessage.builder()
                .taskId(null)
                .workerAddress(null)
                .build();
        sendTaskNotification(uploadResultMessage);

    }*/

}
