package com.iexec.core.replicate;

import com.iexec.common.utils.BytesUtils;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.configuration.ResultRepositoryConfiguration;
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
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

import static com.iexec.core.task.TaskStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;


public class ReplicateSupplyServiceTests {

        private final static String WALLET_WORKER_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";

    private final static String CHAIN_TASK_ID = "0x65bc5e94ed1486b940bd6cc0013c418efad58a0a52a3d08cee89faaa21970426";

    private final static String DAPP_NAME = "dappName";
    private final static String COMMAND_LINE = "commandLine";
    private final long maxExecutionTime = 60000;
    private final static String NO_TEE_TAG = BytesUtils.EMPTY_HEXASTRING_64;
    private final static String TEE_TAG = "0x0000000000000000000000000000000000000000000000000000000000000001";

    @Mock
    private TaskService taskService;

    @Mock
    private WorkerService workerService;

    @Mock
    private IexecHubService iexecHubService;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private ResultRepositoryConfiguration resulRepositoryConfig;

    @InjectMocks
    private ReplicateSupplyService replicateSupplyService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }


    // Tests on the getAvailableReplicate method

    @Test
    public void shouldNotGetAnyReplicateSinceWorkerDoesntExist() {
        when(workerService.getWorker(Mockito.anyString())).thenReturn(Optional.empty());

        Optional<Replicate> optional = replicateSupplyService.getAvailableReplicate(123, WALLET_WORKER_1);
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    public void shouldNotGetReplicateSinceNoRunningTask() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(1)
                .lastAliveDate(new Date())
                .build();

        when(workerService.getWorker(Mockito.anyString())).thenReturn(Optional.of(existingWorker));
        when(taskService.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(new ArrayList<>());

        Optional<Replicate> optional = replicateSupplyService.getAvailableReplicate(123, WALLET_WORKER_1);
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    public void shouldNotGetAnyReplicateSinceWorkerIsFull() {

        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(1)
                .lastAliveDate(new Date())
                .build();

        Task runningTask1 = new Task(DAPP_NAME, COMMAND_LINE, 3, CHAIN_TASK_ID);
        runningTask1.changeStatus(RUNNING);

        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(taskService.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(Collections.singletonList(runningTask1));

        Optional<Replicate> optional = replicateSupplyService.getAvailableReplicate(123, WALLET_WORKER_1);
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    public void shouldNotGetAnyReplicateSinceWorkerAlreadyContributed() {

        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(2)
                .lastAliveDate(new Date())
                .build();

        Task runningTask1 = new Task(DAPP_NAME, COMMAND_LINE, 5);
        runningTask1.changeStatus(RUNNING);
        runningTask1.setTag(NO_TEE_TAG);

        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(taskService.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(Collections.singletonList(runningTask1));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(true);

        Optional<Replicate> optional = replicateSupplyService.getAvailableReplicate(123, WALLET_WORKER_1);
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    public void shouldNotGetReplicateSinceTaskDoesntNeedMoreReplicate() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(2)
                .lastAliveDate(new Date())
                .build();

        Task runningTask = new Task(DAPP_NAME, COMMAND_LINE, 5);
        runningTask.changeStatus(RUNNING);
        runningTask.setTag(NO_TEE_TAG);

        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(taskService.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(Collections.singletonList(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(false);
        when(replicatesService.moreReplicatesNeeded(CHAIN_TASK_ID, runningTask.getNumWorkersNeeded(), maxExecutionTime)).thenReturn(false);

        Optional<Replicate> optional = replicateSupplyService.getAvailableReplicate(123, WALLET_WORKER_1);
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    public void shouldGetAReplicate() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(2)
                .lastAliveDate(new Date())
                .build();

        Task runningTask = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        runningTask.setInitializationBlockNumber(10);
        runningTask.setMaxExecutionTime(maxExecutionTime);
        runningTask.changeStatus(RUNNING);
        runningTask.setTag(NO_TEE_TAG);

        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(taskService.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(Collections.singletonList(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(false);
        when(replicatesService.moreReplicatesNeeded(CHAIN_TASK_ID, runningTask.getNumWorkersNeeded(), runningTask.getMaxExecutionTime())).thenReturn(true);

        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(
                Optional.of(new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID)));

        Optional<Replicate> optional = replicateSupplyService.getAvailableReplicate(123, WALLET_WORKER_1);

        assertThat(optional).isPresent();

        Mockito.verify(replicatesService, Mockito.times(1))
                .addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService, Mockito.times(1))
                .addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
    }

    @Test
    public void shouldTeeNeededTaskNotBeGivenToTeeDisabledWorker() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(2)
                .teeEnabled(false)
                .lastAliveDate(new Date())
                .build();

        Task runningTask = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        runningTask.setInitializationBlockNumber(10);
        runningTask.setMaxExecutionTime(maxExecutionTime);
        runningTask.changeStatus(RUNNING);
        runningTask.setTag(TEE_TAG);

        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(taskService.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(Collections.singletonList(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));

        Optional<Replicate> optional = replicateSupplyService.getAvailableReplicate(123, WALLET_WORKER_1);

        assertThat(optional).isEmpty();

        Mockito.verify(replicatesService, Mockito.times(0))
                .addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService, Mockito.times(0))
                .addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
    }

    @Test
    public void shouldTeeNeededTaskBeGivenToTeeDisabledWorker() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(2)
                .teeEnabled(true)
                .lastAliveDate(new Date())
                .build();

        Task runningTask = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        runningTask.setInitializationBlockNumber(10);
        runningTask.setMaxExecutionTime(maxExecutionTime);
        runningTask.changeStatus(RUNNING);
        runningTask.setTag(TEE_TAG);

        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(taskService.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(Collections.singletonList(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(false);
        when(replicatesService.moreReplicatesNeeded(CHAIN_TASK_ID, runningTask.getNumWorkersNeeded(), runningTask.getMaxExecutionTime())).thenReturn(true);
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(
                Optional.of(new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID)));

        Optional<Replicate> optional = replicateSupplyService.getAvailableReplicate(123, WALLET_WORKER_1);

        assertThat(optional).isPresent();

        Mockito.verify(replicatesService, Mockito.times(1))
                .addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService, Mockito.times(1))
                .addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
    }

}