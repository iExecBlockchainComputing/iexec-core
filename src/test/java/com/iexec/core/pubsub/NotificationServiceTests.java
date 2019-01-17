package com.iexec.core.pubsub;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.iexec.common.result.TaskNotification;

public class NotificationServiceTests {

    @Mock
    private SimpMessagingTemplate sender;

    @Spy
    @InjectMocks
    private NotificationService notificationService;

    @Before
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