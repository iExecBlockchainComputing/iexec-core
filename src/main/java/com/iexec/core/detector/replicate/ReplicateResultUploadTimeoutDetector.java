package com.iexec.core.detector.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.detector.Detector;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.result.ResultService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.iexec.core.utils.DateTimeUtils.addMinutesToDate;

@Slf4j
@Service
public class ReplicateResultUploadTimeoutDetector implements Detector {

    private TaskService taskService;
    private ReplicatesService replicatesService;
    private ResultService resultService;

    public ReplicateResultUploadTimeoutDetector(TaskService taskService,
                                                ReplicatesService replicatesService,
                                                ResultService resultService) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
        this.resultService = resultService;
    }

    @Scheduled(fixedRateString = "${detector.resultuploadtimeout.period}")
    @Override
    public void detect() {
        // check all tasks with status upload result requested
        // Timeout for the replicate uploading its result is 2 min.

        List<TaskStatus> taskUploadStatuses = Arrays.asList(
                TaskStatus.RESULT_UPLOAD_REQUESTED,
                TaskStatus.RESULT_UPLOADING);

        List<ReplicateStatus> replicateUploadFailureStatuses = Arrays.asList(
                ReplicateStatus.RESULT_UPLOAD_FAILED,
                ReplicateStatus.RESULT_UPLOAD_REQUEST_FAILED);

        for (Task task : taskService.findByCurrentStatus(taskUploadStatuses)) {
            String chainTaskId = task.getChainTaskId();
            String uploadingWallet = task.getUploadingWorkerWalletAddress();

            Optional<Replicate> oUploadingReplicate = replicatesService.getReplicate(chainTaskId, uploadingWallet);
            if (!oUploadingReplicate.isPresent()) {
                return;
            }

            Replicate uploadingReplicate = oUploadingReplicate.get();

            boolean startUploadLongAgo = new Date().after(addMinutesToDate(task.getLatestStatusChange().getDate(), 2));
            boolean hasReplicateUploadAlreadyFailed = replicateUploadFailureStatuses.contains(uploadingReplicate.getCurrentStatus());

            if (!startUploadLongAgo || hasReplicateUploadAlreadyFailed) {
                return;
            }

            log.info("detected replicate with resultUploadTimeout [chainTaskId:{}, replicate:{}, currentStatus:{}]",
                    chainTaskId, uploadingReplicate.getWalletAddress(), uploadingReplicate.getCurrentStatus());

            if (uploadingReplicate.containsStatus(ReplicateStatus.RESULT_UPLOADING) &&
                    !uploadingReplicate.containsStatus(ReplicateStatus.RESULT_UPLOADED)) {
                // delete the result from the database as the result may be truncated
                resultService.removeResult(chainTaskId);
                replicatesService.updateReplicateStatus(chainTaskId, uploadingReplicate.getWalletAddress(),
                        ReplicateStatus.RESULT_UPLOAD_FAILED, ReplicateStatusModifier.POOL_MANAGER);
                return;
            }

            if (uploadingReplicate.containsStatus(ReplicateStatus.RESULT_UPLOAD_REQUESTED) &&
                    !uploadingReplicate.containsStatus(ReplicateStatus.RESULT_UPLOADING)) {
                replicatesService.updateReplicateStatus(chainTaskId, uploadingReplicate.getWalletAddress(),
                        ReplicateStatus.RESULT_UPLOAD_REQUEST_FAILED, ReplicateStatusModifier.POOL_MANAGER);
                return;
            }
        }
    }
}
