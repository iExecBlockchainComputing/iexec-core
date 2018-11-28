package com.iexec.core.task;

import com.iexec.common.chain.ChainContribution;
import com.iexec.common.chain.ChainContributionStatus;
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

import java.util.*;

import static com.iexec.core.task.TaskStatus.*;
import static com.iexec.core.utils.DateTimeUtils.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class TaskServiceTests {

    private final static String WALLET_WORKER_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private final static String WALLET_WORKER_2 = "0x2ab2674aa374fe6415d11f0a8fcbd8027fc1e6a9";
    private final static String WALLET_WORKER_3 = "0x3a3406e69adf886c442ff1791cbf67cea679275d";
    private final static String WALLET_WORKER_4 = "0x4aef50214110fdad4e8b9128347f2ba1ec72f614";

    private final static String CHAIN_TASK_ID = "chainTaskId";

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
        String chainTaskId = "chainTaskId";
        Task task = Task.builder()
                .id("realId")
                .chainTaskId(chainTaskId)
                .currentStatus(TaskStatus.TRANSACTION_INITIALIZE_COMPLETED)
                .commandLine("commandLine")
                .trust(2)
                .build();
        when(taskRepository.findByChainTaskId(chainTaskId)).thenReturn(Optional.of(task));
        Optional<Task> optional = taskService.getTaskByChainTaskId(chainTaskId);
        assertThat(optional.isPresent()).isTrue();
        assertThat(optional.get()).isEqualTo(task);
    }

    @Test
    public void shouldAddTask() {
        String chainDealId = "chainDealId";
        Task task = Task.builder()
                .id("realId")
                .currentStatus(TaskStatus.TRANSACTION_INITIALIZE_COMPLETED)
                .chainDealId(chainDealId)
                .taskIndex(0)
                .dappName("dappName")
                .commandLine("commandLine")
                .trust(2)
                .build();
        when(taskRepository.save(any())).thenReturn(task);
        Task saved = taskService.addTask("chainDealId", 0,"dappName", "commandLine", 2);
        assertThat(saved).isNotNull();
        assertThat(saved).isEqualTo(task);
    }


    //@Test
    public void shouldUpdateReplicateStatusFromComputedToContributed() {//CONTRIBUTED on-chain
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("0x1", "chainTaskId"));

        replicates.get(0).updateStatus(ReplicateStatus.COMPUTED);

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.COMPUTED));

        Task task = Task.builder()
                .id("taskId")
                .chainTaskId("chainTaskId")
                .currentStatus(TaskStatus.COMPUTED)
                .commandLine("ls")
                .trust(2)
                .dateStatusList(dateStatusList)
                .build();

        when(taskRepository.findByChainTaskId("chainTaskId")).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        ChainContribution onChainContribution = new ChainContribution();
        onChainContribution.setStatus(ChainContributionStatus.CONTRIBUTED);
        when(iexecHubService.getContribution("chainTaskId", "0x1")).thenReturn(onChainContribution);

        // taskService.updateReplicateStatus("chainTaskId", "0x1", ReplicateStatus.CONTRIBUTED);

        // Optional<Replicate> replicate = task.getReplicate("0x1");
        // assertThat(replicate.isPresent()).isTrue();
        // assertThat(replicate.get().getCurrentStatus()).isEqualTo(ReplicateStatus.CONTRIBUTED);
    }

    @Test
    public void shouldNotUpdateReplicateStatusFromComputedToContributed() {//REJECTED on-chain
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("0x1", "chainTaskId"));

        replicates.get(0).updateStatus(ReplicateStatus.COMPUTED);

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.COMPUTED));

        Task task = Task.builder()
                .id("taskId")
                .chainTaskId("chainTaskId")
                .currentStatus(TaskStatus.COMPUTED)
                .commandLine("ls")
                .trust(2)
                .dateStatusList(dateStatusList)
                .build();

        when(taskRepository.findByChainTaskId("chainTaskId")).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        ChainContribution onChainContribution = new ChainContribution();
        onChainContribution.setStatus(ChainContributionStatus.REJECTED);
        when(iexecHubService.getContribution("chainTaskId", "0x1")).thenReturn(onChainContribution);

        // taskService.updateReplicateStatus("chainTaskId", "0x1", ReplicateStatus.CONTRIBUTED);

        // Optional<Replicate> replicate = task.getReplicate("0x1");
        // assertThat(replicate.isPresent()).isTrue();
        // assertThat(replicate.get().getCurrentStatus()).isEqualTo(ReplicateStatus.COMPUTED);
    }

    @Test
    public void shouldWaitUpdateReplicateStatusFromUnsetToContributed() throws InterruptedException {//UNSET on-chain
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("0x1", "chainTaskId"));

        replicates.get(0).updateStatus(ReplicateStatus.COMPUTED);

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.COMPUTED));

        Task task = Task.builder()
                .id("taskId")
                .chainTaskId("chainTaskId")
                .currentStatus(TaskStatus.COMPUTED)
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

        Task task = new Task("dappName", "commandLine", 3, CHAIN_TASK_ID);
        task.changeStatus(TRANSACTION_INITIALIZE_COMPLETED);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED)).thenReturn(3);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(0);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    // 2 replicates in RUNNING and and 2 in COMPUTED
    @Test
    public void shouldUpdateTaskToRunningFromWorkersInRunningAndComputed() {
        Task task = new Task("dappName", "commandLine", 4, CHAIN_TASK_ID);
        task.changeStatus(TRANSACTION_INITIALIZE_COMPLETED);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED)).thenReturn(4);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(2);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    // all replicates in TRANSACTION_INITIALIZE_COMPLETED
    @Test
    public void shouldNotUpdateToRunningSinceAllReplicatesInCreated() {
        Task task = new Task("dappName", "commandLine", 4, CHAIN_TASK_ID);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED)).thenReturn(0);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(0);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RUNNING);
    }


    // Two replicates in COMPUTED BUT trust = 2, so the task should not be able to move directly from
    // TRANSACTION_INITIALIZE_COMPLETED to COMPUTED
    @Test
    public void shouldNotUpdateToRunningCase2() {

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED)).thenReturn(2);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(2);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RUNNING);
    }

    // @Test
    public void shouldUpdateToComputedAndResultRequest() {
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("worker1", "chainTaskId"));
        replicates.add(new Replicate("worker2", "chainTaskId"));
        replicates.add(new Replicate("worker3", "chainTaskId"));
        replicates.get(1).updateStatus(ReplicateStatus.COMPUTED);
        replicates.get(2).updateStatus(ReplicateStatus.COMPUTED);

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.TRANSACTION_INITIALIZE_COMPLETED));

        Task task = Task.builder()
                .id("taskId")
                .currentStatus(TaskStatus.TRANSACTION_INITIALIZE_COMPLETED)
                .commandLine("ls")
                .trust(2)
                .dateStatusList(dateStatusList)
                .build();
        task.changeStatus(TaskStatus.RUNNING);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryToMoveTaskToNextStatus(task);
        TaskStatus lastButOneStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus();
        assertThat(lastButOneStatus).isEqualTo(TaskStatus.COMPUTED);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.UPLOAD_RESULT_REQUESTED);
    }

    // not enough COMPUTED replicates
    //@Test
    public void shouldNotUpdateToComputedAndResultRequest() {
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("worker1", "chainTaskId"));
        replicates.add(new Replicate("worker2", "chainTaskId"));
        replicates.add(new Replicate("worker3", "chainTaskId"));
        replicates.get(1).updateStatus(ReplicateStatus.COMPUTED);
        replicates.get(2).updateStatus(ReplicateStatus.RUNNING);

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.TRANSACTION_INITIALIZE_COMPLETED));

        Task task = Task.builder()
                .id("taskId")
                .currentStatus(TaskStatus.TRANSACTION_INITIALIZE_COMPLETED)
                .commandLine("ls")
                .trust(2)
                .dateStatusList(dateStatusList)
                .build();
        task.changeStatus(TaskStatus.RUNNING);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.UPLOAD_RESULT_REQUESTED);
    }

    @Test
    public void shouldUpdateFromUploadRequestedToUploadingResult() {//one worker is uploading
        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.setCurrentStatus(UPLOAD_RESULT_REQUESTED);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.UPLOADING_RESULT)).thenReturn(1);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.UPLOADING_RESULT);
    }

    @Test
    public void shouldNotUpdateToUploadingResult() { //no worker is uploading
        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(UPLOAD_RESULT_REQUESTED);
        when(replicatesService.getNbReplicatesWithStatus(CHAIN_TASK_ID, ReplicateStatus.UPLOADING_RESULT)).thenReturn(0);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.UPLOAD_RESULT_REQUESTED);
        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.UPLOAD_RESULT_REQUESTED);
    }

    //@Test
    public void shouldUpdateToResultUploadedThenCompleted() { //one worker uploaded
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("worker1", "chainTaskId"));
        replicates.add(new Replicate("worker2", "chainTaskId"));
        replicates.add(new Replicate("worker3", "chainTaskId"));
        replicates.get(1).updateStatus(ReplicateStatus.COMPUTED);
        replicates.get(2).updateStatus(ReplicateStatus.RESULT_UPLOADED);

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.TRANSACTION_INITIALIZE_COMPLETED));

        Task task = Task.builder()
                .id("taskId")
                .chainTaskId("chainTaskId")
                .commandLine("ls")
                .trust(2)
                .dateStatusList(dateStatusList)
                .build();
        task.changeStatus(TaskStatus.UPLOADING_RESULT);

        taskService.tryToMoveTaskToNextStatus(task);
        TaskStatus lastButOneStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus();
        assertThat(lastButOneStatus).isEqualTo(TaskStatus.RESULT_UPLOADED);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    // at least one UPLOADED
    @Test
    public void shouldUpdateFromUploadingResultToResultUploaded() {
        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.RUNNING);
        task.changeStatus(TaskStatus.UPLOAD_RESULT_REQUESTED);
        task.changeStatus(TaskStatus.UPLOADING_RESULT);

        when(replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.RESULT_UPLOADED)).thenReturn(1);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RESULT_UPLOADED);
    }

    // No worker in UPLOADED
    @Test
    public void shouldNotUpdateToResultUploaded() {
        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.RUNNING);
        task.changeStatus(TaskStatus.UPLOAD_RESULT_REQUESTED);
        task.changeStatus(TaskStatus.UPLOADING_RESULT);

        when(replicatesService.getNbReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.RESULT_UPLOADED)).thenReturn(0);

        taskService.tryToMoveTaskToNextStatus(task);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RESULT_UPLOADED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.COMPLETED);
    }

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
        when(taskRepository.findByCurrentStatus(Arrays.asList(TRANSACTION_INITIALIZE_COMPLETED, RUNNING)))
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

        Task runningTask1 = new Task("dappName", "command", 3);
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
        when(taskRepository.findByCurrentStatus(Arrays.asList(TRANSACTION_INITIALIZE_COMPLETED, RUNNING)))
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

        Task runningTask1 = new Task("dappName", "command", 3);
        runningTask1.setId("task1");
        runningTask1.changeStatus(RUNNING);
        // runningTask1.setReplicates(listReplicates1);

        when(workerService.getWorker(workerName)).thenReturn(Optional.of(existingWorker));
        when(taskRepository.findByCurrentStatus(Arrays.asList(TRANSACTION_INITIALIZE_COMPLETED, RUNNING)))
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

        Task runningTask1 = new Task("dappName", "command", 1);
        runningTask1.setId(taskId);
        runningTask1.changeStatus(RUNNING);

        when(workerService.getWorker(workerName)).thenReturn(Optional.of(existingWorker));
        when(taskRepository.findByCurrentStatus(Arrays.asList(TRANSACTION_INITIALIZE_COMPLETED, RUNNING)))
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

        Task runningTask1 = new Task("dappName", "command", 3);
        runningTask1.setId(taskId);
        runningTask1.changeStatus(RUNNING);
        // runningTask1.setReplicates(listReplicates1);

        when(workerService.getWorker(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(taskRepository.findByCurrentStatus(Arrays.asList(TRANSACTION_INITIALIZE_COMPLETED, RUNNING)))
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
