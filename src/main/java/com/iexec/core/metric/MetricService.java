package com.iexec.core.metric;

import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;

import static com.iexec.core.task.TaskStatus.*;

@Slf4j
@Service
public class MetricService {


    private WorkerService workerService;
    private TaskService taskService;

    public MetricService(WorkerService workerService,
                         TaskService taskService) {
        this.workerService = workerService;
        this.taskService = taskService;
    }

    public PlatformMetric getPlatformMetrics() {
        return PlatformMetric.builder()
                .aliveWorkers(workerService.getAliveWorkers().size())
                .aliveTotalCpu(workerService.getAliveTotalCpu())
                .aliveAvailableCpu(workerService.getAliveAvailableCpu())
                .completedTasks(taskService.findByCurrentStatus(TaskStatus.COMPLETED).size())
                .build();
    }

}
