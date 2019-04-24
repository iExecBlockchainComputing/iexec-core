package com.iexec.core.detector.replicate;

import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.detector.Detector;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskExecutorEngine;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.core.utils.DateTimeUtils.addMinutesToDate;

@Slf4j
@Service
public class ReplicateResultUploadTimeoutDetector implements Detector {

    private TaskService taskService;
    private ReplicatesService replicatesService;
    private TaskExecutorEngine taskExecutorEngine;

    public ReplicateResultUploadTimeoutDetector(TaskService taskService,
                                                ReplicatesService replicatesService,
                                                TaskExecutorEngine taskExecutorEngine) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
        this.taskExecutorEngine = taskExecutorEngine;
    }

    @Scheduled(fixedRateString = "${detector.resultuploadtimeout.period}")
    @Override
    public void detect() {
        // check all tasks with status upload result requested
        // Timeout for the replicate uploading its result is 2 min.

        List<TaskStatus> taskUploadStatuses = Arrays.asList(
                TaskStatus.RESULT_UPLOAD_REQUESTED,
                TaskStatus.RESULT_UPLOADING);

        for (Task task : taskService.findByCurrentStatus(taskUploadStatuses)) {
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
                taskExecutorEngine.updateTask(task.getChainTaskId());
                return;
            }

            log.info("detected replicate with resultUploadTimeout [chainTaskId:{}, replicate:{}, currentStatus:{}]",
                    chainTaskId, uploadingReplicate.getWalletAddress(), uploadingReplicate.getCurrentStatus());

            if (task.getCurrentStatus() == TaskStatus.RESULT_UPLOAD_REQUESTED) {
                replicatesService.updateReplicateStatus(chainTaskId, uploadingReplicate.getWalletAddress(),
                        RESULT_UPLOAD_REQUEST_FAILED, ReplicateStatusModifier.POOL_MANAGER);

                taskExecutorEngine.updateTask(task.getChainTaskId());
                return;
            }

            if (task.getCurrentStatus() == TaskStatus.RESULT_UPLOADING) {
                replicatesService.updateReplicateStatus(chainTaskId, uploadingReplicate.getWalletAddress(),
                        RESULT_UPLOAD_FAILED, ReplicateStatusModifier.POOL_MANAGER);

                taskExecutorEngine.updateTask(task.getChainTaskId());
                return;
            }
        }
    }
}
