package com.iexec.core.detector;

import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static com.iexec.common.replicate.ReplicateStatus.*;

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
                    if (!replicate.getCurrentStatus().equals(WORKER_LOST)) {
                        replicatesService.updateReplicateStatus(chainTaskId, workerWallet, WORKER_LOST);
                    }
                });
            }
        }
    }
}

