package com.iexec.core.detector.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.detector.replicate.ContributionUnnotifiedDetector;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.*;

import java.util.Arrays;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ContributionUnnotifiedDetectorTests {

    private final static String CHAIN_TASK_ID = "chainTaskId";

    @Mock
    private TaskService taskService;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private IexecHubService iexecHubService;

    @Spy
    @InjectMocks
    private ContributionUnnotifiedDetector contributionDetector;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldDetectUnNotifiedContributed() {
        Task task = mock(Task.class);
        when(task.getChainTaskId()).thenReturn(any());
        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));

        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.getCurrentStatus()).thenReturn(ReplicateStatus.COMPUTED);
        when(replicate1.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(true);

        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate1));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(true);
        contributionDetector.detect();

        Mockito.verify(replicatesService, Mockito.times(3))//CAN_CONTRIBUTE & CONTRIBUTING & CONTRIBUTED
                .updateReplicateStatus(any(), any(), any(), any());
    }

    @Test
    public void shouldNotDetectUnNotifiedContributedIfNotContributed() {
        Task task = mock(Task.class);
        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));

        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.getCurrentStatus()).thenReturn(ReplicateStatus.COMPUTED);
        when(replicate1.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(true);

        when(replicatesService.getReplicates(any())).thenReturn(Collections.singletonList(replicate1));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(false);
        contributionDetector.detect();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any());
    }

    @Test
    public void shouldNotYetDetectUnNotifiedContributedIfContributingPeriodTooShort() {
        Task task = mock(Task.class);
        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));

        Replicate replicate1 = mock(Replicate.class);
        when(replicate1.getCurrentStatus()).thenReturn(ReplicateStatus.COMPUTED);
        when(replicate1.isCreatedMoreThanNPeriodsAgo(anyInt(), anyLong())).thenReturn(false);

        when(replicatesService.getReplicates(task.getChainTaskId())).thenReturn(Collections.singletonList(replicate1));
        when(iexecHubService.doesWishedStatusMatchesOnChainStatus(any(), any(), any())).thenReturn(true);
        contributionDetector.detect();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any());
    }

}
