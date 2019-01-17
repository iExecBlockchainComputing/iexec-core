package com.iexec.core.detector;

import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;

import static com.iexec.common.replicate.ReplicateStatus.CONTRIBUTION_TIMEOUT;

@Slf4j
@Service
public class ContributionTimeoutDetector implements Detector {

    private TaskService taskService;
    private ReplicatesService replicatesService;
    private WorkerService workerService;

    public ContributionTimeoutDetector(TaskService taskService,
                                       ReplicatesService replicatesService,
                                       WorkerService workerService) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
        this.workerService = workerService;
    }

    @Scheduled(fixedRateString = "${detector.contribution.timeout.period}")
    @Override
    public void detect() {
        log.info("Trying to detect contribution timeout");
        for (Task task : taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))) {
            boolean doesTaskNeedUpdate = false;
            Date now = new Date();
            if (now.after(task.getContributionDeadline())) {
                log.info("Task with contribution timeout found [chainTaskId:{}]", task.getChainTaskId());
                // update all replicates status attached to this task
                for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                    workerService.removeChainTaskIdFromWorker(task.getChainTaskId(), replicate.getWalletAddress());
                    replicatesService.updateReplicateStatus(task.getChainTaskId(), replicate.getWalletAddress(),
                            CONTRIBUTION_TIMEOUT, ReplicateStatusModifier.POOL_MANAGER);
                    doesTaskNeedUpdate = true;
                }
                if (doesTaskNeedUpdate) {
                    taskService.tryToMoveTaskToNextStatus(task);
                }
            }
        }
    }
}
