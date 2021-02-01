/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.core.detector;

import com.google.common.collect.ImmutableSet;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.TaskService;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static com.iexec.common.replicate.ReplicateStatus.WORKER_LOST;

@Service
@Slf4j
public class WorkerLostDetector implements Detector {

    public static final ImmutableSet<ReplicateStatus> SHOULD_NOT_UPDATE_STATUSES =
            new ImmutableSet.Builder<ReplicateStatus>()
                    .add(WORKER_LOST)
                    .addAll(ReplicateStatus.getFinalStatuses())
                    .build();

    private final ReplicatesService replicatesService;
    private final WorkerService workerService;
    private final TaskService taskService;

    public WorkerLostDetector(
            ReplicatesService replicatesService,
            WorkerService workerService,
            TaskService taskService
    ) {
        this.replicatesService = replicatesService;
        this.workerService = workerService;
        this.taskService = taskService;
    }

    @Scheduled(fixedRateString = "#{@cronConfiguration.getWorkerLost()}")
    @Override
    public void detect() {
        log.debug("Detecting lost workers");
        for (Worker worker : workerService.getLostWorkers()) {
            String workerWallet = worker.getWalletAddress();
            for (String chainTaskId : worker.getParticipatingChainTaskIds()) {
                if (taskService.isExpired(chainTaskId)) {
                    continue;
                }
                replicatesService
                        .getReplicate(chainTaskId, workerWallet)
                        .ifPresent(replicate -> {
                            if (!SHOULD_NOT_UPDATE_STATUSES.contains(replicate.getCurrentStatus())) {
                                replicatesService.updateReplicateStatus(
                                        chainTaskId,
                                        workerWallet,
                                        WORKER_LOST
                                );
                            }
                        });
            }
        }
    }
}

