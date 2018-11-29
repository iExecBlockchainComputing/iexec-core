package com.iexec.core.task.listener;

import com.iexec.common.result.TaskNotification;
import com.iexec.common.result.TaskNotificationType;
import com.iexec.core.pubsub.NotificationService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskExecutorEngine;
import com.iexec.core.task.event.ConsensusReachedEvent;
import com.iexec.core.task.event.PleaseUploadEvent;
import com.iexec.core.task.event.TaskCompletedEvent;
import com.iexec.core.task.event.TaskCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class TaskListeners {

    private TaskExecutorEngine taskExecutorEngine;
    private NotificationService notificationService;
    private ReplicatesService replicatesService;

    public TaskListeners(TaskExecutorEngine taskExecutorEngine,
                         NotificationService notificationService,
                         ReplicatesService replicatesService) {
        this.taskExecutorEngine = taskExecutorEngine;
        this.notificationService = notificationService;
        this.replicatesService = replicatesService;
    }

    @EventListener
    public void onTaskCreatedEvent(TaskCreatedEvent event) {
        Task task = event.getTask();
        log.info("Received TaskCreatedEvent [chainDealId:{}, taskIndex:{}] ",
                task.getChainDealId(), task.getTaskIndex());
        taskExecutorEngine.updateTask(task);
    }

    // when a task is finalized, all workers need to be informed
    // the task should also be removed from the executor
    @EventListener
    public void onTaskCompletedEvent(TaskCompletedEvent event) {
        Task task = event.getTask();
        String chainTaskId = task.getChainTaskId();
        log.info("Received TaskCompletedEvent [chainTaskId:{}] ", chainTaskId);

        taskExecutorEngine.removeTaskExecutor(task);

        notificationService.sendTaskNotification(TaskNotification.builder()
                .chainTaskId(chainTaskId)
                .taskNotificationType(TaskNotificationType.COMPLETED)
                .workersAddress(Collections.emptyList())
                .build());
    }

    @EventListener
    public void onTaskConsensusReached(ConsensusReachedEvent event) {
        String chainTaskId = event.getChainTaskId();
        log.info("Received ConsensusReachedEvent [chainTaskId:{}] ", chainTaskId);

        String winningHash = event.getConsensus();

        List<String> winners = new ArrayList<>();
        List<String> losers = new ArrayList<>();
        for (Replicate replicate : replicatesService.getReplicates(chainTaskId)) {
            if (replicate.getContributionHash().equals(winningHash)) {
                winners.add(replicate.getWalletAddress());
            } else {
                losers.add(replicate.getWalletAddress());
            }
        }

        // winners: please reveal
        if (!winners.isEmpty()) {
            notificationService.sendTaskNotification(TaskNotification.builder()
                    .taskNotificationType(TaskNotificationType.PLEASE_REVEAL)
                    .chainTaskId(chainTaskId)
                    .workersAddress(winners).build()
            );
        }

        // losers: please abort
        if (!losers.isEmpty()) {
            notificationService.sendTaskNotification(TaskNotification.builder()
                    .taskNotificationType(TaskNotificationType.PLEASE_ABORT_CONSENSUS_REACHED)
                    .chainTaskId(chainTaskId)
                    .workersAddress(losers).build()
            );
        }
    }

    @EventListener
    public void onPleaseUploadEvent(PleaseUploadEvent event) {
        String chainTaskId = event.getChainTaskId();
        String workerWallet = event.getWorkerWallet();

        log.info("Received PleaseUploadEvent [chainTaskId:{}, workerWallet:{}] ", chainTaskId, workerWallet);
        notificationService.sendTaskNotification(TaskNotification.builder()
                .chainTaskId(chainTaskId)
                .workersAddress(Collections.singletonList(workerWallet))
                .taskNotificationType(TaskNotificationType.PLEASE_UPLOAD)
                .build());
        log.info("NotifyUploadingWorker completed[uploadingWorkerWallet={}]", workerWallet);
    }
}
