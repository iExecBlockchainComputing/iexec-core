package com.iexec.core.pubsub;

import com.iexec.common.result.UploadResultMessage;
import com.iexec.core.task.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UploadService {

    private SimpMessagingTemplate sender;
    private TaskService taskService;

    public UploadService(SimpMessagingTemplate sender, TaskService taskService) {
        this.sender = sender;
        this.taskService = taskService;
    }

    @Scheduled(fixedRate = 3000)
    public void run(){
        log.info("Check if results need to be uploaded");
        //List<Replicates> uploadableReplicates = taskService.getUploadableReplicates();
        UploadResultMessage uploadResultMessage = UploadResultMessage.builder()
                .taskId(null)//TODO change it
                .workerAddress(null)//TODO change it
                .build();
        sender.convertAndSend("/topic/uploadResult", uploadResultMessage);

    }

}
