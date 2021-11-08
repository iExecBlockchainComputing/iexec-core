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

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.core.detector.replicate.ContributionUnnotifiedDetector;
import com.iexec.core.replicate.ReplicateUpdatedEvent;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.TaskUpdateManager;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import static com.iexec.common.replicate.ReplicateStatusCause.TASK_NOT_ACTIVE;

@Slf4j
@Component
public class ReplicateListeners {

    private final TaskUpdateManager taskUpdateManager;
    private final WorkerService workerService;
    private final ContributionUnnotifiedDetector contributionUnnotifiedDetector;
    private final ReplicatesService replicatesService;

    public ReplicateListeners(WorkerService workerService,
                              TaskUpdateManager taskUpdateManager,
                              ContributionUnnotifiedDetector contributionUnnotifiedDetector,
                              ReplicatesService replicatesService) {
        this.workerService = workerService;
        this.taskUpdateManager = taskUpdateManager;
        this.contributionUnnotifiedDetector = contributionUnnotifiedDetector;
        this.replicatesService = replicatesService;
    }

    @EventListener
    public void onReplicateUpdatedEvent(ReplicateUpdatedEvent event) {
        log.debug("Received ReplicateUpdatedEvent [chainTaskId:{}] ", event.getChainTaskId());
        ReplicateStatusUpdate statusUpdate = event.getReplicateStatusUpdate();
        ReplicateStatus newStatus = statusUpdate.getStatus();
        ReplicateStatusCause cause = statusUpdate.getDetails() != null ? statusUpdate.getDetails().getCause() : null;

        taskUpdateManager.publishUpdateTaskRequest(event.getChainTaskId());

        /*
         * Should release 1 CPU of given worker for this replicate if status is
         * "COMPUTED" or "*_FAILED" before COMPUTED
         * */
        if (newStatus.equals(ReplicateStatus.START_FAILED)
                || newStatus.equals(ReplicateStatus.APP_DOWNLOAD_FAILED)
                || newStatus.equals(ReplicateStatus.DATA_DOWNLOAD_FAILED)
                || newStatus.equals(ReplicateStatus.COMPUTED)
                || newStatus.equals(ReplicateStatus.COMPUTE_FAILED)) {
            workerService.removeComputedChainTaskIdFromWorker(event.getChainTaskId(), event.getWalletAddress());
        }

        /*
         * A CONTRIBUTE_FAILED status with the cause TASK_NOT_ACTIVE means this new worker have been
         * authorized to contribute (but couldn't) while we had a consensus_reached onchain but not
         * in database (meaning another worker didn't notify he had contributed).
         * We should start a detector which will look for unnotified contributions and will upgrade
         * task to consensus_reached
         */
        if (cause != null && cause.equals(TASK_NOT_ACTIVE)) {
            contributionUnnotifiedDetector.detectOnchainContributed();
        }

        /*
         * Should add FAILED status if not completable
         * */
        if (ReplicateStatus.getUncompletableStatuses().contains(newStatus)) {
            replicatesService.updateReplicateStatus(event.getChainTaskId(),
                    event.getWalletAddress(), ReplicateStatus.FAILED);
        }

        /*
         * Should release given worker for this replicate if status is COMPLETED or FAILED
         * */
        if (ReplicateStatus.getFinalStatuses().contains(newStatus)) {
            workerService.removeChainTaskIdFromWorker(event.getChainTaskId(), event.getWalletAddress());
        }
    }

}
