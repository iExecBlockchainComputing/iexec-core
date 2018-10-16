package com.iexec.core.detector;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WorkerLostDetector implements Detector {

    private TaskService taskService;
    private WorkerService workerService;

    public WorkerLostDetector(TaskService taskService,
                              WorkerService workerService) {
        this.taskService = taskService;
        this.workerService = workerService;
    }

    @Scheduled(fixedRateString = "${detector.workerlost.period}")
    @Override
    public void detect() {
        for (Worker worker : workerService.getLostWorkers()) {
            for (Task task : taskService.getTasks(worker.getTaskIds())) {
                task.getReplicate(worker.getName()).ifPresent(replicate -> {
                    if (!replicate.getCurrentStatus().equals(ReplicateStatus.WORKER_LOST)) {
                        workerService.removeTaskIdFromWorker(task.getId(), worker.getName());
                        taskService.updateReplicateStatus(task.getId(), worker.getName(), ReplicateStatus.WORKER_LOST);
                    }
                });
            }
        }
    }
}
