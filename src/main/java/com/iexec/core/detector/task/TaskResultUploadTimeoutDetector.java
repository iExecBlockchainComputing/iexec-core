package com.iexec.core.detector.task;

import com.iexec.core.detector.Detector;
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


@Slf4j
@Service
public class TaskResultUploadTimeoutDetector implements Detector {

    private TaskService taskService;
    private TaskExecutorEngine taskExecutorEngine;

    public TaskResultUploadTimeoutDetector(TaskService taskService,
                                        TaskExecutorEngine taskExecutorEngine) {
        this.taskService = taskService;
        this.taskExecutorEngine = taskExecutorEngine;
    }

    @Scheduled(fixedRateString = "${cron.detector.resultuploadtimeout.period}")
    @Override
    public void detect() {
        log.debug("Trying to detect tasks with upload timeout");

        List<Task> inUploadStatus = taskService.findByCurrentStatus(Arrays.asList(
                TaskStatus.RESULT_UPLOAD_REQUESTED,
                TaskStatus.RESULT_UPLOADING));

        for (Task task : inUploadStatus) {
            String chainTaskId = task.getChainTaskId();

            boolean isNowAfterFinalDeadline = task.getFinalDeadline() != null && new Date().after(task.getFinalDeadline());

            if (isNowAfterFinalDeadline) {
                log.info("found task in status {} after final deadline [chainTaskId:{}]",
                        task.getCurrentStatus(), chainTaskId);
                taskExecutorEngine.updateTask(task.getChainTaskId());
            }
        }
    }
}
