package com.iexec.core.detector;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

import static com.iexec.core.utils.DateTimeUtils.addMinutesToDate;

@Slf4j
@Service
public class ResultUploadTimeoutDetector implements Detector {

    private TaskService taskService;
    private ReplicatesService replicatesService;

    public ResultUploadTimeoutDetector(TaskService taskService,
                                       ReplicatesService replicatesService) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
    }

    @Scheduled(fixedRateString = "${detector.resultuploadtimeout.period}")
    @Override
    public void detect() {
        // check all tasks with status upload result requested
        // Timeout for the replicate uploading its result is 1 min.
        for (Task task : taskService.findByCurrentStatus(TaskStatus.RESULT_UPLOAD_REQUESTED)) {
            String chainTaskId = task.getChainTaskId();

            Optional<Replicate> optional = replicatesService.getReplicate(chainTaskId, task.getUploadingWorkerWalletAddress());
            if(optional.isPresent()){
                Replicate replicate = optional.get();
                boolean startUploadLongAgo = new Date().after(addMinutesToDate(task.getLatestStatusChange().getDate(), 1));

                if (startUploadLongAgo) {
                    replicatesService.updateReplicateStatus(chainTaskId, replicate.getWalletAddress(),
                            ReplicateStatus.UPLOAD_RESULT_REQUEST_FAILED);
                }
            }
        }
    }
}
