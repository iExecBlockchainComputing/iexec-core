package com.iexec.core.pubsub;

import com.iexec.common.result.UploadResultMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UploadService {

    private SimpMessagingTemplate sender;

    public UploadService(SimpMessagingTemplate sender) {
        this.sender = sender;
    }

    public void requestUpload(UploadResultMessage uploadResultMessage) {
        sender.convertAndSend("/topic/uploadResult", uploadResultMessage);
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
        requestUpload(uploadResultMessage);

    }*/

}
