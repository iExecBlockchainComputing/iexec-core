package com.iexec.core.detector;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.utils.DateTimeUtils;
import com.iexec.core.worker.WorkerService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class ContributionTimeoutDetectorTests {

    private final static String CHAIN_TASK_ID = "chainTaskId";

    @Mock
    private TaskService taskService;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private WorkerService workerService;

    @InjectMocks
    private ContributionTimeoutDetector contributionTimeoutDetector;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotDetectAnyContributionTimeout() {
        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.emptyList());
        contributionTimeoutDetector.detect();

        Mockito.verify(workerService, Mockito.times(0))
                .removeChainTaskIdFromWorker(any(), any());

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any());

        Mockito.verify(taskService, Mockito.times(0))
                .tryToMoveTaskToNextStatus(any());
    }

    @Test
    public void shouldNotUpdateTaskIfBeforeTimeout() {
        Date now = new Date();
        Date oneMinuteAfterNow = DateTimeUtils.addMinutesToDate(now, 1);

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.RUNNING);
        task.setContributionDeadline(oneMinuteAfterNow);

        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));
        contributionTimeoutDetector.detect();

        Mockito.verify(workerService, Mockito.times(0))
                .removeChainTaskIdFromWorker(any(), any());

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any());

        Mockito.verify(taskService, Mockito.times(0))
                .tryToMoveTaskToNextStatus(any());
    }


    @Test
    public void shouldUpdateIfIsTimeout() {
        Date now = new Date();
        Date oneMinuteBeforeNow = DateTimeUtils.addMinutesToDate(now, -1);

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.RUNNING);
        task.setContributionDeadline(oneMinuteBeforeNow);

        Replicate replicate1 = new Replicate("0x1", CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.CREATED);

        Replicate replicate2 = new Replicate("0x2", CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.RUNNING);

        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));
        when(replicatesService.getReplicates(task.getChainTaskId())).thenReturn(Arrays.asList(replicate1, replicate2));
        contributionTimeoutDetector.detect();

        Mockito.verify(workerService, Mockito.times(2))
                .removeChainTaskIdFromWorker(any(), any());

        Mockito.verify(replicatesService, Mockito.times(2))
                .updateReplicateStatus(any(), any(), any());

        Mockito.verify(taskService, Mockito.times(1))
                .tryToMoveTaskToNextStatus(any());
    }

}
