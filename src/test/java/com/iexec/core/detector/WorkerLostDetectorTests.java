package com.iexec.core.detector;

import com.iexec.common.replicate.ReplicateStatus;
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

import static com.iexec.core.utils.DateTimeUtils.addMinutesToDate;
import static org.mockito.Mockito.when;

public class WorkerLostDetectorTests {

    @Mock
    private WorkerService workerService;

    @Mock
    private TaskService taskService;

    @InjectMocks
    private WorkerLostDetector workerLostDetector;

    private final byte[] EMPTY_BYTE = new byte[0];

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotDetectAnyWorkerLost(){
        when(workerService.getLostWorkers()).thenReturn(Collections.emptyList());
        workerLostDetector.detect();
        Mockito.verify(taskService, Mockito.times(0))
                .updateReplicateStatus(Mockito.any(), Mockito.any(), Mockito.any());

    }

    @Test
    public void shouldUpdateOneReplicateToWorkerLost(){
        Date twoMinutesAgo = addMinutesToDate(new Date(), -2);
        String taskId = "task1";
        String workerName = "worker1";

        Worker worker = Worker.builder()
                .name(workerName)
                .lastAliveDate(twoMinutesAgo)
                .taskIds(Collections.singletonList(taskId))
                .build();

        Task task = new Task( "dappName", "commandLine", 2, EMPTY_BYTE);
        task.setId(taskId);
        task.createNewReplicate(workerName);
        task.getReplicate(workerName).ifPresent(replicate -> replicate.updateStatus(ReplicateStatus.RUNNING));

        when(workerService.getLostWorkers()).thenReturn(Collections.singletonList(worker));
        when(taskService.getTasks(worker.getTaskIds())).thenReturn(Collections.singletonList(task));

        workerLostDetector.detect();
        // verify that the call on the update is correct
        Mockito.verify(taskService, Mockito.times(1))
                .updateReplicateStatus(task.getId(), worker.getName(), ReplicateStatus.WORKER_LOST);

        // verify that the worker should remove the taskId from its current tasks
        Mockito.verify(workerService, Mockito.times(1))
                .removeTaskIdFromWorker(task.getId(), worker.getName());
    }

    // similar test with previous except that the Replicate is already is WORKER_LOST status.
    @Test
    public void shouldNotUpdateOneReplicateToWorkerLostSinceAlreadyUpdated(){
        Date twoMinutesAgo = addMinutesToDate(new Date(), -2);
        String taskId = "task1";
        String workerName = "worker1";

        Worker worker = Worker.builder()
                .name(workerName)
                .lastAliveDate(twoMinutesAgo)
                .taskIds(Collections.singletonList(taskId))
                .build();

        Task task = new Task("dappName", "commandLine", 2, EMPTY_BYTE);
        task.setId(taskId);
        task.createNewReplicate(workerName);
        task.getReplicate(workerName).ifPresent(replicate -> replicate.updateStatus(ReplicateStatus.RUNNING));
        task.getReplicate(workerName).ifPresent(replicate -> replicate.updateStatus(ReplicateStatus.WORKER_LOST));

        when(workerService.getLostWorkers()).thenReturn(Collections.singletonList(worker));
        when(taskService.getTasks(worker.getTaskIds())).thenReturn(Collections.singletonList(task));

        workerLostDetector.detect();
        // verify that the call on the update is correct
        Mockito.verify(taskService, Mockito.times(0))
                .updateReplicateStatus(task.getId(), worker.getName(), ReplicateStatus.WORKER_LOST);

        // verify that the worker should remove the taskId from its current tasks
        Mockito.verify(workerService, Mockito.times(0))
                .removeTaskIdFromWorker(task.getId(), worker.getName());
    }
}
