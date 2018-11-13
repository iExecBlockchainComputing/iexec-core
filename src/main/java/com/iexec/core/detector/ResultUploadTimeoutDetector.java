package com.iexec.core.detector;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;

import static com.iexec.core.utils.DateTimeUtils.addMinutesToDate;

@Slf4j
@Service
public class ResultUploadTimeoutDetector implements Detector {

    private TaskService taskService;

    public ResultUploadTimeoutDetector(TaskService taskService) {
        this.taskService = taskService;
    }

    @Scheduled(fixedRateString = "${detector.resultuploadtimeout.period}")
    @Override
    public void detect() {
        // check all tasks with status upload result requested
        // Timeout for the replicate uploading its result is 1 min.
        for (Task task : taskService.findByCurrentStatus(TaskStatus.UPLOAD_RESULT_REQUESTED)) {
            for (Replicate replicate : task.getReplicates()) {
                boolean isUploadingResultReplicate = replicate.getWalletAddress().equals(task.getUploadingWorkerWalletAddress());
                boolean startUploadLongAgo = new Date().after(addMinutesToDate(task.getLatestStatusChange().getDate(), 1));

                if (isUploadingResultReplicate && startUploadLongAgo) {
                    taskService.updateReplicateStatus(task.getId(), replicate.getWalletAddress(), ReplicateStatus.UPLOAD_RESULT_REQUEST_FAILED);
                }
            }
        }
    }
}
