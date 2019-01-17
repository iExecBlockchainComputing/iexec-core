package com.iexec.core.detector;

import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.iexec.core.utils.DateTimeUtils.addMinutesToDate;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RevealTimeoutDetectorTests {

    private final static String WALLET_WORKER_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private final static String WALLET_WORKER_2 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd249";
    private final static String CHAIN_TASK_ID = "chainTaskId";

    @Mock
    private TaskService taskService;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private IexecHubService iexecHubService;

    @InjectMocks
    private RevealTimeoutDetector revealDetector;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldDetectSingleRevealTimeout() {
        Date twoMinutesAgo = addMinutesToDate(new Date(), -2);

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.CONSENSUS_REACHED);
		task.setRevealDeadline(twoMinutesAgo);
		List<Task> taskList = Collections.singletonList(task);

        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
		replicate2.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
		List<Replicate> replicateList = Arrays.asList(replicate1, replicate2);

		List<TaskStatus> taskStatusList = Arrays.asList(TaskStatus.AT_LEAST_ONE_REVEALED,
		TaskStatus.RESULT_UPLOAD_REQUESTED, TaskStatus.RESULT_UPLOADING, TaskStatus.RESULT_UPLOADED);

		when(taskService.findByCurrentStatus(taskStatusList)).thenReturn(taskList);
        when(replicatesService.getReplicates(task.getChainTaskId())).thenReturn(replicateList);
        when(taskService.findByCurrentStatus(TaskStatus.CONSENSUS_REACHED)).thenReturn(Collections.emptyList());

		revealDetector.detect();
		
		Mockito.verify(replicatesService, Mockito.times(1))
			.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
				ReplicateStatus.REVEAL_TIMEOUT, ReplicateStatusModifier.POOL_MANAGER);

        Mockito.verify(replicatesService, Mockito.times(1))
			.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_2,
				ReplicateStatus.REVEAL_TIMEOUT, ReplicateStatusModifier.POOL_MANAGER);
}

	@Test
    public void shouldDetectReOpenCase() {
        Date twoMinutesAgo = addMinutesToDate(new Date(), -2);

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.CONSENSUS_REACHED);
		task.setRevealDeadline(twoMinutesAgo);
		List<Task> taskList = Collections.singletonList(task);

        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
		replicate2.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
		List<Replicate> replicateList = Arrays.asList(replicate1, replicate2);

		List<TaskStatus> taskStatusList = Arrays.asList(TaskStatus.AT_LEAST_ONE_REVEALED,
		TaskStatus.RESULT_UPLOAD_REQUESTED, TaskStatus.RESULT_UPLOADING, TaskStatus.RESULT_UPLOADED);

		when(taskService.findByCurrentStatus(taskStatusList)).thenReturn(Collections.emptyList());
        when(replicatesService.getReplicates(task.getChainTaskId())).thenReturn(replicateList);
        when(taskService.findByCurrentStatus(TaskStatus.CONSENSUS_REACHED)).thenReturn(taskList);

		revealDetector.detect();

		Mockito.verify(replicatesService, Mockito.times(1))
			.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
				ReplicateStatus.REVEAL_TIMEOUT, ReplicateStatusModifier.POOL_MANAGER);

        Mockito.verify(replicatesService, Mockito.times(1))
			.updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_2,
				ReplicateStatus.REVEAL_TIMEOUT, ReplicateStatusModifier.POOL_MANAGER);

		Mockito.verify(taskService, Mockito.times(1))
			.reOpenTask(task);
	}

    @Test
    public void shouldNotDetectAnyRevealTimeout() {
		List<TaskStatus> taskStatusList = Arrays.asList(TaskStatus.AT_LEAST_ONE_REVEALED,
		TaskStatus.RESULT_UPLOAD_REQUESTED, TaskStatus.RESULT_UPLOADING, TaskStatus.RESULT_UPLOADED);

		when(taskService.findByCurrentStatus(taskStatusList))
			.thenReturn(Collections.emptyList());
		when(taskService.findByCurrentStatus(TaskStatus.CONSENSUS_REACHED))
			.thenReturn(Collections.emptyList());

        revealDetector.detect();

        Mockito.verify(replicatesService, Mockito.times(0))
			.getReplicates(Mockito.any());

        Mockito.verify(replicatesService, Mockito.times(0))
			.updateReplicateStatus(any(), any(), any(), any());

        Mockito.verify(iexecHubService, Mockito.times(0))
			.reOpen(Mockito.any());
    }


    @Test
    public void shouldUpdateOneReplicateToRevealTimeout() {
        Date twoMinutesAgo = addMinutesToDate(new Date(), -2);

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.CONSENSUS_REACHED);
        task.setRevealDeadline(twoMinutesAgo);

        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);


        when(taskService.findByCurrentStatus(TaskStatus.CONSENSUS_REACHED)).thenReturn(Collections.singletonList(task));
        when(replicatesService.getReplicates(task.getChainTaskId())).thenReturn(Arrays.asList(replicate1, replicate2));

        revealDetector.detect();

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.REVEAL_TIMEOUT, ReplicateStatusModifier.POOL_MANAGER);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_2,
                        ReplicateStatus.REVEAL_TIMEOUT, ReplicateStatusModifier.POOL_MANAGER);

        Mockito.verify(taskService, Mockito.times(1))
                .reOpenTask(task);
    }

    @Test
    public void shouldNotUpdateSinceTaskIsNotTimedout() {
        Date twoMinutesInFuture = addMinutesToDate(new Date(), 2);

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.CONSENSUS_REACHED);
        task.setRevealDeadline(twoMinutesInFuture);

        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);


        when(taskService.findByCurrentStatus(TaskStatus.CONSENSUS_REACHED)).thenReturn(Collections.singletonList(task));
        when(replicatesService.getReplicates(task.getChainTaskId())).thenReturn(Arrays.asList(replicate1, replicate2));

        revealDetector.detect();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any());

        Mockito.verify(iexecHubService, Mockito.times(0))
                .reOpen(Mockito.any());
    }
}
