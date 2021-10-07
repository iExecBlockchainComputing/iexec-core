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

package com.iexec.core.detector.replicate;

import com.iexec.core.detector.Detector;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.TaskUpdateManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.utils.DateTimeUtils.addMinutesToDate;

@Slf4j
@Service
public class ReplicateResultUploadTimeoutDetector implements Detector {

    private final TaskService taskService;
    private final TaskUpdateManager taskUpdateManager;
    private final ReplicatesService replicatesService;

    public ReplicateResultUploadTimeoutDetector(
            TaskService taskService,
            TaskUpdateManager taskUpdateManager,
            ReplicatesService replicatesService
    ) {
        this.taskService = taskService;
        this.taskUpdateManager = taskUpdateManager;
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

            Optional<Replicate> oUploadingReplicate = replicatesService.getReplicate(chainTaskId, uploadingWallet);
            if (!oUploadingReplicate.isPresent()) {
                return;
            }

            Replicate uploadingReplicate = oUploadingReplicate.get();

            boolean startedUploadLongAgo = new Date().after(addMinutesToDate(task.getLatestStatusChange().getDate(), 2));
            boolean hasReplicateAlreadyFailedToUpload = uploadingReplicate.containsStatus(RESULT_UPLOAD_REQUEST_FAILED) ||
                                                        uploadingReplicate.containsStatus(RESULT_UPLOAD_FAILED);

            if (!startedUploadLongAgo) {
                return;
            }

            if (hasReplicateAlreadyFailedToUpload) {
                taskUpdateManager.publishUpdateTaskRequest(task.getChainTaskId());
                return;
            }

            log.info("detected replicate with resultUploadTimeout [chainTaskId:{}, replicate:{}, currentStatus:{}]",
                    chainTaskId, uploadingReplicate.getWalletAddress(), uploadingReplicate.getCurrentStatus());

            replicatesService.updateReplicateStatus(chainTaskId, uploadingReplicate.getWalletAddress(),
                    RESULT_UPLOAD_FAILED);
            taskUpdateManager.publishUpdateTaskRequest(task.getChainTaskId());
        }
    }
}
