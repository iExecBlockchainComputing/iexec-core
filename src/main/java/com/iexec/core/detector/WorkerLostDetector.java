package com.iexec.core.detector;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Optional;

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
            for (String taskId : worker.getTaskIds()) {
                Optional<Task> task = taskService.getTask(taskId);
                if (task.isPresent()) {
                    Optional<Replicate> replicate = task.get().getReplicate(worker.getName());
                    replicate.ifPresent(optionalReplicate -> {
                        if (!optionalReplicate.getCurrentStatus().equals(ReplicateStatus.WORKER_LOST)) {
                            workerService.removeTaskIdFromWorker(taskId, worker.getName());
                            taskService.updateReplicateStatus(taskId, worker.getName(), ReplicateStatus.WORKER_LOST);
                        }
                    });
                }
            }
        }
    }
}
