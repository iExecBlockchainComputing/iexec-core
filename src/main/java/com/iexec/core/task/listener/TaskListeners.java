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

package com.iexec.core.task.listener;

import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.task.TaskAbortCause;
import com.iexec.core.pubsub.NotificationService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskUpdateManager;
import com.iexec.core.task.event.*;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class TaskListeners {

    private final TaskUpdateManager taskUpdateManager;
    private final NotificationService notificationService;
    private final ReplicatesService replicatesService;
    private final WorkerService workerService;

    public TaskListeners(TaskUpdateManager taskUpdateManager,
                         NotificationService notificationService,
                         ReplicatesService replicatesService,
                         WorkerService workerService) {
        this.taskUpdateManager = taskUpdateManager;
        this.notificationService = notificationService;
        this.replicatesService = replicatesService;
        this.workerService = workerService;
    }


    @EventListener
    public void onTaskCreatedEvent(TaskCreatedEvent event) {
        log.info("Received TaskCreatedEvent [chainTaskId:{}]", event.getChainTaskId());
        taskUpdateManager.publishUpdateTaskRequest(event.getChainTaskId());
    }

    @EventListener
    public void onTaskContributionTimeout(ContributionTimeoutEvent event) {
        String chainTaskId = event.getChainTaskId();
        log.info("Received ContributionTimeoutEvent [chainTaskId:{}] ", chainTaskId);

        List<String> workerAddresses = new ArrayList<>();
        for (Replicate replicate : replicatesService.getReplicates(chainTaskId)) {
            workerAddresses.add(replicate.getWalletAddress());
            workerService.removeChainTaskIdFromWorker(chainTaskId, replicate.getWalletAddress());
        }

        notificationService.sendTaskNotification(TaskNotification.builder()
                .chainTaskId(chainTaskId)
                .workersAddress(workerAddresses)
                .taskNotificationType(TaskNotificationType.PLEASE_ABORT)
                .taskNotificationExtra(TaskNotificationExtra.builder()
                        .taskAbortCause(TaskAbortCause.CONTRIBUTION_TIMEOUT)
                        .build())
                .build());
        log.info("NotifyAbortContributionTimeout completed[workerAddresses:{}]", workerAddresses);
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
            TaskNotificationExtra notificationExtra = TaskNotificationExtra.builder()
                    .blockNumber(event.getBlockNumber())
                    .build();

            notificationService.sendTaskNotification(TaskNotification.builder()
                    .taskNotificationType(TaskNotificationType.PLEASE_REVEAL)
                    .chainTaskId(chainTaskId)
                    .taskNotificationExtra(notificationExtra)
                    .workersAddress(winners).build()
            );
        }

        // losers: please abort
        if (!losers.isEmpty()) {
            notificationService.sendTaskNotification(TaskNotification.builder()
                    .chainTaskId(chainTaskId)
                    .workersAddress(losers)
                    .taskNotificationType(TaskNotificationType.PLEASE_ABORT)
                    .taskNotificationExtra(TaskNotificationExtra.builder()
                            .taskAbortCause(TaskAbortCause.CONSENSUS_REACHED)
                            .build())
                    .build()
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

    @EventListener
    public void onResultUploadTimeoutEvent(ResultUploadTimeoutEvent event) {
        String chainTaskId = event.getChainTaskId();
        log.info("Received ResultUploadTimeoutEvent [chainTaskId:{}] ", chainTaskId);
    }

    // when a task is finalized, all workers need to be informed
    // the task should also be removed from the executor
    @EventListener
    public void onTaskCompletedEvent(TaskCompletedEvent event) {
        Task task = event.getTask();
        String chainTaskId = task.getChainTaskId();
        log.info("Received TaskCompletedEvent [chainTaskId:{}] ", chainTaskId);


        notificationService.sendTaskNotification(TaskNotification.builder()
                .chainTaskId(chainTaskId)
                .taskNotificationType(TaskNotificationType.PLEASE_COMPLETE)
                .workersAddress(Collections.emptyList())
                .build());

        removeChainTaskIdFromWorkers(chainTaskId);
    }

    @EventListener
    public void onTaskFailedEvent(TaskFailedEvent event) {
        String chainTaskId = event.getChainTaskId();
        log.info("Received TaskFailedEvent [chainTaskId:{}] ", chainTaskId);

        notificationService.sendTaskNotification(TaskNotification.builder()
                .chainTaskId(chainTaskId)
                .taskNotificationType(TaskNotificationType.PLEASE_ABORT)
                .workersAddress(Collections.emptyList())
                .build());

        removeChainTaskIdFromWorkers(chainTaskId);
    }

    @EventListener
    public void onTaskRunningFailedEvent(TaskRunningFailedEvent event) {
        String chainTaskId = event.getChainTaskId();
        log.info("Received TaskRunningFailedEvent [chainTaskId:{}] ", chainTaskId);

        notificationService.sendTaskNotification(TaskNotification.builder()
                .chainTaskId(chainTaskId)
                .taskNotificationType(TaskNotificationType.PLEASE_ABORT)
                .workersAddress(Collections.emptyList())
                .build());

        removeChainTaskIdFromWorkers(chainTaskId);
    }

    private void removeChainTaskIdFromWorkers(String chainTaskId) {
        for (Replicate replicate : replicatesService.getReplicates(chainTaskId)) {
            workerService.removeChainTaskIdFromWorker(chainTaskId, replicate.getWalletAddress());
        }
    }

}
