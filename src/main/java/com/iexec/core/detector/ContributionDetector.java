package com.iexec.core.detector;

import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.chain.IexecHubService;
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
import java.util.List;

import static com.iexec.common.replicate.ReplicateStatus.*;

@Slf4j
@Service
public class ContributionDetector implements Detector {

    private TaskService taskService;
    private ReplicatesService replicatesService;
    private WorkerService workerService;
    private IexecHubService iexecHubService;

    public ContributionDetector(TaskService taskService,
                                ReplicatesService replicatesService,
                                WorkerService workerService,
                                IexecHubService iexecHubService) {
        this.taskService = taskService;
        this.replicatesService = replicatesService;
        this.workerService = workerService;
        this.iexecHubService = iexecHubService;
    }

    @Scheduled(fixedRateString = "${detector.contribute.period}")
    @Override
    public void detect() {
        log.info("Trying to detect contribution timeout");
        detectContributionTimeout();
        log.info("Trying to detect un-notified contributed");
        detectUnNotifiedContributed();
    }

    void detectContributionTimeout() {
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
                if (doesTaskNeedUpdate){
                    taskService.tryToMoveTaskToNextStatus(task);
                }
            }
        }
    }

    void detectUnNotifiedContributed() {
        for (Task task : taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))) {
            boolean doesTaskNeedUpdate = false;
            for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                //check if a worker has contributed on-chain but hasn't notified off-chain
                if (replicate.isContributingPeriodTooLong(task.getTimeRef()) &&
                        iexecHubService.checkContributionStatus(task.getChainTaskId(),
                                replicate.getWalletAddress(), ChainContributionStatus.CONTRIBUTED)){
                    updateReplicateStatuses(task.getChainTaskId(), replicate);
                    doesTaskNeedUpdate = true;
                }
            }
            if (doesTaskNeedUpdate){
                taskService.tryToMoveTaskToNextStatus(task);
            }
        }
    }

    private void updateReplicateStatuses(String chainTaskId, Replicate replicate) {
        List<ReplicateStatus> statusesToUpdate = getMissingStatuses(replicate.getCurrentStatus(), CONTRIBUTED);
        for (ReplicateStatus statusToUpdate: statusesToUpdate){
            replicatesService.updateReplicateStatus(chainTaskId, replicate.getWalletAddress(),
                    statusToUpdate, ReplicateStatusModifier.POOL_MANAGER);
        }
    }


}
