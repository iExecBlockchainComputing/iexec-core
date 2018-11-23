package com.iexec.core.detector;

import com.iexec.common.replicate.ReplicateStatus;
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
                .updateReplicateStatus(Mockito.any(), Mockito.any(), Mockito.any());

    }

    @Test
    public void shouldUpdateOneReplicateToWorkerLost(){
        Date twoMinutesAgo = addMinutesToDate(new Date(), -2);
        String chainTaskId = "chainTaskId";
        String workerName = "worker1";
        String walletAddress = "0x748e091bf16048cb5103E0E10F9D5a8b7fBDd860";

        Worker worker = Worker.builder()
                .name(workerName)
                .walletAddress(walletAddress)
                .lastAliveDate(twoMinutesAgo)
                .chainTaskIds(Collections.singletonList(chainTaskId))
                .build();


        Replicate replicate = new Replicate(walletAddress, chainTaskId);
        replicate.updateStatus(ReplicateStatus.RUNNING);

        when(workerService.getLostWorkers()).thenReturn(Collections.singletonList(worker));
        when(replicatesService.getReplicate(chainTaskId, walletAddress)).thenReturn(Optional.of(replicate));

        workerLostDetector.detect();
        // verify that the call on the update is correct
        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(chainTaskId, worker.getName(), ReplicateStatus.WORKER_LOST);

        // verify that the worker should remove the taskId from its current tasks
        Mockito.verify(workerService, Mockito.times(1))
                .removeChainTaskIdFromWorker(chainTaskId, worker.getName());
    }

    // similar test with previous except that the Replicate is already is WORKER_LOST status.
    @Test
    public void shouldNotUpdateOneReplicateToWorkerLostSinceAlreadyUpdated(){
        Date twoMinutesAgo = addMinutesToDate(new Date(), -2);
        String chainTaskId = "chainTaskId";
        String workerName = "worker1";
        String walletAddress = "0x748e091bf16048cb5103E0E10F9D5a8b7fBDd860";

        Worker worker = Worker.builder()
                .name(workerName)
                .walletAddress(walletAddress)
                .lastAliveDate(twoMinutesAgo)
                .chainTaskIds(Collections.singletonList(chainTaskId))
                .build();

        Replicate replicate = new Replicate(walletAddress, chainTaskId);
        replicate.updateStatus(ReplicateStatus.RUNNING);
        replicate.updateStatus(ReplicateStatus.WORKER_LOST);

        when(workerService.getLostWorkers()).thenReturn(Collections.singletonList(worker));
        when(replicatesService.getReplicate(chainTaskId, walletAddress)).thenReturn(Optional.of(replicate));

        workerLostDetector.detect();
        // verify that the call on the update is correct
        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(chainTaskId, worker.getName(), ReplicateStatus.WORKER_LOST);

        // verify that the worker should remove the taskId from its current tasks
        Mockito.verify(workerService, Mockito.times(0))
                .removeChainTaskIdFromWorker(chainTaskId, worker.getName());
    }
}
