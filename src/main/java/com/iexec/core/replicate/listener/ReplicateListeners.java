/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.replicate.listener;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.core.detector.replicate.ContributionUnnotifiedDetector;
import com.iexec.core.replicate.ReplicateUpdatedEvent;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusCause.TASK_NOT_ACTIVE;

@Slf4j
@Component
public class ReplicateListeners {

    private final TaskUpdateRequestManager taskUpdateRequestManager;
    private final WorkerService workerService;
    private final ContributionUnnotifiedDetector contributionUnnotifiedDetector;
    private final ReplicatesService replicatesService;

    public ReplicateListeners(final WorkerService workerService,
                              final TaskUpdateRequestManager taskUpdateRequestManager,
                              final ContributionUnnotifiedDetector contributionUnnotifiedDetector,
                              final ReplicatesService replicatesService) {
        this.workerService = workerService;
        this.taskUpdateRequestManager = taskUpdateRequestManager;
        this.contributionUnnotifiedDetector = contributionUnnotifiedDetector;
        this.replicatesService = replicatesService;
    }

    @EventListener
    public void onReplicateUpdatedEvent(final ReplicateUpdatedEvent event) {
        log.debug("Received ReplicateUpdatedEvent [chainTaskId:{}, workerAddress:{}, status:{}]",
                event.getChainTaskId(), event.getWalletAddress(), event.getReplicateStatusUpdate().getStatus());
        final ReplicateStatusUpdate statusUpdate = event.getReplicateStatusUpdate();
        final ReplicateStatus newStatus = statusUpdate.getStatus();
        final ReplicateStatusCause cause = statusUpdate.getDetails() != null ? statusUpdate.getDetails().getCause() : null;

        // Those are the only transitions justifying to update a task status
        // When one worker updates its replicate status to STARTED, its task status can be updated to RUNNING
        // When one worker updates its replicate status to RESULT_UPLOADED, its task status can be updated to RESULT_UPLOADED
        // The other statuses denote major PoCo events allowing to see an update of the associated task status
        // The check against isFailedBeforeComputed allows to trigger the running2runningFailed check in TaskUpdateManager
        // This protection allows to avoid unnecessary loops in update manager as well as unnecessary on-chain calls
        if (List.of(STARTED, CONTRIBUTE_AND_FINALIZE_DONE, CONTRIBUTED, REVEALED, RESULT_UPLOADED).contains(newStatus)
                || newStatus.isFailedBeforeComputed()) {
            taskUpdateRequestManager.publishRequest(event.getChainTaskId());
        }

        /*
         * Should release 1 CPU of given worker for this replicate if status is
         * "COMPUTED" or "*_FAILED" before COMPUTED
         */
        if (newStatus.isFailedBeforeComputed() || newStatus == ReplicateStatus.COMPUTED) {
            log.info("End of replicate computation detected [chainTaskId:{}, workerAddress:{}]",
                    event.getChainTaskId(), event.getWalletAddress());
            workerService.removeComputedChainTaskIdFromWorker(event.getChainTaskId(), event.getWalletAddress());
        }

        /*
         * A CONTRIBUTE_FAILED status with the cause TASK_NOT_ACTIVE means this new worker have been
         * authorized to contribute (but couldn't) while we had a consensus_reached onchain but not
         * in database (meaning another worker didn't notify he had contributed).
         * We should start a detector which will look for unnotified contributions and will upgrade
         * task to consensus_reached
         */
        if (cause == TASK_NOT_ACTIVE) {
            contributionUnnotifiedDetector.detectOnchainDone();
        }

        /*
         * Should add FAILED status if not completable
         */
        if (ReplicateStatus.getUncompletableStatuses().contains(newStatus)) {
            replicatesService.updateReplicateStatus(event.getChainTaskId(),
                    event.getWalletAddress(), ReplicateStatusUpdate.poolManagerRequest(FAILED));
        }

        /*
         * Should release given worker for this replicate if status is COMPLETED or FAILED
         */
        if (ReplicateStatus.getFinalStatuses().contains(newStatus)) {
            workerService.removeChainTaskIdFromWorker(event.getChainTaskId(), event.getWalletAddress());
        }
    }

}
