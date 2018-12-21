package com.iexec.core.task;

import com.iexec.common.chain.ChainContribution;
import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ChainTaskStatus;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.pubsub.NotificationService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
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
import java.util.concurrent.ExecutionException;

import static com.iexec.core.task.TaskStatus.*;
import static com.iexec.core.utils.DateTimeUtils.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

public class TaskServiceTests {

    private final static String WALLET_WORKER_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private final static String CHAIN_TASK_ID = "chainTaskId";
    private final static String CHAIN_DEAL_ID = "dealId";
    private final static String DAPP_NAME = "dappName";
    private final static String COMMAND_LINE = "commandLine";

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private WorkerService workerService;

    @Mock
    private IexecHubService iexecHubService;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private TaskService taskService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotGetTask() {
        when(taskRepository.findByChainTaskId("dummyId")).thenReturn(Optional.empty());
        Optional<Task> task = taskService.getTaskByChainTaskId("dummyId");
        assertThat(task.isPresent()).isFalse();
    }

    @Test
    public void shouldGetOneTask() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        when(taskRepository.findByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        Optional<Task> optional = taskService.getTaskByChainTaskId(CHAIN_TASK_ID);

        assertThat(optional.isPresent()).isTrue();
        assertThat(optional).isEqualTo(Optional.of(task));
    }

    @Test
    public void shouldAddTask() {
        Task task = new Task(CHAIN_DEAL_ID, 0, DAPP_NAME, COMMAND_LINE, 2);
        task.changeStatus(TaskStatus.INITIALIZED);

        when(taskRepository.save(any())).thenReturn(task);
        Optional<Task> saved = taskService.addTask(CHAIN_DEAL_ID, 0, DAPP_NAME, COMMAND_LINE, 2);
        assertThat(saved).isPresent();
        assertThat(saved).isEqualTo(Optional.of(task));
    }

    @Test
    public void shouldNotAddTask() {
        Task task = new Task(CHAIN_DEAL_ID, 0, DAPP_NAME, COMMAND_LINE, 2);
        task.changeStatus(TaskStatus.INITIALIZED);

        ArrayList<Task> list = new ArrayList<>();
        list.add(task);

        when(taskRepository.findByChainDealIdAndTaskIndex(CHAIN_DEAL_ID, 0)).thenReturn(list);
        Optional<Task> saved = taskService.addTask(CHAIN_DEAL_ID, 0, DAPP_NAME, COMMAND_LINE, 2);
        assertThat(saved).isEqualTo(Optional.empty());
    }

    // Tests on received2Initialized transition

    @Test
    public void shouldUpdateReceived2Initializing2Initialized() throws ExecutionException, InterruptedException {
        Task task = new Task(CHAIN_DEAL_ID, 1, DAPP_NAME, COMMAND_LINE, 2);
        task.changeStatus(TaskStatus.RECEIVED);
        task.setChainTaskId("");

        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.initialize(CHAIN_DEAL_ID, 1)).thenReturn(CHAIN_TASK_ID);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(INITIALIZING);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 1).getStatus()).isEqualTo(INITIALIZED);

        // test that double call doesn't change anything
        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    public void shouldNotUpdateReceived2Initializing() throws ExecutionException, InterruptedException {
        Task task = new Task(CHAIN_DEAL_ID, 1, DAPP_NAME, COMMAND_LINE, 2);
        task.changeStatus(TaskStatus.RECEIVED);
        task.setChainTaskId("");

        when(iexecHubService.hasEnoughGas()).thenReturn(false);
        when(iexecHubService.initialize(CHAIN_DEAL_ID, 1)).thenReturn(CHAIN_TASK_ID);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
    }

    @Test
    public void shouldUpdateReceived2InitializedFailed() throws ExecutionException, InterruptedException {
        Task task = new Task(CHAIN_DEAL_ID, 1, DAPP_NAME, COMMAND_LINE, 2);
        task.changeStatus(RECEIVED);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);

        when(iexecHubService.initialize(CHAIN_DEAL_ID, 1)).thenReturn("");
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZE_FAILED);
    }

    @Test
    public void shouldNotUpdateReceived2InitializedSinceChainTaskIdNotEmpty() throws ExecutionException, InterruptedException {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(RECEIVED);

        when(iexecHubService.initialize(CHAIN_DEAL_ID, 1)).thenReturn(CHAIN_TASK_ID);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(INITIALIZED);
    }

    // Tests on initialized2Running transition

    @Test
    public void shouldUpdateInitialized2Running() { // 1 RUNNING out of 2
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 3, CHAIN_TASK_ID);
        task.changeStatus(INITIALIZED);

        when(replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED)).thenReturn(2);
        when(replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.COMPUTED)).thenReturn(0);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    public void shouldNOTUpdateInitialized2RunningSinceNoRunningReplicates() { // 0 RUNNING or COMPUTED
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(INITIALIZED);

        when(replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED)).thenReturn(0);
        when(replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.COMPUTED)).thenReturn(0);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(RUNNING);
    }

    // Tests on running2ConsensusReached transition

    @Test
    public void shouldUpdateRunning2ConsensusReached() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(RUNNING);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(2)
                .build()));
        when(replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.CONTRIBUTED)).thenReturn(2);
        when(taskRepository.save(task)).thenReturn(task);
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    @Test
    public void shouldNotUpdateRunning2ConsensusReachedSinceWrongTaskStatus() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(INITIALIZED);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(2)
                .build()));
        when(replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.CONTRIBUTED)).thenReturn(2);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    public void shouldNOTUpdateRunning2ConsensusReachedSinceOnChainStatusNotRevealing() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(RUNNING);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.UNSET)
                .winnerCounter(2)
                .build()));
        when(replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.CONTRIBUTED)).thenReturn(2);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    public void shouldNOTUpdateRunning2ConsensusReachedSinceWinnerContributorsDiffers() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(RUNNING);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(2)
                .build()));
        when(replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.CONTRIBUTED)).thenReturn(1);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
    }

    // Tests on consensusReached2AtLeastOneReveal2UploadRequested transition

    @Test
    public void shouldUpdateConsensusReached2AtLeastOneReveal2UploadRequested() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(CONSENSUS_REACHED);
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED);

        when(replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.REVEALED)).thenReturn(1);
        when(taskRepository.save(task)).thenReturn(task);
        when(replicatesService.getReplicateWithRevealStatus(task.getChainTaskId())).thenReturn(Optional.of(replicate));
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getUploadingWorkerWalletAddress()).isEqualTo(replicate.getWalletAddress());
        int size = task.getDateStatusList().size();
        assertThat(task.getDateStatusList().get(size - 2).getStatus()).isEqualTo(AT_LEAST_ONE_REVEALED);
        assertThat(task.getDateStatusList().get(size - 1).getStatus()).isEqualTo(RESULT_UPLOAD_REQUESTED);
    }

    @Test
    public void shouldNOTUpdateConsensusReached2AtLeastOneRevealSinceNoRevealedReplicate() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(CONSENSUS_REACHED);
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED);

        when(replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.REVEALED)).thenReturn(0);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    // Tests on uploadRequested2UploadingResult transition
    @Test
    public void shouldUpdateFromUploadRequestedToUploadingResult() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.setCurrentStatus(RESULT_UPLOAD_REQUESTED);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RESULT_UPLOADING)).thenReturn(1);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RESULT_UPLOADING);
    }

    @Test
    public void shouldUpdateFromUploadRequestedToUploadingResultSinceNoWorkerUploading() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(RESULT_UPLOAD_REQUESTED);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RESULT_UPLOADING)).thenReturn(0);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RESULT_UPLOAD_REQUESTED);
        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RESULT_UPLOAD_REQUESTED);
    }

    // Test on resultUploading2Uploaded2Finalizing2Finalized

    @Test
    public void shouldUpdateResultUploading2Uploaded2Finalizing2Finalized() throws ExecutionException, InterruptedException { //one worker uploaded
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(RESULT_UPLOADING);

        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RESULT_UPLOADED)).thenReturn(1);
        when(iexecHubService.canFinalize(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.finalizeTask(any(), any())).thenReturn(true);

        taskService.tryToMoveTaskToNextStatus(task);

        TaskStatus lastButOneStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus();
        TaskStatus lastButTwoStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus();
        TaskStatus lastButThreeStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 4).getStatus();

        assertThat(task.getCurrentStatus()).isEqualTo(COMPLETED);
        assertThat(lastButOneStatus).isEqualTo(FINALIZED);
        assertThat(lastButTwoStatus).isEqualTo(FINALIZING);
        assertThat(lastButThreeStatus).isEqualTo(RESULT_UPLOADED);
    }

    @Test
    public void shouldUpdateResultUploading2UploadedButNot2Finalizing() throws ExecutionException, InterruptedException { //one worker uploaded
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(RESULT_UPLOADING);

        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RESULT_UPLOADED)).thenReturn(1);
        when(iexecHubService.canFinalize(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(false);

        taskService.tryToMoveTaskToNextStatus(task);

        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(FINALIZING);
    }

    @Test
    public void shouldUpdateResultUploading2Uploaded2Finalizing2FinalizeFail() throws ExecutionException, InterruptedException { //one worker uploaded && finalize FAIL
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(RESULT_UPLOADING);

        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RESULT_UPLOADED)).thenReturn(1);
        when(iexecHubService.canFinalize(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.finalizeTask(any(), any())).thenReturn(false);

        taskService.tryToMoveTaskToNextStatus(task);

        TaskStatus lastButOneStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus();
        TaskStatus lastButTwoStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus();

        assertThat(task.getCurrentStatus()).isEqualTo(FINALIZE_FAILED);
        assertThat(lastButOneStatus).isEqualTo(FINALIZING);
        assertThat(lastButTwoStatus).isEqualTo(RESULT_UPLOADED);
    }

    @Test
    public void shouldUpdateResultUploading2UploadedFailAndRequestUploadAgain() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(RESULT_UPLOADING);

        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED);

        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RESULT_UPLOADED)).thenReturn(0);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RESULT_UPLOAD_REQUEST_FAILED)).thenReturn(1);
        when(replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.REVEALED)).thenReturn(1);
        when(replicatesService.getReplicateWithRevealStatus(task.getChainTaskId())).thenReturn(Optional.of(replicate));
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getUploadingWorkerWalletAddress()).isEqualTo(replicate.getWalletAddress());
        int size = task.getDateStatusList().size();
        assertThat(task.getDateStatusList().get(size - 2).getStatus()).isEqualTo(RESULT_UPLOADING);
        assertThat(task.getDateStatusList().get(size - 1).getStatus()).isEqualTo(RESULT_UPLOAD_REQUESTED);
    }



    @Test
    public void shouldWaitUpdateReplicateStatusFromUnsetToContributed() {
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("0x1", "chainTaskId"));

        replicates.get(0).updateStatus(ReplicateStatus.COMPUTED);

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.RUNNING));

        Task task = Task.builder()
                .id("taskId")
                .chainTaskId("chainTaskId")
                .currentStatus(TaskStatus.RUNNING)
                .commandLine("ls")
                .numWorkersNeeded(2)
                .dateStatusList(dateStatusList)
                .build();

        when(taskRepository.findByChainTaskId("chainTaskId")).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);
        ChainContribution chainContribution = ChainContribution.builder().status(ChainContributionStatus.UNSET).build();
        when(iexecHubService.getContribution("chainTaskId", "0x1")).thenReturn(Optional.of(chainContribution));


        Runnable runnable1 = () -> {
            // taskService.updateReplicateStatus("chainTaskId", "0x1", ReplicateStatus.CONTRIBUTED);
            // Optional<Replicate> replicate = task.getReplicate("0x1");
            // assertThat(replicate.isPresent()).isTrue();
            // assertThat(replicate.get().getCurrentStatus()).isEqualTo(ReplicateStatus.COMPUTED);
        };

        Thread thread1 = new Thread(runnable1);
        thread1.start();

        Runnable runnable2 = () -> {
            sleep(500L);
            chainContribution.setStatus(ChainContributionStatus.CONTRIBUTED);
            sleep(500L);
            // assertThat(task.getReplicate("0x1").get().getCurrentStatus()).isEqualTo(ReplicateStatus.CONTRIBUTED);
        };

        Thread thread2 = new Thread(runnable2);
        thread2.start();
    }


    // 3 replicates in RUNNING 0 in COMPUTED
    @Test
    public void shouldUpdateTaskToRunningFromWorkersInRunning() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 3, CHAIN_TASK_ID);
        task.changeStatus(INITIALIZED);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED)).thenReturn(3);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(0);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    // 2 replicates in RUNNING and and 2 in COMPUTED
    @Test
    public void shouldUpdateTaskToRunningFromWorkersInRunningAndComputed() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 8, CHAIN_TASK_ID);
        task.changeStatus(INITIALIZED);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED)).thenReturn(4);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(2);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    // all replicates in INITIALIZED
    @Test
    public void shouldNotUpdateToRunningSinceAllReplicatesInCreated() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 4, CHAIN_TASK_ID);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED)).thenReturn(0);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(0);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RUNNING);
    }

    // Two replicates in COMPUTED BUT numWorkersNeeded = 2, so the task should not be able to move directly from
    // INITIALIZED to COMPUTED
    @Test
    public void shouldNotUpdateToRunningCase2() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 3, CHAIN_TASK_ID);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED)).thenReturn(2);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(2);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RUNNING);
    }


    // at least one UPLOADED
    @Test
    public void shouldUpdateFromUploadingResultToResultUploaded() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 3, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.RUNNING);
        task.changeStatus(TaskStatus.RESULT_UPLOAD_REQUESTED);
        task.changeStatus(TaskStatus.RESULT_UPLOADING);

        when(replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.RESULT_UPLOADED)).thenReturn(1);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RESULT_UPLOADED);
    }

    // No worker in UPLOADED
    @Test
    public void shouldNotUpdateToResultUploaded() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 3, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.RUNNING);
        task.changeStatus(TaskStatus.RESULT_UPLOAD_REQUESTED);
        task.changeStatus(TaskStatus.RESULT_UPLOADING);

        when(replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.RESULT_UPLOADED)).thenReturn(0);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RESULT_UPLOADED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.COMPLETED);
    }

    // Tests on the getAvailableReplicate method

    @Test
    public void shouldNotGetAnyReplicateSinceWorkerDoesntExist() {
        when(workerService.getWorker(Mockito.anyString())).thenReturn(Optional.empty());

        Optional<Replicate> optional = taskService.getAvailableReplicate(WALLET_WORKER_1);
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
        when(taskRepository.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(new ArrayList<>());

        Optional<Replicate> optional = taskService.getAvailableReplicate(WALLET_WORKER_1);
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
        when(taskRepository.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(Collections.singletonList(runningTask1));

        Optional<Replicate> optional = taskService.getAvailableReplicate(WALLET_WORKER_1);
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

        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(taskRepository.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(Collections.singletonList(runningTask1));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.hasWorkerAlreadyContributed(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(true);

        Optional<Replicate> optional = taskService.getAvailableReplicate(WALLET_WORKER_1);
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

        Task runningTask1 = new Task(DAPP_NAME, COMMAND_LINE, 5);
        runningTask1.changeStatus(RUNNING);

        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(taskRepository.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(Collections.singletonList(runningTask1));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.hasWorkerAlreadyContributed(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(false);
        when(replicatesService.moreReplicatesNeeded(CHAIN_TASK_ID, runningTask1.getNumWorkersNeeded())).thenReturn(false);

        Optional<Replicate> optional = taskService.getAvailableReplicate(WALLET_WORKER_1);
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

        Task runningTask1 = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        runningTask1.changeStatus(RUNNING);

        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(taskRepository.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(Collections.singletonList(runningTask1));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.hasWorkerAlreadyContributed(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(false);
        when(replicatesService.moreReplicatesNeeded(CHAIN_TASK_ID, runningTask1.getNumWorkersNeeded())).thenReturn(true);

        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(
                Optional.of(new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID)));

        Optional<Replicate> optional = taskService.getAvailableReplicate(WALLET_WORKER_1);
        assertThat(optional.isPresent()).isTrue();

        Mockito.verify(replicatesService, Mockito.times(1))
                .addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService, Mockito.times(1))
                .addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
    }
}
