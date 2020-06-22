package com.iexec.core.detector.task;

import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ChainTaskStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.detector.Detector;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskExecutorEngine;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class InitializedTaskDetector implements Detector {

    private TaskService taskService;
    private TaskExecutorEngine taskExecutorEngine;
    private IexecHubService iexecHubService;

    public InitializedTaskDetector(TaskService taskService,
                                   TaskExecutorEngine taskExecutorEngine,
                                   IexecHubService iexecHubService) {
        this.taskService = taskService;
        this.taskExecutorEngine = taskExecutorEngine;
        this.iexecHubService = iexecHubService;
    }

    /**
     * Detector to detect tasks that are initializing but are not initialized yet.
     */
    @Scheduled(fixedRateString = "${cron.detector.task.initialized.unnotified.period}")
    @Override
    public void detect() {
        log.debug("Trying to detect initialized tasks");
        for (Task task : taskService.findByCurrentStatus(TaskStatus.INITIALIZING)) {
            Optional<ChainTask> chainTask = iexecHubService.getChainTask(task.getChainTaskId());
            if (chainTask.isPresent() && !chainTask.get().getStatus().equals(ChainTaskStatus.UNSET)) {
                log.info("Detected confirmed missing update (task) [is:{}, should:{}, taskId:{}]",
                        TaskStatus.INITIALIZING, TaskStatus.INITIALIZED, task.getChainTaskId());
                taskExecutorEngine.updateTask(task.getChainTaskId());
            }
        }
    }
}
