package com.iexec.core.detector;

import com.iexec.core.chain.IexecHubService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Date;

import static com.iexec.common.replicate.ReplicateStatus.CONTRIBUTED;
import static com.iexec.common.replicate.ReplicateStatus.REVEALING;
import static com.iexec.common.replicate.ReplicateStatus.REVEAL_TIMEOUT;

public class RevealTimeoutDetector implements Detector {

    private TaskService taskService;
    private IexecHubService iexecHubService;
    private ReplicatesService replicatesService;

    public RevealTimeoutDetector(TaskService taskService,
                                 IexecHubService iexecHubService,
                                 ReplicatesService replicatesService) {
        this.taskService = taskService;
        this.iexecHubService = iexecHubService;
        this.replicatesService = replicatesService;
    }

    @Scheduled(fixedRateString = "${detector.revealtimeout.period}")
    @Override
    public void detect() {
        for (Task task : taskService.findByCurrentStatus(TaskStatus.CONSENSUS_REACHED)) {
            Date now = new Date();
            if (now.after(task.getRevealDeadline())) {

                // update all replicates status attached to this task
                for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                    if(replicate.getCurrentStatus().equals(REVEALING) ||
                            replicate.getCurrentStatus().equals(CONTRIBUTED)) {
                        replicatesService.updateReplicateStatus(task.getChainTaskId(), replicate.getWalletAddress(), REVEAL_TIMEOUT);
                    }
                }

                // reopen the task
                iexecHubService.reOpen(task.getChainTaskId());
            }
        }
    }
}
