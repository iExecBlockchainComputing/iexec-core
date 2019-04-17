package com.iexec.core.replicate;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.disconnection.InterruptedReplicateModel;
import com.iexec.common.disconnection.RecoveryAction;
import com.iexec.common.replicate.ReplicateDetails;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusChange;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.utils.BytesUtils;
import com.iexec.core.chain.SignatureService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.sms.SmsService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskExecutorEngine;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.iexec.core.task.TaskStatus.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;


public class ReplicateSupplyServiceTests {

    private final static String WALLET_WORKER_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";

    private final static String CHAIN_TASK_ID = "0x65bc5e94ed1486b940bd6cc0013c418efad58a0a52a3d08cee89faaa21970426";

    private final static String DAPP_NAME = "dappName";
    private final static String COMMAND_LINE = "commandLine";
    private final static String NO_TEE_TAG = BytesUtils.EMPTY_HEXASTRING_64;
    private final static String TEE_TAG = "0x0000000000000000000000000000000000000000000000000000000000000001";
    private final static String ENCLAVE_CHALLENGE = "dummyEnclave";
    private final static long maxExecutionTime = 60000;

    @Mock private ReplicatesService replicatesService;
    @Mock private SignatureService signatureService;
    @Mock private TaskExecutorEngine taskExecutorEngine;
    @Mock private TaskService taskService;
    @Mock private WorkerService workerService;
    @Mock private SmsService smsService;

    @InjectMocks
    private ReplicateSupplyService replicateSupplyService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }


    // Tests on getAuthOfAvailableReplicate()

    @Test
    public void shouldNotGetAnyReplicateSinceWorkerDoesntExist() {
        when(workerService.getWorker(Mockito.anyString())).thenReturn(Optional.empty());

        Optional<ContributionAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(123, WALLET_WORKER_1);
        assertThat(oAuthorization).isEmpty();
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
        when(taskService.getInitializedOrRunningTasks()).thenReturn(new ArrayList<>());

        Optional<ContributionAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(123, WALLET_WORKER_1);
        assertThat(oAuthorization).isEmpty();
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
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask1));

        Optional<ContributionAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(123, WALLET_WORKER_1);
        assertThat(oAuthorization).isEmpty();
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
        runningTask1.setInitializationBlockNumber(10);
        runningTask1.setMaxExecutionTime(maxExecutionTime);
        runningTask1.changeStatus(RUNNING);
        runningTask1.setTag(NO_TEE_TAG);

        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask1));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(true);
        when(replicatesService.moreReplicatesNeeded(anyString(), anyInt(), anyLong()))
                .thenReturn(true);

        Optional<ContributionAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(123, WALLET_WORKER_1);

        assertThat(oAuthorization).isEmpty();
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
        runningTask.setInitializationBlockNumber(10);
        runningTask.setMaxExecutionTime(maxExecutionTime);
        runningTask.setTag(NO_TEE_TAG);

        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(false);
        when(replicatesService.moreReplicatesNeeded(CHAIN_TASK_ID, runningTask.getNumWorkersNeeded(),
                maxExecutionTime)).thenReturn(false);

        Optional<ContributionAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(123, WALLET_WORKER_1);
        assertThat(oAuthorization).isEmpty();
    }

    @Test
    public void shouldNotGetReplicateSinceEnclaveChallengeNeededButNotGenerated() {
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
        runningTask.setTag(TEE_TAG);

        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask));
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(false);
        when(replicatesService.moreReplicatesNeeded(CHAIN_TASK_ID, runningTask.getNumWorkersNeeded(),
                runningTask.getMaxExecutionTime())).thenReturn(true);
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, true)).thenReturn("");

        Optional<ContributionAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(123, WALLET_WORKER_1);

        assertThat(oAuthorization).isEmpty();

        Mockito.verify(replicatesService, Mockito.times(0))
                .addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService, Mockito.times(0))
                .addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
    }

    @Test
    public void shouldGetReplicateWithNoTee() {
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
        runningTask.setTag(NO_TEE_TAG);

        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask));
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(false);
        when(replicatesService.moreReplicatesNeeded(CHAIN_TASK_ID, runningTask.getNumWorkersNeeded(),
                runningTask.getMaxExecutionTime())).thenReturn(true);
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, false)).thenReturn(BytesUtils.EMPTY_ADDRESS);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, BytesUtils.EMPTY_ADDRESS))
                .thenReturn(new ContributionAuthorization());

        Optional<ContributionAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(123, WALLET_WORKER_1);

        assertThat(oAuthorization).isPresent();

        Mockito.verify(replicatesService, Mockito.times(1))
                .addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService, Mockito.times(1))
                .addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
    }

    @Test
    public void shouldGetReplicateWithTee() {
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

        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask));
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(false);
        when(replicatesService.moreReplicatesNeeded(CHAIN_TASK_ID, runningTask.getNumWorkersNeeded(),
                runningTask.getMaxExecutionTime())).thenReturn(true);
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, true)).thenReturn(ENCLAVE_CHALLENGE);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(new ContributionAuthorization());

        Optional<ContributionAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(123, WALLET_WORKER_1);

        assertThat(oAuthorization).isPresent();

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
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));

        Optional<ContributionAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(123, WALLET_WORKER_1);

        assertThat(oAuthorization).isEmpty();

        Mockito.verify(replicatesService, Mockito.times(0))
                .addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService, Mockito.times(0))
                .addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
    }

    @Test
    public void shouldTeeNeededTaskBeGivenToTeeEnabledWorker() {
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
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(false);
        when(replicatesService.moreReplicatesNeeded(CHAIN_TASK_ID, runningTask.getNumWorkersNeeded(), runningTask.getMaxExecutionTime())).thenReturn(true);
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, true)).thenReturn(ENCLAVE_CHALLENGE);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(new ContributionAuthorization());

        Optional<ContributionAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(123, WALLET_WORKER_1);

        assertThat(oAuthorization).isPresent();

        Mockito.verify(replicatesService, Mockito.times(1))
                .addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService, Mockito.times(1))
                .addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
    }

    // Tests on getInterruptedReplicates()

    @Test
    public void shouldReturnEmptyListSinceNotParticipatingToAnyTask() {

        when(taskService.getTasksByChainTaskIds(any()))
                .thenReturn(Collections.emptyList());

        List<InterruptedReplicateModel> list =
                replicateSupplyService.getInterruptedReplicates(1l, WALLET_WORKER_1);

        assertThat(list).isEmpty();
        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any());
    }

    @Test
    public void shouldNotGetInterruptedReplicateSinceEnclaveChallengeNeededButNotGenerated() {

        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        Task teeTask = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        teeTask.setInitializationBlockNumber(10);
        Optional<Replicate> noTeeReplicate = getStubReplicate(ReplicateStatus.COMPUTING);
        teeTask.setTag(TEE_TAG);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(Arrays.asList(teeTask));
        when(replicatesService.getReplicate(any(), any())).thenReturn(noTeeReplicate);
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, true)).thenReturn("");

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(3l, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isEmpty();

        Mockito.verify(replicatesService, Mockito.times(0))
            .updateReplicateStatus(any(), any(), any(), any());
    }


    @Test
    // CREATED, ..., CAN_CONTRIBUTE => RecoveryAction.CONTRIBUTE
    public void shouldTellReplicateToContributeWhenComputing() {
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RUNNING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.COMPUTING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(3l, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isNotEmpty();
        RecoveryAction action = interruptedReplicates.get(0).getRecoveryAction();
        assertThat(action).isEqualTo(RecoveryAction.CONTRIBUTE);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);
    }

    @Test
    // CONTRIBUTING + !onChain => RecoveryAction.CONTRIBUTE
    public void shouldTellReplicateToContributeSinceNotDoneOnchain() {
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RUNNING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.CONTRIBUTING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        when(replicatesService.didReplicateContributeOnchain(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(false);

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(3l, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isNotEmpty();
        RecoveryAction action = interruptedReplicates.get(0).getRecoveryAction();
        assertThat(action).isEqualTo(RecoveryAction.CONTRIBUTE);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);
    }

    @Test
    // CONTRIBUTING + done onChain   => updateStatus to CONTRIBUTED
    // Task not in CONSENSUS_REACHED => RecoveryAction.WAIT
    public void shouldTellReplicateToWaitSinceContributedOnchain() {
        long blockNumber = 3;
        ChainReceipt chainReceipt = new ChainReceipt(blockNumber, "");
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RUNNING);
        Optional<Replicate> replicate1 = getStubReplicate(ReplicateStatus.CONTRIBUTING);
        Optional<Replicate> replicate2 = getStubReplicate(ReplicateStatus.CONTRIBUTED);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(replicate1)
                .thenReturn(replicate2);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());
        when(replicatesService.didReplicateContributeOnchain(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(true);
        when(taskService.isConsensusReached(taskList.get(0))).thenReturn(false);

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(blockNumber, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isNotEmpty();
        RecoveryAction action = interruptedReplicates.get(0).getRecoveryAction();
        assertThat(action).isEqualTo(RecoveryAction.WAIT);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);
        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.POOL_MANAGER,
                        ReplicateDetails.builder().chainReceipt(chainReceipt).build());
    }

    @Test
    // CONTRIBUTING + done onChain => updateStatus to CONTRIBUTED
    // Task in CONSENSUS_REACHED   => RecoveryAction.REVEAL
    public void shouldTellReplicateToRevealSinceConsensusReached() {
        long blockNumber = 3;
        ChainReceipt chainReceipt = new ChainReceipt(blockNumber, "");
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RUNNING);
        Optional<Replicate> replicate1 = getStubReplicate(ReplicateStatus.CONTRIBUTING);
        Optional<Replicate> replicate2 = getStubReplicate(ReplicateStatus.CONTRIBUTED);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(replicate1)
                .thenReturn(replicate2);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());
        when(replicatesService.didReplicateContributeOnchain(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(true);
        when(taskService.isConsensusReached(taskList.get(0))).thenReturn(true);

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(blockNumber, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isNotEmpty();
        RecoveryAction action = interruptedReplicates.get(0).getRecoveryAction();
        assertThat(action).isEqualTo(RecoveryAction.REVEAL);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);
        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.POOL_MANAGER,
                        ReplicateDetails.builder().chainReceipt(chainReceipt).build());
    }

    @Test
    // any status + Task in CONTRIBUTION_TIMEOUT => RecoveryAction.ABORT_CONTRIBUTION_TIMEOUT
    public void shouldTellReplicateToAbortSinceContributionTimeout() {
        long blockNumber = 3;
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.CONTRIBUTION_TIMEOUT);
        Optional<Replicate> replicate1 = getStubReplicate(ReplicateStatus.CONTRIBUTING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(replicate1);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(blockNumber, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isNotEmpty();
        RecoveryAction action = interruptedReplicates.get(0).getRecoveryAction();
        assertThat(action).isEqualTo(RecoveryAction.ABORT_CONTRIBUTION_TIMEOUT);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);
    }

    @Test
    // !CONTRIBUTED + Task in CONSENSUS_REACHED => RecoveryAction.ABORT_CONSENSUS_REACHED
    public void shouldTellReplicateToWaitSinceConsensusReachedAndItDidNotContribute() {
        long blockNumber = 3;
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.CONSENSUS_REACHED);
        Optional<Replicate> replicate1 = getStubReplicate(ReplicateStatus.RUNNING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(replicate1);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(blockNumber, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isNotEmpty();
        RecoveryAction action = interruptedReplicates.get(0).getRecoveryAction();
        assertThat(action).isEqualTo(RecoveryAction.ABORT_CONSENSUS_REACHED);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);
    }

    @Test
    // CONTRIBUTED + Task in REVEAL phase => RecoveryAction.REVEAL
    public void shouldTellReplicateToRevealSinceContributed() {
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.AT_LEAST_ONE_REVEALED);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.CONTRIBUTED);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(3l, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isNotEmpty();
        RecoveryAction action = interruptedReplicates.get(0).getRecoveryAction();
        assertThat(action).isEqualTo(RecoveryAction.REVEAL);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);
    }

    @Test
    // REVEALING + !onChain => RecoveryAction.REVEAL
    public void shouldTellReplicateToRevealSinceNotDoneOnchain() {
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.AT_LEAST_ONE_REVEALED);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.REVEALING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        when(replicatesService.didReplicateContributeOnchain(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(false);

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(3l, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isNotEmpty();
        RecoveryAction action = interruptedReplicates.get(0).getRecoveryAction();
        assertThat(action).isEqualTo(RecoveryAction.REVEAL);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);
    }

    @Test
    // REVEALING + done onChain     => updateStatus to REVEALED
    // no RESULT_UPLOAD_REQUESTED   => RecoveryAction.WAIT
    public void shouldTellReplicateToWaitSinceRevealed() {
        long blockNumber = 3;
        ChainReceipt chainReceipt = new ChainReceipt(blockNumber, "");
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.AT_LEAST_ONE_REVEALED);
        Optional<Replicate> replicate1 = getStubReplicate(ReplicateStatus.REVEALING);
        Optional<Replicate> replicate2 = getStubReplicate(ReplicateStatus.REVEALED);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(replicate1)
                .thenReturn(replicate2);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());
        when(replicatesService.didReplicateRevealOnchain(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(true);

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        when(taskExecutorEngine.updateTask(CHAIN_TASK_ID)).thenReturn(future);
        future.complete(true);

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(blockNumber, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isNotEmpty();
        RecoveryAction action = interruptedReplicates.get(0).getRecoveryAction();
        assertThat(action).isEqualTo(RecoveryAction.WAIT);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);
        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.REVEALED, ReplicateStatusModifier.POOL_MANAGER,
                        ReplicateDetails.builder().chainReceipt(chainReceipt).build());
    }

    @Test
    // REVEALING + done onChain     => updateStatus to REVEALED
    // RESULT_UPLOAD_REQUESTED   => RecoveryAction.UPLOAD_RESULT
    public void shouldTellReplicateToUploadResultSinceRequestedAfterRevealing() {
        long blockNumber = 3;
        ChainReceipt chainReceipt = new ChainReceipt(blockNumber, "");
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.AT_LEAST_ONE_REVEALED);
        Optional<Replicate> replicate1 = getStubReplicate(ReplicateStatus.REVEALING);
        Optional<Replicate> replicate2 = getStubReplicate(ReplicateStatus.RESULT_UPLOAD_REQUESTED);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(replicate1)
                .thenReturn(replicate2);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());
        when(replicatesService.didReplicateRevealOnchain(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(true);

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        when(taskExecutorEngine.updateTask(CHAIN_TASK_ID)).thenReturn(future);
        future.complete(true);

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(blockNumber, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isNotEmpty();
        RecoveryAction action = interruptedReplicates.get(0).getRecoveryAction();
        assertThat(action).isEqualTo(RecoveryAction.UPLOAD_RESULT);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);
        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.REVEALED, ReplicateStatusModifier.POOL_MANAGER,
                        ReplicateDetails.builder().chainReceipt(chainReceipt).build());
    }

    @Test
    // RESULT_UPLOAD_REQUESTED => RecoveryAction.UPLOAD_RESULT
    public void shouldTellReplicateToUploadResultSinceRequested() {
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RESULT_UPLOAD_REQUESTED);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.RESULT_UPLOAD_REQUESTED);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(3l, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isNotEmpty();
        RecoveryAction action = interruptedReplicates.get(0).getRecoveryAction();
        assertThat(action).isEqualTo(RecoveryAction.UPLOAD_RESULT);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);
    }

    @Test
    // RESULT_UPLOADING + not done yet => RecoveryAction.UPLOAD_RESULT
    public void shouldTellReplicateToUploadResultSinceNotDoneYet() {
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RESULT_UPLOADING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.RESULT_UPLOADING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        when(replicatesService.isResultUploaded(CHAIN_TASK_ID)).thenReturn(false);

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(3l, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isNotEmpty();
        RecoveryAction action = interruptedReplicates.get(0).getRecoveryAction();
        assertThat(action).isEqualTo(RecoveryAction.UPLOAD_RESULT);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);
    }

    @Test
    // RESULT_UPLOADING + done => update to ReplicateStatus.RESULT_UPLOADED
    //                            RecoveryAction.WAIT
    public void shouldTellReplicateToWaitSinceDetectedResultUpload() {
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RESULT_UPLOADING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.RESULT_UPLOADING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        when(replicatesService.isResultUploaded(CHAIN_TASK_ID)).thenReturn(true);

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(3l, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isNotEmpty();
        RecoveryAction action = interruptedReplicates.get(0).getRecoveryAction();
        assertThat(action).isEqualTo(RecoveryAction.WAIT);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RESULT_UPLOADED, ReplicateStatusModifier.POOL_MANAGER);
    }

    @Test
    // RESULT_UPLOADED => RecoveryAction.WAIT
    public void shouldTellReplicateToWaitSinceItUploadedResult() {
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RESULT_UPLOADING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.RESULT_UPLOADED);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        when(replicatesService.isResultUploaded(CHAIN_TASK_ID)).thenReturn(true);

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(3l, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isNotEmpty();
        RecoveryAction action = interruptedReplicates.get(0).getRecoveryAction();
        assertThat(action).isEqualTo(RecoveryAction.WAIT);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RESULT_UPLOADED, ReplicateStatusModifier.POOL_MANAGER);
    }

    @Test
    // REVEALED + Task in completion phase => RecoveryAction.WAIT
    public void shouldTellReplicateToWaitForCompletionSinceItRevealed() {
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.FINALIZING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.REVEALED);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID))
                .thenReturn(Optional.of(taskList.get(0)));
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(3l, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isNotEmpty();
        RecoveryAction action = interruptedReplicates.get(0).getRecoveryAction();
        assertThat(action).isEqualTo(RecoveryAction.WAIT);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);
    }

    @Test
    // REVEALED + RESULT_UPLOADED + Task in completion phase => RecoveryAction.WAIT
    public void shouldTellReplicateToWaitForCompletionSinceItRevealedAndUploaded() {
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.FINALIZING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.REVEALED);
        replicate.get().updateStatus(ReplicateStatus.RESULT_UPLOADED, null);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID))
                .thenReturn(Optional.of(taskList.get(0)));

        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(3l, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isNotEmpty();
        RecoveryAction action = interruptedReplicates.get(0).getRecoveryAction();
        assertThat(action).isEqualTo(RecoveryAction.WAIT);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);
    }

    @Test
    public void shouldTellReplicateToCompleteSinceItRevealed() {
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.FINALIZING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.REVEALED);
        Task completedTask = Task.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .currentStatus(TaskStatus.COMPLETED)
                .build();

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID))
                .thenReturn(Optional.of(completedTask));

        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(3l, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isNotEmpty();
        RecoveryAction action = interruptedReplicates.get(0).getRecoveryAction();
        assertThat(action).isEqualTo(RecoveryAction.COMPLETE);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);
    }

    @Test
    // !REVEALED + Task in completion phase => null / nothing
    public void shouldNotTellReplicateToWaitForCompletionSinceItDidNotReveal() {
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.FINALIZING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.REVEALING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<InterruptedReplicateModel> interruptedReplicates =
                replicateSupplyService.getInterruptedReplicates(3l, WALLET_WORKER_1);

        assertThat(interruptedReplicates).isEmpty();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1,
                        ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);
    }

    List<Task> getStubTaskList(TaskStatus status) {
        Task task = Task.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .currentStatus(status)
                .build();

        return Arrays.asList(task);
    }

    Optional<Replicate> getStubReplicate(ReplicateStatus status) {
        Replicate replicate = new Replicate();
        replicate.setWalletAddress(WALLET_WORKER_1);
        replicate.setChainTaskId(CHAIN_TASK_ID);
        replicate.setStatusChangeList(new ArrayList<ReplicateStatusChange>());
        replicate.updateStatus(status, ReplicateStatusModifier.WORKER);
        return Optional.of(replicate);
    }

    ContributionAuthorization getStubAuth() {
        return new ContributionAuthorization();
    }
}