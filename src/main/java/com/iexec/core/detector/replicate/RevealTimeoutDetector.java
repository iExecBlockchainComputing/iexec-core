package com.iexec.core.detector.replicate;

import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.detector.Detector;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
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
import static com.iexec.core.task.TaskStatus.RESULT_UPLOAD_REQUESTED;

@Slf4j
@Service
public class RevealTimeoutDetector implements Detector {

    private TaskService taskService;
    private ReplicatesService replicatesService;

    public RevealTimeoutDetector(TaskService taskService,
                                 ReplicatesService replicatesService) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
    }

    @Scheduled(fixedRateString = "${detector.reveal.timeout.period}")
    @Override
    public void detect() {
        log.info("Trying to detect reveal timeout");

        detectTaskAfterRevealDealLineWithZeroReveal();//finalizable

        detectTaskAfterRevealDealLineWithAtLeastOneReveal();//reopenable
    }

    private void detectTaskAfterRevealDealLineWithAtLeastOneReveal() {
        List<Task> tasks = new ArrayList<>(taskService.findByCurrentStatus(Arrays.asList(AT_LEAST_ONE_REVEALED,
                RESULT_UPLOAD_REQUESTED, RESULT_UPLOADING, RESULT_UPLOADED)));

        for (Task task : tasks) {
            Date now = new Date();
            if (now.after(task.getRevealDeadline())) {
                for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                    if (replicate.getCurrentStatus().equals(REVEALING) ||
                            replicate.getCurrentStatus().equals(CONTRIBUTED) ||
                            replicate.isLostAfterStatus(REVEALING) ||
                            replicate.isLostAfterStatus(CONTRIBUTED)) {
                        replicatesService.updateReplicateStatus(task.getChainTaskId(), replicate.getWalletAddress(),
                                REVEAL_TIMEOUT, ReplicateStatusModifier.POOL_MANAGER);
                    }
                }
                log.info("Found task after revealDeadline with at least one reveal, could be finalized [chainTaskId:{}]", task.getChainTaskId());
            }
        }
    }

    private void detectTaskAfterRevealDealLineWithZeroReveal() {
        for (Task task : taskService.findByCurrentStatus(TaskStatus.CONSENSUS_REACHED)) {
            Date now = new Date();
            if (now.after(task.getRevealDeadline())) {
                // update all replicates status attached to this task
                for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                    if (replicate.getCurrentStatus().equals(REVEALING) ||
                            replicate.getCurrentStatus().equals(CONTRIBUTED) ||
                            replicate.isLostAfterStatus(REVEALING) ||
                            replicate.isLostAfterStatus(CONTRIBUTED)) {
                        replicatesService.updateReplicateStatus(task.getChainTaskId(), replicate.getWalletAddress(),
                                REVEAL_TIMEOUT, ReplicateStatusModifier.POOL_MANAGER);
                    }
                }
                log.info("Found task after revealDeadline with zero reveal, could be reopened [chainTaskId:{}]", task.getChainTaskId());
            }
        }
    }
}