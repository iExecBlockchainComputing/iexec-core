package com.iexec.core.detector.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.detector.Detector;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.Optional;

import static com.iexec.core.utils.DateTimeUtils.addMinutesToDate;

@Slf4j
@Service
public class ReplicateResultUploadTimeoutDetector implements Detector {

    private TaskService taskService;
    private ReplicatesService replicatesService;

    public ReplicateResultUploadTimeoutDetector(TaskService taskService,
                                       ReplicatesService replicatesService) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
    }

    @Scheduled(fixedRateString = "${detector.resultuploadtimeout.period}")
    @Override
    public void detect() {
        // check all tasks with status upload result requested
        // Timeout for the replicate uploading its result is 1 min.
        for (Task task : taskService.findByCurrentStatus(Arrays.asList(TaskStatus.RESULT_UPLOAD_REQUESTED, TaskStatus.RESULT_UPLOADING))) {
            String chainTaskId = task.getChainTaskId();

            Optional<Replicate> optional = replicatesService.getReplicate(chainTaskId, task.getUploadingWorkerWalletAddress());
            if (!optional.isPresent()) {
                return;
            }

            Replicate replicate = optional.get();
            boolean startUploadLongAgo = new Date().after(addMinutesToDate(task.getLatestStatusChange().getDate(), 2));
            boolean hasReplicateUploadAlreadyFailed = replicate.getCurrentStatus().equals(ReplicateStatus.RESULT_UPLOAD_REQUEST_FAILED)
                    || replicate.getCurrentStatus().equals(ReplicateStatus.RESULT_UPLOAD_FAILED);

            if (!startUploadLongAgo || hasReplicateUploadAlreadyFailed) {
                return;
            }

            log.info("detected replicate with resultUploadTimeout [replicate:{},currentStatus:{}]",
            replicate.getWalletAddress(), replicate.getCurrentStatus());

            if (task.getCurrentStatus() == TaskStatus.RESULT_UPLOAD_REQUESTED) {
                replicatesService.updateReplicateStatus(chainTaskId, replicate.getWalletAddress(),
                        ReplicateStatus.RESULT_UPLOAD_REQUEST_FAILED, ReplicateStatusModifier.POOL_MANAGER);
                return;
            }

            if (task.getCurrentStatus() == TaskStatus.RESULT_UPLOADING) {
                replicatesService.updateReplicateStatus(chainTaskId, replicate.getWalletAddress(),
                        ReplicateStatus.RESULT_UPLOAD_FAILED, ReplicateStatusModifier.POOL_MANAGER);
                return;
            }
        }
    }
}
