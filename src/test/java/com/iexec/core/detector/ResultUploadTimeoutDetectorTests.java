package com.iexec.core.detector;

import com.iexec.common.replicate.ReplicateStatus;
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

import static com.iexec.core.utils.DateTimeUtils.addMinutesToDate;
import static org.mockito.Mockito.when;

public class ResultUploadTimeoutDetectorTests {

    @Mock
    private TaskService taskService;

    @InjectMocks
    private ResultUploadTimeoutDetector timeoutDetector;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotDetectAnythingNoTimeout(){
        // the latest status change from the replicate is very new so it is not timed out.

        String taskId = "task1";
        String workerName1 = "worker1";
        String workerName2 = "worker2";

        Task task = new Task("dappName", "commandLine", 2);
        task.setId(taskId);
        task.createNewReplicate(workerName1);
        task.getReplicate(workerName1).ifPresent(replicate -> replicate.updateStatus(ReplicateStatus.RUNNING));
        task.getReplicate(workerName1).ifPresent(replicate -> replicate.updateStatus(ReplicateStatus.COMPUTED));
        task.setUploadingWorkerWalletAddress(workerName1);
        task.changeStatus(TaskStatus.UPLOAD_RESULT_REQUESTED);

        task.createNewReplicate(workerName2);
        task.getReplicate(workerName2).ifPresent(replicate -> replicate.updateStatus(ReplicateStatus.RUNNING));
        task.getReplicate(workerName2).ifPresent(replicate -> replicate.updateStatus(ReplicateStatus.COMPUTED));

        when(taskService.findByCurrentStatus(TaskStatus.UPLOAD_RESULT_REQUESTED)).thenReturn(Collections.singletonList(task));

        // trying to detect any timeout
        timeoutDetector.detect();
        Mockito.verify(taskService, Mockito.times(0))
                .updateReplicateStatus(task.getId(), workerName1, ReplicateStatus.WORKER_LOST);
    }

    @Test
    public void shouldDetectOneReplicateStartedUploadLongAgo(){
        // the latest status change from the replicate is very new so it is not timed out.
        Date twoMinutesAgo = addMinutesToDate(new Date(), -2);
        Date threeMinutesAgo = addMinutesToDate(new Date(), -3);
        Date fourMinutesAgo = addMinutesToDate(new Date(), -4);

        String taskId = "task1";
        String workerName = "worker1";

        Task task = new Task("dappName", "commandLine", 2);
        task.setId(taskId);
        task.createNewReplicate(workerName);

        TaskStatusChange change1 = new TaskStatusChange(fourMinutesAgo, TaskStatus.CREATED);
        TaskStatusChange change2 = new TaskStatusChange(fourMinutesAgo, TaskStatus.RUNNING);
        TaskStatusChange change3 = new TaskStatusChange(threeMinutesAgo, TaskStatus.COMPUTED);
        TaskStatusChange change4 = new TaskStatusChange(twoMinutesAgo, TaskStatus.UPLOAD_RESULT_REQUESTED);

        task.setUploadingWorkerWalletAddress(workerName);
        task.setDateStatusList(Arrays.asList(change1, change2, change3, change4));

        when(taskService.findByCurrentStatus(TaskStatus.UPLOAD_RESULT_REQUESTED)).thenReturn(Collections.singletonList(task));

        // trying to detect any timeout
        timeoutDetector.detect();
        Mockito.verify(taskService, Mockito.times(1))
                .updateReplicateStatus(task.getId(), workerName, ReplicateStatus.UPLOAD_RESULT_REQUEST_FAILED);
    }
}
