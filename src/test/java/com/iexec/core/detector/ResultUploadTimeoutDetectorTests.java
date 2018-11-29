package com.iexec.core.detector;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.TaskStatusChange;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static com.iexec.core.utils.DateTimeUtils.addMinutesToDate;
import static org.mockito.Mockito.when;

public class ResultUploadTimeoutDetectorTests {

    private final static String WALLET_WORKER_1 = "0x748e091bf16048cb5103E0E10F9D5a8b7fBDd860";
    private final static String WALLET_WORKER_2 = "0x748e091bf16048cb5103E0E10F9D5a8b7fBDd861";
    private final static String CHAIN_TASK_ID = "CHAIN_TASK_ID";

    @Mock
    private TaskService taskService;

    @Mock
    private ReplicatesService replicatesService;

    @InjectMocks
    private ResultUploadTimeoutDetector timeoutDetector;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotDetectAnythingNoTimeout(){
        // the latest status change from the replicate is very new so it is not timed out.

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING);
        replicate1.updateStatus(ReplicateStatus.COMPUTED);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING);
        replicate2.updateStatus(ReplicateStatus.COMPUTED);

        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);
        task.changeStatus(TaskStatus.UPLOAD_RESULT_REQUESTED);

        when(taskService.findByCurrentStatus(TaskStatus.UPLOAD_RESULT_REQUESTED)).thenReturn(Collections.singletonList(task));
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(replicate1));


        // trying to detect any timeout
        timeoutDetector.detect();
        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, ReplicateStatus.WORKER_LOST);
        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_2, ReplicateStatus.WORKER_LOST);
    }

    @Test
    public void shouldDetectOneReplicateStartedUploadLongAgo(){
        // the latest status change from the replicate is very new so it is not timed out.
        Date twoMinutesAgo = addMinutesToDate(new Date(), -2);
        Date threeMinutesAgo = addMinutesToDate(new Date(), -3);
        Date fourMinutesAgo = addMinutesToDate(new Date(), -4);

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.RUNNING);
        replicate1.updateStatus(ReplicateStatus.COMPUTED);

        TaskStatusChange change1 = new TaskStatusChange(fourMinutesAgo, TaskStatus.INITIALIZED);
        TaskStatusChange change2 = new TaskStatusChange(fourMinutesAgo, TaskStatus.RUNNING);
        TaskStatusChange change3 = new TaskStatusChange(threeMinutesAgo, TaskStatus.COMPUTED);
        TaskStatusChange change4 = new TaskStatusChange(twoMinutesAgo, TaskStatus.UPLOAD_RESULT_REQUESTED);

        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);
        task.setDateStatusList(Arrays.asList(change1, change2, change3, change4));

        when(taskService.findByCurrentStatus(TaskStatus.UPLOAD_RESULT_REQUESTED)).thenReturn(Collections.singletonList(task));
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(replicate1));

        // trying to detect any timeout
        timeoutDetector.detect();
        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, ReplicateStatus.UPLOAD_RESULT_REQUEST_FAILED);
    }
}
