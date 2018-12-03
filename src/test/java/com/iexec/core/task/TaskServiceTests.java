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
    public void shouldUpdateReceived2Initialized() {
        Task task = new Task(CHAIN_DEAL_ID, 1, DAPP_NAME, COMMAND_LINE, 2);
        task.changeStatus(TaskStatus.RECEIVED);
        task.setChainTaskId("");

        when(iexecHubService.initializeTask(CHAIN_DEAL_ID, 1)).thenReturn(CHAIN_TASK_ID);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);

        // test that double call doesn't change anything
        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    public void shouldUpdateReceived2InitializedFailed() {
        Task task = new Task(CHAIN_DEAL_ID, 1, DAPP_NAME, COMMAND_LINE, 2);
        task.changeStatus(RECEIVED);

        when(iexecHubService.initializeTask(CHAIN_DEAL_ID, 1)).thenReturn("");
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZE_FAILED);
    }

    @Test
    public void shouldNotUpdateReceived2InitializedSinceChainTaskIdNotEmpty() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(RECEIVED);

        when(iexecHubService.initializeTask(CHAIN_DEAL_ID, 1)).thenReturn(CHAIN_TASK_ID);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(INITIALIZED);
    }

    // Tests on initialized2Running transition

    @Test
    public void shouldUpdateInitialized2Running() { // 1 RUNNING out of 2
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
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

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(2)
                .build());
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

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(2)
                .build());
        when(replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.CONTRIBUTED)).thenReturn(2);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    public void shouldNOTUpdateRunning2ConsensusReachedSinceOnChainStatusNotRevealing() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(RUNNING);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(ChainTask.builder()
                .status(ChainTaskStatus.UNSET)
                .winnerCounter(2)
                .build());
        when(replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.CONTRIBUTED)).thenReturn(2);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    public void shouldNOTUpdateRunning2ConsensusReachedSinceWinnerContributorsDiffers() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(RUNNING);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(2)
                .build());
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
    public void shouldUpdateResultUploading2Uploaded2Finalizing2Finalized() { //one worker uploaded
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(RESULT_UPLOADING);

        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RESULT_UPLOADED)).thenReturn(1);
        when(iexecHubService.canFinalize(task.getChainTaskId())).thenReturn(true);
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
    public void shouldUpdateResultUploading2Uploaded2Finalizing2FinalizeFail() { //one worker uploaded && finalize FAIL
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(RESULT_UPLOADING);

        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RESULT_UPLOADED)).thenReturn(1);
        when(iexecHubService.canFinalize(task.getChainTaskId())).thenReturn(true);
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
    public void shouldWaitUpdateReplicateStatusFromUnsetToContributed() throws InterruptedException {//UNSET on-chain
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
                .trust(2)
                .dateStatusList(dateStatusList)
                .build();

        when(taskRepository.findByChainTaskId("chainTaskId")).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        ChainContribution onChainContribution = new ChainContribution();
        onChainContribution.setStatus(ChainContributionStatus.UNSET);
        when(iexecHubService.getContribution("chainTaskId", "0x1")).thenReturn(onChainContribution);


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
            onChainContribution.setStatus(ChainContributionStatus.CONTRIBUTED);
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
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 4, CHAIN_TASK_ID);
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

    // Two replicates in COMPUTED BUT trust = 2, so the task should not be able to move directly from
    // INITIALIZED to COMPUTED
    @Test
    public void shouldNotUpdateToRunningCase2() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED)).thenReturn(2);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(2);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RUNNING);
    }


    // at least one UPLOADED
    @Test
    public void shouldUpdateFromUploadingResultToResultUploaded() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
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
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.RUNNING);
        task.changeStatus(TaskStatus.RESULT_UPLOAD_REQUESTED);
        task.changeStatus(TaskStatus.RESULT_UPLOADING);

        when(replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.RESULT_UPLOADED)).thenReturn(0);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RESULT_UPLOADED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.COMPLETED);
    }

    // Tests on the getAvailableReplicate method

    // TODOOOOOOOOOOOOOOO

    @Test
    public void shouldNotGetAnyReplicateSinceWorkerDoesntExist() {
        when(workerService.getWorker(Mockito.anyString())).thenReturn(Optional.empty());

        Optional<Replicate> optional = taskService.getAvailableReplicate(WALLET_WORKER_1);
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    public void shouldNotGetReplicateSinceNoRunningTask() {
        String workerName = "worker1";

        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .name(workerName)
                .cpuNb(1)
                .lastAliveDate(new Date())
                .build();

        when(workerService.getWorker(Mockito.anyString())).thenReturn(Optional.of(existingWorker));
        when(taskRepository.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(new ArrayList<>());

        Optional<Replicate> optional = taskService.getAvailableReplicate(workerName);
        assertThat(optional.isPresent()).isFalse();
    }

    // @Test
    public void shouldNotGetAnyReplicateSinceWorkerIsFull() {
        String workerName = "worker1";

        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .name(workerName)
                .cpuNb(1)
                .lastAliveDate(new Date())
                .build();

        List<Replicate> listReplicates1 = new ArrayList<>();
        listReplicates1.add(new Replicate(workerName, "chainTaskId"));
        listReplicates1.add(new Replicate("worker2", "chainTaskId"));
        listReplicates1.get(0).updateStatus(ReplicateStatus.RUNNING);
        listReplicates1.get(1).updateStatus(ReplicateStatus.RUNNING);

        Task runningTask1 = new Task(DAPP_NAME, "command", 3);
        runningTask1.setId("task1");
        runningTask1.changeStatus(RUNNING);
        // runningTask1.setReplicates(listReplicates1);

        List<Replicate> listReplicates2 = new ArrayList<>();
        listReplicates2.add(new Replicate("worker2", "chainTaskId2"));
        listReplicates2.get(0).updateStatus(ReplicateStatus.RUNNING);

        Task runningTask2 = new Task("dappName2", "command", 3);
        runningTask2.setId("task2");
        runningTask2.changeStatus(RUNNING);
        // runningTask2.setReplicates(listReplicates2);

        when(workerService.getWorker(workerName)).thenReturn(Optional.of(existingWorker));
        when(taskRepository.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(Arrays.asList(runningTask1, runningTask2));

        Optional<Replicate> optional = taskService.getAvailableReplicate(workerName);
        assertThat(optional.isPresent()).isFalse();
    }

    // @Test
    public void shouldNotGetAnyReplicateSinceWorkerAlreadyContributed() {
        String workerName = "worker1";

        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .cpuNb(2)
                .lastAliveDate(new Date())
                .build();

        List<Replicate> listReplicates1 = new ArrayList<>();
        listReplicates1.add(new Replicate(workerName, "chainTaskId"));
        listReplicates1.add(new Replicate("worker2", "chainTaskId"));
        listReplicates1.get(0).updateStatus(ReplicateStatus.RUNNING);
        listReplicates1.get(1).updateStatus(ReplicateStatus.RUNNING);

        Task runningTask1 = new Task(DAPP_NAME, "command", 3);
        runningTask1.setId("task1");
        runningTask1.changeStatus(RUNNING);
        // runningTask1.setReplicates(listReplicates1);

        when(workerService.getWorker(workerName)).thenReturn(Optional.of(existingWorker));
        when(taskRepository.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(Collections.singletonList(runningTask1));

        Optional<Replicate> optional = taskService.getAvailableReplicate(workerName);
        assertThat(optional.isPresent()).isFalse();
    }

    // @Test
    public void shouldNotGetReplicateSinceTaskDoesntNeedMoreReplicate() {
        String workerName = "worker1";
        String taskId = "task1";

        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .cpuNb(2)
                .lastAliveDate(new Date())
                .build();

        List<Replicate> listReplicates1 = new ArrayList<>();
        listReplicates1.add(new Replicate("worker2", taskId));
        listReplicates1.get(0).updateStatus(ReplicateStatus.RUNNING);

        Task runningTask1 = new Task(DAPP_NAME, "command", 1);
        runningTask1.setId(taskId);
        runningTask1.changeStatus(RUNNING);

        when(workerService.getWorker(workerName)).thenReturn(Optional.of(existingWorker));
        when(taskRepository.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(Collections.singletonList(runningTask1));
        when(taskRepository.save(any())).thenReturn(runningTask1);
        when(workerService.addChainTaskIdToWorker(taskId, workerName)).thenReturn(Optional.of(existingWorker));

        Optional<Replicate> optional = taskService.getAvailableReplicate(workerName);
        assertThat(optional.isPresent()).isFalse();
    }

    // @Test
    public void shouldGetAReplicate() {
        String taskId = "task1";
        String chainTaskId = "chainTaskId1";
        String walletAddress = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";

        Worker existingWorker = Worker.builder()
                .id("1")
                .name("worker1")
                .walletAddress(walletAddress)
                .cpuNb(2)
                .lastAliveDate(new Date())
                .build();

        List<Replicate> listReplicates1 = new ArrayList<>();
        listReplicates1.add(new Replicate("worker2", chainTaskId));
        listReplicates1.get(0).updateStatus(ReplicateStatus.RUNNING);

        Task runningTask1 = new Task(DAPP_NAME, "command", 3);
        runningTask1.setId(taskId);
        runningTask1.changeStatus(RUNNING);
        // runningTask1.setReplicates(listReplicates1);

        when(workerService.getWorker(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(taskRepository.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING)))
                .thenReturn(Collections.singletonList(runningTask1));
        when(taskRepository.save(any())).thenReturn(runningTask1);
        when(workerService.addChainTaskIdToWorker(taskId, walletAddress)).thenReturn(Optional.of(existingWorker));

        Optional<Replicate> optional = taskService.getAvailableReplicate(walletAddress);
        assertThat(optional.isPresent()).isTrue();
        Replicate replicate = optional.get();
        assertThat(replicate.getCurrentStatus()).isEqualTo(ReplicateStatus.CREATED);
        assertThat(replicate.getWalletAddress()).isEqualTo(walletAddress);
    }
}
