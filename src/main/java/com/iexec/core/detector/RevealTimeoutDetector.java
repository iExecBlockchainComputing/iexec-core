package com.iexec.core.detector;

import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskExecutorEngine;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.core.task.TaskStatus.*;
import static com.iexec.core.task.TaskStatus.RESULT_UPLOADED;
import static com.iexec.core.task.TaskStatus.RESULT_UPLOADING;

@Slf4j
@Service
public class RevealTimeoutDetector implements Detector {

    private TaskService taskService;
    private ReplicatesService replicatesService;
    private TaskExecutorEngine taskExecutorEngine;

    public RevealTimeoutDetector(TaskService taskService,
                                 ReplicatesService replicatesService,
                                 TaskExecutorEngine taskExecutorEngine) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
        this.taskExecutorEngine = taskExecutorEngine;
    }

    @Scheduled(fixedRateString = "${detector.reveal.timeout.period}")
    @Override
    public void detect() {
        log.info("Trying to detect reveal timeout");

        // in case no worker reveals anything then the task is open again
        detectReOpenCase();

        // in case there is at least one reveal, the others may time out and the task continues
        detectSingleRevealTimeout();
    }

    private void detectSingleRevealTimeout() {
        List<Task> tasks = new ArrayList<>(taskService.findByCurrentStatus(Arrays.asList(AT_LEAST_ONE_REVEALED,
                RESULT_UPLOAD_REQUESTED, RESULT_UPLOADING, RESULT_UPLOADED)));

        for (Task task : tasks) {
            Date now = new Date();
            if (now.after(task.getRevealDeadline())) {
                // TODO: Un-revealing workers should be notified to PLEASE_ABORT_ON_REVEAL_TIMEOUT elsewhere
                for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                    if (replicate.getCurrentStatus().equals(REVEALING) ||
                            replicate.getCurrentStatus().equals(CONTRIBUTED)) {
                        replicatesService.updateReplicateStatus(task.getChainTaskId(), replicate.getWalletAddress(),
                                REVEAL_TIMEOUT, ReplicateStatusModifier.POOL_MANAGER);
                    }
                }
                // end TODO
                log.info("Task with a reveal timeout found [chainTaskId:{}]", task.getChainTaskId());
                taskExecutorEngine.updateTask(task.getChainTaskId());
            }
        }
    }

    private void detectReOpenCase() {
        for (Task task : taskService.findByCurrentStatus(TaskStatus.CONSENSUS_REACHED)) {
            log.info("Task with consensus reached: " + task.getChainTaskId());
            Date now = new Date();
            if (now.after(task.getRevealDeadline())) {

                // update all replicates status attached to this task
                for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                    if (replicate.getCurrentStatus().equals(REVEALING) ||
                            replicate.getCurrentStatus().equals(CONTRIBUTED)) {
                        replicatesService.updateReplicateStatus(task.getChainTaskId(), replicate.getWalletAddress(),
                                REVEAL_TIMEOUT, ReplicateStatusModifier.POOL_MANAGER);
                    }
                }
                //TODO: We shouldn't call directly reOpenTask here but use taskExecutorEngine.updateTask(task);
                taskService.reOpenTask(task);
            }
        }
    }
}