/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.detector.replicate;

import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.core.detector.Detector;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.iexec.common.replicate.ReplicateStatus.RESULT_UPLOAD_FAILED;

@Slf4j
@Service
public class ReplicateResultUploadTimeoutDetector implements Detector {

    private final TaskService taskService;
    private final TaskUpdateRequestManager taskUpdateRequestManager;
    private final ReplicatesService replicatesService;

    public ReplicateResultUploadTimeoutDetector(
            TaskService taskService,
            TaskUpdateRequestManager taskUpdateRequestManager,
            ReplicatesService replicatesService
    ) {
        this.taskService = taskService;
        this.taskUpdateRequestManager = taskUpdateRequestManager;
        this.replicatesService = replicatesService;
    }

    @Scheduled(fixedRateString = "#{@cronConfiguration.getResultUploadTimeout()}")
    @Override
    public void detect() {
        // check all tasks with status upload result requested
        // Timeout for the replicate uploading its result is 2 min.
        log.debug("Detecting result upload timeout");

        for (Task task : taskService.findByCurrentStatus(TaskStatus.RESULT_UPLOADING)) {
            String chainTaskId = task.getChainTaskId();
            String uploadingWallet = task.getUploadingWorkerWalletAddress();

            final Replicate uploadingReplicate = replicatesService.getReplicate(chainTaskId, uploadingWallet).orElse(null);
            if (uploadingReplicate == null) {
                return;
            }

            boolean startedUploadLongAgo = Instant.now().isAfter(
                    Instant.ofEpochMilli(task.getLatestStatusChange().getDate().getTime()).plus(2L, ChronoUnit.MINUTES));
            boolean hasReplicateAlreadyFailedToUpload = uploadingReplicate.containsStatus(RESULT_UPLOAD_FAILED);

            if (!startedUploadLongAgo) {
                return;
            }

            if (hasReplicateAlreadyFailedToUpload) {
                taskUpdateRequestManager.publishRequest(task.getChainTaskId());
                return;
            }

            log.info("detected replicate with resultUploadTimeout [chainTaskId:{}, replicate:{}, currentStatus:{}]",
                    chainTaskId, uploadingReplicate.getWalletAddress(), uploadingReplicate.getCurrentStatus());

            replicatesService.updateReplicateStatus(
                    chainTaskId, uploadingReplicate.getWalletAddress(), ReplicateStatusUpdate.poolManagerRequest(RESULT_UPLOAD_FAILED));
            taskUpdateRequestManager.publishRequest(task.getChainTaskId());
        }
    }
}
