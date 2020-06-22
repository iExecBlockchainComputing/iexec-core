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

@Slf4j
@Service
public class ContributionTimeoutTaskDetector implements Detector {

    private TaskService taskService;
    private TaskExecutorEngine taskExecutorEngine;

    public ContributionTimeoutTaskDetector(TaskService taskService,
                                           TaskExecutorEngine taskExecutorEngine) {
        this.taskService = taskService;
        this.taskExecutorEngine = taskExecutorEngine;
    }

    @Scheduled(fixedRateString = "${cron.detector.contribution.timeout.period}")
    @Override
    public void detect() {
        log.debug("Trying to detect contribution timeout");
        for (Task task : taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))) {
            Date now = new Date();
            if (now.after(task.getContributionDeadline())) {
                log.info("Task with contribution timeout found [chainTaskId:{}]", task.getChainTaskId());
                taskExecutorEngine.updateTask(task.getChainTaskId());
            }
        }
    }
}
