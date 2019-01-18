package com.iexec.core.detector;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WorkerLostDetector implements Detector {

    private ReplicatesService replicatesService;
    private WorkerService workerService;

    public WorkerLostDetector(ReplicatesService replicatesService,
                              WorkerService workerService) {
        this.replicatesService = replicatesService;
        this.workerService = workerService;
    }

    @Scheduled(fixedRateString = "${detector.workerlost.period}")
    @Override
    public void detect() {
        for (Worker worker : workerService.getLostWorkers()) {
            String workerWallet = worker.getWalletAddress();

            for (String chainTaskId : worker.getParticipatingChainTaskIds()) {
                replicatesService.getReplicate(chainTaskId, workerWallet).ifPresent(replicate -> {
                    if (!replicate.getCurrentStatus().equals(ReplicateStatus.WORKER_LOST)) {
                        workerService.removeChainTaskIdFromWorker(chainTaskId, workerWallet);
                        replicatesService.updateReplicateStatus(chainTaskId, workerWallet,
                                ReplicateStatus.WORKER_LOST, ReplicateStatusModifier.POOL_MANAGER);
                    }
                });
            }
        }
    }
}

