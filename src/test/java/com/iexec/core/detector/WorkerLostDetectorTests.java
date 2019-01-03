package com.iexec.core.detector;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static com.iexec.core.utils.DateTimeUtils.addMinutesToDate;
import static org.mockito.Mockito.when;

public class WorkerLostDetectorTests {

    private final static String WALLET_WORKER = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private final static String CHAIN_TASK_ID = "chainTaskId";

    @Mock
    private WorkerService workerService;

    @Mock
    private ReplicatesService replicatesService;

    @InjectMocks
    private WorkerLostDetector workerLostDetector;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotDetectAnyWorkerLost(){
        when(workerService.getLostWorkers()).thenReturn(Collections.emptyList());
        workerLostDetector.detect();
        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

    }

    @Test
    public void shouldUpdateOneReplicateToWorkerLost(){
        Date twoMinutesAgo = addMinutesToDate(new Date(), -2);

        Worker worker = Worker.builder()
                .walletAddress(WALLET_WORKER)
                .lastAliveDate(twoMinutesAgo)
                .chainTaskIds(Collections.singletonList(CHAIN_TASK_ID))
                .build();


        Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);

        when(workerService.getLostWorkers()).thenReturn(Collections.singletonList(worker));
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER)).thenReturn(Optional.of(replicate));

        workerLostDetector.detect();
        // verify that the call on the update is correct
        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER, ReplicateStatus.WORKER_LOST, ReplicateStatusModifier.SCHEDULER);

        // verify that the worker should remove the taskId from its current tasks
        Mockito.verify(workerService, Mockito.times(1))
                .removeChainTaskIdFromWorker(CHAIN_TASK_ID, WALLET_WORKER);
    }

    // similar test with previous except that the Replicate is already is WORKER_LOST status.
    @Test
    public void shouldNotUpdateOneReplicateToWorkerLostSinceAlreadyUpdated(){
        Date twoMinutesAgo = addMinutesToDate(new Date(), -2);

        Worker worker = Worker.builder()
                .walletAddress(WALLET_WORKER)
                .lastAliveDate(twoMinutesAgo)
                .chainTaskIds(Collections.singletonList(CHAIN_TASK_ID))
                .build();

        Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.RUNNING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.WORKER_LOST, ReplicateStatusModifier.SCHEDULER);

        when(workerService.getLostWorkers()).thenReturn(Collections.singletonList(worker));
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER)).thenReturn(Optional.of(replicate));

        workerLostDetector.detect();
        // verify that the call on the update is correct
        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER, ReplicateStatus.WORKER_LOST, ReplicateStatusModifier.SCHEDULER);

        // verify that the worker should remove the taskId from its current tasks
        Mockito.verify(workerService, Mockito.times(0))
                .removeChainTaskIdFromWorker(CHAIN_TASK_ID, WALLET_WORKER);
    }
}
