package com.iexec.core.detector;

import com.iexec.core.task.Task;
import com.iexec.core.task.TaskExecutorEngine;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class UnstartedTxDetector implements Detector {

    private TaskService taskService;
    private TaskExecutorEngine taskExecutorEngine;

    public UnstartedTxDetector(TaskService taskService,
                               TaskExecutorEngine taskExecutorEngine) {
        this.taskService = taskService;
        this.taskExecutorEngine = taskExecutorEngine;
    }

    @Scheduled(fixedRateString = "${detector.unstartedtx.period}")
    @Override
    public void detect() {
        //start finalize when needed
        List<Task> notYetFinalizingTasks = taskService.findByCurrentStatus(TaskStatus.RESULT_UPLOADED);
        for (Task task : notYetFinalizingTasks) {
            log.info("UnstartedTxDetector should update RESULT_UPLOADED task to FINALIZING [chainTaskId:{}]",
                    task.getChainTaskId());
            taskExecutorEngine.updateTask(task.getChainTaskId());
        }

        //start initialize when needed
        List<Task> notYetInitializingTasks = taskService.findByCurrentStatus(TaskStatus.RECEIVED);
        for (Task task : notYetInitializingTasks) {
            log.info("UnstartedTxDetector should update RECEIVED task to INITIALIZING [chainDealId:{}, taskIndex:{}]",
                    task.getChainDealId(), task.getTaskIndex());
            taskExecutorEngine.updateTask(task.getChainTaskId());
        }
    }
}

