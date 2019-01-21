package com.iexec.core.detector;

import com.iexec.core.chain.IexecHubService;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskExecutorEngine;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.utils.DateTimeUtils;
import com.iexec.core.worker.WorkerService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class ContributionTimeoutTaskDetectorTests {

    private final static String CHAIN_TASK_ID = "chainTaskId";

    @Mock
    private TaskService taskService;

    @Mock
    private TaskExecutorEngine taskExecutorEngine;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private WorkerService workerService;

    @Spy
    @InjectMocks
    private ContributionTimeoutTaskDetector contributionDetector;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotDetectAnyContributionTimeout() {
        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.emptyList());
        contributionDetector.detect();

        Mockito.verify(workerService, Mockito.times(0))
                .removeChainTaskIdFromWorker(any(), any());

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any());

        Mockito.verify(taskExecutorEngine, Mockito.times(0))
                .updateTask(any());
    }

    @Test
    public void shouldNotUpdateTaskIfBeforeTimeout() {
        Date now = new Date();
        Date oneMinuteAfterNow = DateTimeUtils.addMinutesToDate(now, 1);

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.RUNNING);
        task.setContributionDeadline(oneMinuteAfterNow);

        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));
        contributionDetector.detect();

        Mockito.verify(workerService, Mockito.times(0))
                .removeChainTaskIdFromWorker(any(), any());

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any());

        Mockito.verify(taskExecutorEngine, Mockito.times(0))
                .updateTask(any());
    }


    @Test
    public void shouldUpdateIfIsTimeout() {
        Date now = new Date();
        Date oneMinuteBeforeNow = DateTimeUtils.addMinutesToDate(now, -1);

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.RUNNING);
        task.setContributionDeadline(oneMinuteBeforeNow);

        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));

        contributionDetector.detect();

        Mockito.verify(taskExecutorEngine, Mockito.times(1))
                .updateTask(any());
    }
}
