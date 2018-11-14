package com.iexec.core.task;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.pubsub.NotificationService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static com.iexec.core.task.TaskStatus.CREATED;
import static com.iexec.core.task.TaskStatus.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class TaskServiceTests {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private WorkerService workerService;

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
                .currentStatus(TaskStatus.CREATED)
                .commandLine("commandLine")
                .nbContributionNeeded(2)
                .build();
        when(taskRepository.findByChainTaskId(chainTaskId)).thenReturn(Optional.of(task));
        Optional<Task> optional = taskService.getTaskByChainTaskId(chainTaskId);
        assertThat(optional.isPresent()).isTrue();
        assertThat(optional.get()).isEqualTo(task);
    }

    @Test
    public void shouldAddTask() {
        String chainTaskId = "123";
        Task task = Task.builder()
                .id("realId")
                .chainTaskId("chainTaskId")
                .currentStatus(TaskStatus.CREATED)
                .chainTaskId(chainTaskId)
                .dappName("dappName")
                .commandLine("commandLine")
                .nbContributionNeeded(2)
                .build();
        when(taskRepository.save(any())).thenReturn(task);
        Task saved = taskService.addTask("dappName", "commandLine", 2, chainTaskId);
        assertThat(saved).isNotNull();
        assertThat(saved).isEqualTo(task);
    }

    @Test
    public void shouldUpdateReplicateStatus() {
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("worker1", "chainTaskId"));

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.CREATED));

        Task task = Task.builder()
                .id("taskId")
                .chainTaskId("chainTaskId")
                .currentStatus(TaskStatus.CREATED)
                .commandLine("ls")
                .nbContributionNeeded(1)
                .replicates(replicates)
                .dateStatusList(dateStatusList)
                .build();

        when(taskRepository.findByChainTaskId("chainTaskId")).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);
        Optional<Replicate> updated = taskService.updateReplicateStatus("chainTaskId", "worker1", ReplicateStatus.RUNNING);
        assertThat(updated.isPresent()).isTrue();
        assertEquals(2, updated.get().getStatusChangeList().size());
        assertThat(updated.get().getStatusChangeList().get(0).getStatus()).isEqualTo(ReplicateStatus.CREATED);
        assertThat(updated.get().getStatusChangeList().get(1).getStatus()).isEqualTo(ReplicateStatus.RUNNING);
    }

    // some replicates in RUNNING
    @Test
    public void shouldUpdateToRunningCase1() {
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("worker1", "chainTaskId"));
        replicates.add(new Replicate("worker2", "chainTaskId"));
        replicates.add(new Replicate("worker3", "chainTaskId"));
        replicates.get(1).updateStatus(ReplicateStatus.RUNNING);
        replicates.get(2).updateStatus(ReplicateStatus.RUNNING);

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.CREATED));

        Task task = Task.builder()
                .id("taskId")
                .currentStatus(TaskStatus.CREATED)
                .commandLine("ls")
                .nbContributionNeeded(2)
                .replicates(replicates)
                .dateStatusList(dateStatusList)
                .build();

        taskService.tryUpdateToRunning(task);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    // some replicates in RUNNING and COMPUTED
    @Test
    public void shouldUpdateToRunningCase2() {
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("worker1", "chainTaskId"));
        replicates.add(new Replicate("worker2", "chainTaskId"));
        replicates.add(new Replicate("worker3", "chainTaskId"));
        replicates.get(1).updateStatus(ReplicateStatus.COMPUTED);
        replicates.get(2).updateStatus(ReplicateStatus.RUNNING);

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.CREATED));

        Task task = Task.builder()
                .id("taskId")
                .currentStatus(TaskStatus.CREATED)
                .commandLine("ls")
                .nbContributionNeeded(2)
                .replicates(replicates)
                .dateStatusList(dateStatusList)
                .build();

        taskService.tryUpdateToRunning(task);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    // all replicates in CREATED
    @Test
    public void shouldNotUpdateToRunningCase1() {
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("worker1", "chainTaskId"));
        replicates.add(new Replicate("worker2", "chainTaskId"));
        replicates.add(new Replicate("worker3", "chainTaskId"));

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.CREATED));

        Task task = Task.builder()
                .id("taskId")
                .chainTaskId("chainTaskId")
                .currentStatus(TaskStatus.CREATED)
                .commandLine("ls")
                .nbContributionNeeded(2)
                .replicates(replicates)
                .dateStatusList(dateStatusList)
                .build();

        taskService.tryUpdateToRunning(task);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RUNNING);
    }


    // Two replicates in COMPUTED BUT nbContributionNeeded = 2, so the task should not be able to move directly from
    // CREATED to COMPUTED
    @Test
    public void shouldNotUpdateToRunningCase2() {
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("worker1", "chainTaskId"));
        replicates.add(new Replicate("worker2", "chainTaskId"));
        replicates.add(new Replicate("worker3", "chainTaskId"));
        replicates.get(1).updateStatus(ReplicateStatus.COMPUTED);
        replicates.get(2).updateStatus(ReplicateStatus.COMPUTED);

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.CREATED));

        Task task = Task.builder()
                .id("taskId")
                .currentStatus(TaskStatus.CREATED)
                .commandLine("ls")
                .nbContributionNeeded(2)
                .replicates(replicates)
                .dateStatusList(dateStatusList)
                .build();

        taskService.tryUpdateToRunning(task);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RUNNING);
    }

    @Test
    public void shouldUpdateToComputedAndResultRequest() {
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("worker1", "chainTaskId"));
        replicates.add(new Replicate("worker2", "chainTaskId"));
        replicates.add(new Replicate("worker3", "chainTaskId"));
        replicates.get(1).updateStatus(ReplicateStatus.COMPUTED);
        replicates.get(2).updateStatus(ReplicateStatus.COMPUTED);

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.CREATED));

        Task task = Task.builder()
                .id("taskId")
                .currentStatus(TaskStatus.CREATED)
                .commandLine("ls")
                .nbContributionNeeded(2)
                .replicates(replicates)
                .dateStatusList(dateStatusList)
                .build();
        task.changeStatus(TaskStatus.RUNNING);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryUpdateToComputedAndResultRequest(task);
        TaskStatus lastButOneStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus();
        assertThat(lastButOneStatus).isEqualTo(TaskStatus.COMPUTED);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.UPLOAD_RESULT_REQUESTED);
    }

    // not enough COMPUTED replicates
    @Test
    public void shouldNotUpdateToComputedAndResultRequest() {
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("worker1", "chainTaskId"));
        replicates.add(new Replicate("worker2", "chainTaskId"));
        replicates.add(new Replicate("worker3", "chainTaskId"));
        replicates.get(1).updateStatus(ReplicateStatus.COMPUTED);
        replicates.get(2).updateStatus(ReplicateStatus.RUNNING);

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.CREATED));

        Task task = Task.builder()
                .id("taskId")
                .currentStatus(TaskStatus.CREATED)
                .commandLine("ls")
                .nbContributionNeeded(2)
                .replicates(replicates)
                .dateStatusList(dateStatusList)
                .build();
        task.changeStatus(TaskStatus.RUNNING);
        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryUpdateToComputedAndResultRequest(task);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.UPLOAD_RESULT_REQUESTED);
    }

    // at least one UPLOADED
    @Test
    public void shouldUpdateToUploadingResult() {
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("worker1", "chainTaskId"));
        replicates.add(new Replicate("worker2", "chainTaskId"));
        replicates.add(new Replicate("worker3", "chainTaskId"));
        replicates.get(1).updateStatus(ReplicateStatus.COMPUTED);
        replicates.get(2).updateStatus(ReplicateStatus.RESULT_UPLOADED);

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.CREATED));

        Task task = Task.builder()
                .id("taskId")
                .currentStatus(TaskStatus.CREATED)
                .commandLine("ls")
                .nbContributionNeeded(2)
                .replicates(replicates)
                .dateStatusList(dateStatusList)
                .build();
        task.changeStatus(TaskStatus.RUNNING);
        task.changeStatus(TaskStatus.UPLOAD_RESULT_REQUESTED);
        task.changeStatus(TaskStatus.UPLOADING_RESULT);
        task.changeStatus(TaskStatus.RESULT_UPLOADED);
        task.changeStatus(TaskStatus.COMPLETED);

        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryUpdateToResultUploaded(task);
        TaskStatus lastButOneStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus();
        assertThat(lastButOneStatus).isEqualTo(TaskStatus.RESULT_UPLOADED);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    public void shouldNotUpdateToResultUploaded() {
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("worker1", "chainTaskId"));
        replicates.add(new Replicate("worker2", "chainTaskId"));
        replicates.add(new Replicate("worker3", "chainTaskId"));
        replicates.get(1).updateStatus(ReplicateStatus.COMPUTED);
        replicates.get(2).updateStatus(ReplicateStatus.COMPUTED);

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.CREATED));

        Task task = Task.builder()
                .id("taskId")
                .currentStatus(TaskStatus.CREATED)
                .commandLine("ls")
                .nbContributionNeeded(2)
                .replicates(replicates)
                .dateStatusList(dateStatusList)
                .build();
        task.changeStatus(TaskStatus.RUNNING);
        task.changeStatus(TaskStatus.COMPLETED);
        task.changeStatus(TaskStatus.UPLOAD_RESULT_REQUESTED);

        when(taskRepository.save(task)).thenReturn(task);

        taskService.tryUpdateToResultUploaded(task);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RESULT_UPLOADED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    public void shouldNotGetAnyReplicateSinceWorkerDoesntExist(){
        when(workerService.getWorker(Mockito.anyString())).thenReturn(Optional.empty());

        Optional<Replicate> optional = taskService.getAvailableReplicate("worker1");
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    public void shouldNotGetReplicateSinceNoRunningTask(){
        String workerName = "worker1";

        Worker existingWorker = Worker.builder()
                .id("1")
                .name(workerName)
                .cpuNb(1)
                .lastAliveDate(new Date())
                .build();

        when(workerService.getWorker(Mockito.anyString())).thenReturn(Optional.of(existingWorker));
        when(taskRepository.findByCurrentStatus(Arrays.asList(CREATED, RUNNING)))
                .thenReturn(new ArrayList<>());

        Optional<Replicate> optional = taskService.getAvailableReplicate(workerName);
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    public void shouldNotGetAnyReplicateSinceWorkerIsFull(){
        String workerName = "worker1";

        Worker existingWorker = Worker.builder()
                .id("1")
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
        runningTask1.setReplicates(listReplicates1);

        List<Replicate> listReplicates2 = new ArrayList<>();
        listReplicates2.add(new Replicate("worker2", "chainTaskId2"));
        listReplicates2.get(0).updateStatus(ReplicateStatus.RUNNING);

        Task runningTask2 = new Task("dappName2", "command", 3);
        runningTask2.setId("task2");
        runningTask2.changeStatus(RUNNING);
        runningTask2.setReplicates(listReplicates2);

        when(workerService.getWorker(workerName)).thenReturn(Optional.of(existingWorker));
        when(taskRepository.findByCurrentStatus(Arrays.asList(CREATED, RUNNING)))
                .thenReturn(Arrays.asList(runningTask1, runningTask2));

        Optional<Replicate> optional = taskService.getAvailableReplicate(workerName);
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    public void shouldNotGetAnyReplicateSinceWorkerAlreadyContributed(){
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
        runningTask1.setReplicates(listReplicates1);

        when(workerService.getWorker(workerName)).thenReturn(Optional.of(existingWorker));
        when(taskRepository.findByCurrentStatus(Arrays.asList(CREATED, RUNNING)))
                .thenReturn(Collections.singletonList(runningTask1));

        Optional<Replicate> optional = taskService.getAvailableReplicate(workerName);
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    public void shouldNotGetReplicateSinceTaskDoesntNeedMoreReplicate(){
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
        runningTask1.setReplicates(listReplicates1);

        when(workerService.getWorker(workerName)).thenReturn(Optional.of(existingWorker));
        when(taskRepository.findByCurrentStatus(Arrays.asList(CREATED, RUNNING)))
                .thenReturn(Collections.singletonList(runningTask1));
        when(taskRepository.save(any())).thenReturn(runningTask1);
        when(workerService.addTaskIdToWorker(taskId, workerName)).thenReturn(Optional.of(existingWorker));

        Optional<Replicate> optional = taskService.getAvailableReplicate(workerName);
        assertThat(optional.isPresent()).isFalse();
    }

    @Test
    public void shouldGetAReplicate(){
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
        runningTask1.setReplicates(listReplicates1);

        when(workerService.getWorker(walletAddress)).thenReturn(Optional.of(existingWorker));
        when(taskRepository.findByCurrentStatus(Arrays.asList(CREATED, RUNNING)))
                .thenReturn(Collections.singletonList(runningTask1));
        when(taskRepository.save(any())).thenReturn(runningTask1);
        when(workerService.addTaskIdToWorker(taskId, walletAddress)).thenReturn(Optional.of(existingWorker));

        Optional<Replicate> optional = taskService.getAvailableReplicate(walletAddress);
        assertThat(optional.isPresent()).isTrue();
        Replicate replicate = optional.get();
        assertThat(replicate.getCurrentStatus()).isEqualTo(ReplicateStatus.CREATED);
        assertThat(replicate.getWalletAddress()).isEqualTo(walletAddress);
    }
}
