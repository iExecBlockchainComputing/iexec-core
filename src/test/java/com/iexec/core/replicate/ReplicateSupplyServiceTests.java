/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.core.replicate;

import com.iexec.common.replicate.*;
import com.iexec.common.utils.DateTimeUtils;
import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.notification.TaskNotification;
import com.iexec.commons.poco.notification.TaskNotificationExtra;
import com.iexec.commons.poco.notification.TaskNotificationType;
import com.iexec.commons.poco.task.TaskAbortCause;
import com.iexec.commons.poco.tee.TeeUtils;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.core.chain.SignatureService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.core.task.TaskStatus.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;


class ReplicateSupplyServiceTests {

    private final static String WALLET_WORKER_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private final static String WALLET_WORKER_2 = "0xdcfeffee1443fbf9277e6fa3b50cf3b38f7101af";

    private final static String CHAIN_TASK_ID = "0x65bc5e94ed1486b940bd6cc0013c418efad58a0a52a3d08cee89faaa21970426";
    private final static String CHAIN_TASK_ID_2 = "0xc536af16737e02bb28100452a932056d499be3c462619751a9ed36515de64d50";

    private final static String DAPP_NAME = "dappName";
    private final static String COMMAND_LINE = "commandLine";
    private final static String NO_TEE_TAG = BytesUtils.EMPTY_HEX_STRING_32;
    private final static String TEE_TAG = TeeUtils.TEE_SCONE_ONLY_TAG; //any supported TEE tag
    private final static String ENCLAVE_CHALLENGE = "dummyEnclave";
    private final static long maxExecutionTime = 60000;
    long workerLastBlock = 12;

    @Mock
    private ReplicatesService replicatesService;
    @Mock
    private SignatureService signatureService;
    @Mock
    private TaskService taskService;
    @Mock
    private TaskUpdateRequestManager taskUpdateRequestManager;
    @Mock
    private WorkerService workerService;
    @Mock
    private Web3jService web3jService;

    @Spy
    @InjectMocks
    private ReplicateSupplyService replicateSupplyService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    void workerCanWorkAndHasGas(String workerAddress) {
        when(workerService.canAcceptMoreWorks(workerAddress)).thenReturn(true);
        when(web3jService.hasEnoughGas(workerAddress)).thenReturn(true);
    }

    // Tests on getAuthOfAvailableReplicate()

    // If worker does not exist, canAcceptMoreWorks return false
    // It is not possible in the current implementation to test workerService.getWorker with an empty Optional
    // in getAuthOfAvailableReplicate method
    @Test
    void shouldNotGetAnyReplicateSinceWorkerDoesNotExist() {
        when(workerService.getWorker(Mockito.anyString())).thenReturn(Optional.empty());
        Optional<ReplicateTaskSummary> replicateTaskSummary =
                replicateSupplyService.getAvailableReplicateTaskSummary(workerLastBlock, WALLET_WORKER_1);
        assertThat(replicateTaskSummary).isEmpty();
        Mockito.verifyNoInteractions(web3jService, taskService, taskUpdateRequestManager, replicatesService, signatureService);
    }

    @Test
    void shouldNotGetReplicateSinceWorkerLastBlockNotAvailable() {
        workerCanWorkAndHasGas(WALLET_WORKER_1);
        Optional<ReplicateTaskSummary> replicateTaskSummary =
                replicateSupplyService.getAvailableReplicateTaskSummary(0, WALLET_WORKER_1);
        assertThat(replicateTaskSummary).isEmpty();
        Mockito.verifyNoInteractions(web3jService, taskService, taskUpdateRequestManager, replicatesService, signatureService);
    }

    @Test
    void shouldNotGetReplicateSinceNoRunningTask() {
        workerCanWorkAndHasGas(WALLET_WORKER_1);
        when(taskService.getPrioritizedInitializedOrRunningTask(false, Collections.emptyList())).thenReturn(Optional.empty());
        Optional<ReplicateTaskSummary> replicateTaskSummary =
                replicateSupplyService.getAvailableReplicateTaskSummary(workerLastBlock, WALLET_WORKER_1);
        assertThat(replicateTaskSummary).isEmpty();
        Mockito.verify(taskService, Mockito.never()).getTaskByChainTaskId(CHAIN_TASK_ID);
        Mockito.verifyNoInteractions(taskUpdateRequestManager, replicatesService, signatureService);
    }

    @Test
    void shouldNotGetReplicateSinceNoReplicatesList() {
        Worker worker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_2)
                .cpuNb(4)
                .teeEnabled(false)
                .lastAliveDate(new Date())
                .build();

        Task runningTask = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        runningTask.setMaxExecutionTime(maxExecutionTime);
        runningTask.changeStatus(RUNNING);
        runningTask.setTag(NO_TEE_TAG);
        runningTask.setContributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), 60));
        runningTask.setEnclaveChallenge(BytesUtils.EMPTY_ADDRESS);

        workerCanWorkAndHasGas(WALLET_WORKER_2);
        when(taskService.getPrioritizedInitializedOrRunningTask(true, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_2)).thenReturn(Optional.of(worker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        Optional<ReplicateTaskSummary> replicateTaskSummary =
                replicateSupplyService.getAvailableReplicateTaskSummary(workerLastBlock, WALLET_WORKER_2);
        assertThat(replicateTaskSummary).isEmpty();
        Mockito.verify(taskService, Mockito.never()).isConsensusReached(any());
        Mockito.verifyNoInteractions(signatureService);
        assertTaskAccessForNewReplicateLockNeverUsed(CHAIN_TASK_ID);
    }

    @Test
    void shouldNotGetReplicateSinceConsensusReachedOnChain() {
        Worker worker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_2)
                .cpuNb(4)
                .teeEnabled(false)
                .lastAliveDate(new Date())
                .build();
        final Replicate replicate = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);
        List<Replicate> replicates = List.of(replicate);
        ReplicatesList replicatesList = Mockito.spy(
                new ReplicatesList(CHAIN_TASK_ID, replicates)
        );

        Task runningTask = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        runningTask.setMaxExecutionTime(maxExecutionTime);
        runningTask.changeStatus(RUNNING);
        runningTask.setTag(NO_TEE_TAG);
        runningTask.setContributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), 60));
        runningTask.setEnclaveChallenge(BytesUtils.EMPTY_ADDRESS);

        workerCanWorkAndHasGas(WALLET_WORKER_2);
        when(taskService.getPrioritizedInitializedOrRunningTask(true, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_2)).thenReturn(Optional.of(worker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(taskService.isConsensusReached(replicatesList)).thenReturn(true);
        when(replicatesList.hasWorkerAlreadyParticipated(WALLET_WORKER_2)).thenReturn(false);

        Optional<ReplicateTaskSummary> replicateTaskSummary =
                replicateSupplyService.getAvailableReplicateTaskSummary(workerLastBlock, WALLET_WORKER_2);

        assertThat(replicateTaskSummary).isEmpty();

        Mockito.verify(taskService).isConsensusReached(replicatesList);
        Mockito.verifyNoInteractions(signatureService);
        assertTaskAccessForNewReplicateNotDeadLocking(CHAIN_TASK_ID);
    }

    @Test
    void shouldNotGetAnyReplicateSinceWorkerIsFull() {
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(false);
        Optional<ReplicateTaskSummary> replicateTaskSummary =
                replicateSupplyService.getAvailableReplicateTaskSummary(workerLastBlock, WALLET_WORKER_1);
        assertThat(replicateTaskSummary).isEmpty();
        Mockito.verifyNoInteractions(taskService, taskUpdateRequestManager, replicatesService, signatureService);
    }

    @Test
    void shouldNotGetAnyReplicateSinceWorkerDoesNotHaveEnoughGas() {
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(web3jService.hasEnoughGas(WALLET_WORKER_1)).thenReturn(false);
        Optional<ReplicateTaskSummary> replicateTaskSummary =
                replicateSupplyService.getAvailableReplicateTaskSummary(workerLastBlock, WALLET_WORKER_1);
        assertThat(replicateTaskSummary).isEmpty();
        Mockito.verify(web3jService).hasEnoughGas(WALLET_WORKER_1);
        Mockito.verifyNoInteractions(taskService, taskUpdateRequestManager, replicatesService, signatureService);
    }

    @Test
    void shouldNotGetAnyReplicateSinceWorkerAlreadyParticipated() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(2)
                .lastAliveDate(new Date())
                .build();

        Task runningTask = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        runningTask.setMaxExecutionTime(maxExecutionTime);
        runningTask.changeStatus(RUNNING);
        runningTask.setTag(NO_TEE_TAG);
        runningTask.setContributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), 60));
        runningTask.setEnclaveChallenge(BytesUtils.EMPTY_ADDRESS);

        final ReplicatesList replicatesList = Mockito.spy(new ReplicatesList(
                CHAIN_TASK_ID,
                Collections.singletonList(new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID))
        ));

        workerCanWorkAndHasGas(WALLET_WORKER_1);
        when(taskService.getPrioritizedInitializedOrRunningTask(true, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        when(replicatesList.hasWorkerAlreadyParticipated(WALLET_WORKER_1)).thenReturn(true);
        Optional<ReplicateTaskSummary> replicateTaskSummary =
                replicateSupplyService.getAvailableReplicateTaskSummary(workerLastBlock, WALLET_WORKER_1);

        assertThat(replicateTaskSummary).isEmpty();

        Mockito.verifyNoInteractions(signatureService);
        assertTaskAccessForNewReplicateLockNeverUsed(CHAIN_TASK_ID);
    }

    @Test
    void shouldNotGetReplicateSinceDoesNotNeedMoreContributionsForConsensus() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_2)
                .cpuNb(2)
                .lastAliveDate(new Date())
                .build();

        int trust = 5;
        Task runningTask = new Task(DAPP_NAME, COMMAND_LINE, trust, CHAIN_TASK_ID);
        runningTask.changeStatus(RUNNING);
        runningTask.setMaxExecutionTime(maxExecutionTime);
        runningTask.setTag(NO_TEE_TAG);
        runningTask.setContributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), 60));
        runningTask.setEnclaveChallenge(BytesUtils.EMPTY_ADDRESS);

        // Replicate already scheduled and contributed on worker1
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate.setWorkerWeight(trust);
        replicate.setContributionHash("test");

        final ReplicatesList replicatesList = Mockito.spy(
                new ReplicatesList(CHAIN_TASK_ID, List.of(replicate))
        );

        // Try to see if a replicate of the task can be scheduled on worker2
        workerCanWorkAndHasGas(WALLET_WORKER_2);
        when(taskService.getPrioritizedInitializedOrRunningTask(true, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_2)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(replicatesList.hasWorkerAlreadyParticipated(WALLET_WORKER_1)).thenReturn(false);
        Optional<ReplicateTaskSummary> replicateTaskSummary =
                replicateSupplyService.getAvailableReplicateTaskSummary(workerLastBlock, WALLET_WORKER_2);
        assertThat(replicateTaskSummary).isEmpty();

        Mockito.verifyNoInteractions(signatureService);
        assertTaskAccessForNewReplicateNotDeadLocking(CHAIN_TASK_ID);
    }

    @Test
    void shouldNotGetReplicateSinceEnclaveChallengeNeededButNotGenerated() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(2)
                .teeEnabled(true)
                .lastAliveDate(new Date())
                .build();

        Task runningTask = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        runningTask.setMaxExecutionTime(maxExecutionTime);
        runningTask.changeStatus(RUNNING);
        runningTask.setTag(TEE_TAG);
        runningTask.setContributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), 60));
        runningTask.setEnclaveChallenge("");

        final ReplicatesList replicatesList = Mockito.spy(
                new ReplicatesList(CHAIN_TASK_ID, Collections.emptyList())
        );

        workerCanWorkAndHasGas(WALLET_WORKER_1);
        when(taskService.getPrioritizedInitializedOrRunningTask(false, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        when(replicatesList.hasWorkerAlreadyParticipated(WALLET_WORKER_1)).thenReturn(false);
        Optional<ReplicateTaskSummary> replicateTaskSummary =
                replicateSupplyService.getAvailableReplicateTaskSummary(workerLastBlock, WALLET_WORKER_1);

        assertThat(replicateTaskSummary).isEmpty();

        Mockito.verify(replicatesService, Mockito.never()).addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService, Mockito.never()).addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verifyNoInteractions(signatureService);
        assertTaskAccessForNewReplicateLockNeverUsed(CHAIN_TASK_ID);
    }

    @Test
    void shouldGetOnlyOneReplicateSinceOtherOneReachedConsensusDeadline() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(4)
                .teeEnabled(false)
                .lastAliveDate(new Date())
                .build();

        int trust = 5;
        Task task1 = new Task(DAPP_NAME, COMMAND_LINE, trust, CHAIN_TASK_ID);
        task1.setMaxExecutionTime(maxExecutionTime);
        task1.setContributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), 60));
        task1.changeStatus(RUNNING);
        task1.setTag(NO_TEE_TAG);
        task1.setEnclaveChallenge(BytesUtils.EMPTY_ADDRESS);

        Task taskDeadlineReached = new Task(DAPP_NAME, COMMAND_LINE, trust, CHAIN_TASK_ID_2);
        taskDeadlineReached.setMaxExecutionTime(maxExecutionTime);
        taskDeadlineReached.setContributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), -60));
        taskDeadlineReached.changeStatus(RUNNING);
        taskDeadlineReached.setTag(NO_TEE_TAG);
        taskDeadlineReached.setEnclaveChallenge(BytesUtils.EMPTY_ADDRESS);

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.emptyList());

        workerCanWorkAndHasGas(WALLET_WORKER_1);
        when(taskService.getPrioritizedInitializedOrRunningTask(true, List.of(CHAIN_TASK_ID)))
                .thenReturn(Optional.of(taskDeadlineReached));
        when(taskService.getPrioritizedInitializedOrRunningTask(true, Collections.emptyList()))
                .thenReturn(Optional.of(task1));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, BytesUtils.EMPTY_ADDRESS))
                .thenReturn(WorkerpoolAuthorization.builder().chainTaskId(CHAIN_TASK_ID).build());

        final Optional<ReplicateTaskSummary> replicateTaskSummary = replicateSupplyService.getAvailableReplicateTaskSummary(workerLastBlock, WALLET_WORKER_1);

        assertThat(replicateTaskSummary).isPresent();
        assertThat(replicateTaskSummary.get().getWorkerpoolAuthorization().getChainTaskId()).isEqualTo(CHAIN_TASK_ID);

        Mockito.verify(replicatesService).addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService).addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(signatureService, times(0)).createAuthorization(any(), eq(CHAIN_TASK_ID_2), any());
        assertTaskAccessForNewReplicateNotDeadLocking(CHAIN_TASK_ID);
    }

    @Test
    void shouldNotGetReplicateWhenTaskAlreadyAccessed() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(2)
                .teeEnabled(false)
                .lastAliveDate(new Date())
                .build();

        Task runningTask = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        runningTask.setMaxExecutionTime(maxExecutionTime);
        runningTask.changeStatus(RUNNING);
        runningTask.setTag(NO_TEE_TAG);
        runningTask.setContributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), 60));
        runningTask.setEnclaveChallenge(BytesUtils.EMPTY_ADDRESS);

        workerCanWorkAndHasGas(WALLET_WORKER_1);
        when(taskService.getPrioritizedInitializedOrRunningTask(true, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(new ReplicatesList(CHAIN_TASK_ID, Collections.emptyList())));
        final Lock lock = replicateSupplyService.taskAccessForNewReplicateLocks.computeIfAbsent(CHAIN_TASK_ID, k -> new ReentrantLock());
        CompletableFuture.runAsync(lock::lock).join();

        Optional<ReplicateTaskSummary> replicateTaskSummary =
                replicateSupplyService.getAvailableReplicateTaskSummary(workerLastBlock, WALLET_WORKER_1);

        assertThat(replicateTaskSummary).isEmpty();
        Mockito.verifyNoInteractions(signatureService);
    }

    @Test
    void shouldGetReplicateWithNoTee() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(2)
                .teeEnabled(false)
                .lastAliveDate(new Date())
                .build();

        Task runningTask = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        runningTask.setMaxExecutionTime(maxExecutionTime);
        runningTask.changeStatus(RUNNING);
        runningTask.setTag(NO_TEE_TAG);
        runningTask.setContributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), 60));
        runningTask.setEnclaveChallenge(BytesUtils.EMPTY_ADDRESS);

        final ReplicatesList replicatesList = Mockito.spy(
                new ReplicatesList(CHAIN_TASK_ID, Collections.emptyList())
        );

        workerCanWorkAndHasGas(WALLET_WORKER_1);
        when(taskService.getPrioritizedInitializedOrRunningTask(true, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, BytesUtils.EMPTY_ADDRESS))
                .thenReturn(new WorkerpoolAuthorization());
        when(replicatesList.hasWorkerAlreadyParticipated(WALLET_WORKER_1)).thenReturn(false);

        Optional<ReplicateTaskSummary> replicateTaskSummary =
                replicateSupplyService.getAvailableReplicateTaskSummary(workerLastBlock, WALLET_WORKER_1);
        assertThat(replicateTaskSummary).isPresent();

        Mockito.verify(replicatesService).addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService).addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(signatureService).createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, BytesUtils.EMPTY_ADDRESS);
        assertTaskAccessForNewReplicateNotDeadLocking(CHAIN_TASK_ID);
    }

    @Test
    void shouldGetReplicateWithTee() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(2)
                .teeEnabled(true)
                .lastAliveDate(new Date())
                .build();

        Task runningTask = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        runningTask.setMaxExecutionTime(maxExecutionTime);
        runningTask.changeStatus(RUNNING);
        runningTask.setTag(TEE_TAG);
        runningTask.setContributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), 60));
        runningTask.setEnclaveChallenge(ENCLAVE_CHALLENGE);

        final ReplicatesList replicatesList = Mockito.spy(
                new ReplicatesList(CHAIN_TASK_ID, Collections.emptyList())
        );

        workerCanWorkAndHasGas(WALLET_WORKER_1);
        when(taskService.getPrioritizedInitializedOrRunningTask(false, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(new WorkerpoolAuthorization());

        when(replicatesList.hasWorkerAlreadyParticipated(WALLET_WORKER_1)).thenReturn(false);
        Optional<ReplicateTaskSummary> replicateTaskSummary =
                replicateSupplyService.getAvailableReplicateTaskSummary(workerLastBlock, WALLET_WORKER_1);

        assertThat(replicateTaskSummary).isPresent();

        Mockito.verify(replicatesService).addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService).addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
        assertTaskAccessForNewReplicateNotDeadLocking(CHAIN_TASK_ID);
    }

    @Test
    void shouldTeeNeededTaskNotBeGivenToTeeDisabledWorker() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(2)
                .teeEnabled(false)
                .lastAliveDate(new Date())
                .build();

        Task runningTask = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        runningTask.setMaxExecutionTime(maxExecutionTime);
        runningTask.changeStatus(RUNNING);
        runningTask.setTag(TEE_TAG);
        runningTask.setContributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), 60));

        workerCanWorkAndHasGas(WALLET_WORKER_1);
        when(taskService.getPrioritizedInitializedOrRunningTask(true, Collections.emptyList()))
                .thenReturn(Optional.empty());
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));

        Optional<ReplicateTaskSummary> replicateTaskSummary =
                replicateSupplyService.getAvailableReplicateTaskSummary(workerLastBlock, WALLET_WORKER_1);

        assertThat(replicateTaskSummary).isEmpty();
        Mockito.verifyNoInteractions(signatureService);
        assertTaskAccessForNewReplicateLockNeverUsed(CHAIN_TASK_ID);
    }

    @Test
    void shouldTeeNeededTaskBeGivenToTeeEnabledWorker() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(2)
                .teeEnabled(true)
                .lastAliveDate(new Date())
                .build();

        Task runningTask = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        runningTask.setMaxExecutionTime(maxExecutionTime);
        runningTask.changeStatus(RUNNING);
        runningTask.setTag(TEE_TAG);
        runningTask.setContributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), 60));
        runningTask.setEnclaveChallenge(ENCLAVE_CHALLENGE);

        final ReplicatesList replicatesList = Mockito.spy(
                new ReplicatesList(CHAIN_TASK_ID, Collections.emptyList())
        );

        workerCanWorkAndHasGas(WALLET_WORKER_1);
        when(taskService.getPrioritizedInitializedOrRunningTask(false, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(new WorkerpoolAuthorization());

        when(replicatesList.hasWorkerAlreadyParticipated(WALLET_WORKER_1)).thenReturn(false);
        Optional<ReplicateTaskSummary> replicateTaskSummary =
                replicateSupplyService.getAvailableReplicateTaskSummary(workerLastBlock, WALLET_WORKER_1);

        assertThat(replicateTaskSummary).isPresent();

        Mockito.verify(replicatesService).addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService).addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
        assertTaskAccessForNewReplicateNotDeadLocking(CHAIN_TASK_ID);
    }

    /**
     * Checks the lock has been released and can be reused.
     *
     * @param chainTaskId ID of the task whose lock should be checked.
     */
    private void assertTaskAccessForNewReplicateNotDeadLocking(String chainTaskId) {
        final Lock lock = replicateSupplyService.taskAccessForNewReplicateLocks.get(chainTaskId);
        final Boolean successfulLock = CompletableFuture.supplyAsync(() -> {
                    final boolean locked = lock.tryLock();
                    if (!locked) {
                        return false;
                    }
                    lock.unlock();
                    return true;
                })
                .completeOnTimeout(false, 1, TimeUnit.SECONDS)
                .join();
        assertThat(successfulLock).isTrue();
    }

    private void assertTaskAccessForNewReplicateLockNeverUsed(String chainTaskId) {
        final Lock lock = replicateSupplyService.taskAccessForNewReplicateLocks.get(chainTaskId);
        assertThat(lock).isNull();
    }

    // Tests on getMissedTaskNotifications()

    @Test
    void shouldReturnEmptyListSinceNotParticipatingToAnyTask() {

        when(taskService.getTasksByChainTaskIds(any()))
                .thenReturn(Collections.emptyList());

        List<TaskNotification> list =
                replicateSupplyService.getMissedTaskNotifications(1L, WALLET_WORKER_1);

        assertThat(list).isEmpty();
        Mockito.verify(replicatesService, times(0))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }

    @Test
    void shouldNotGetInterruptedReplicateSinceEnclaveChallengeNeededButNotGenerated() {

        List<String> ids = List.of(CHAIN_TASK_ID);
        Task teeTask = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        teeTask.setEnclaveChallenge(ENCLAVE_CHALLENGE);
        Optional<Replicate> noTeeReplicate = getStubReplicate(ReplicateStatus.COMPUTING);
        teeTask.setTag(TEE_TAG);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(List.of(teeTask));
        when(replicatesService.getReplicate(any(), any())).thenReturn(noTeeReplicate);

        List<TaskNotification> taskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3L, WALLET_WORKER_1);

        assertThat(taskNotifications).isEmpty();

        Mockito.verify(replicatesService, times(0))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }

    /**
     * CONTRIBUTING + !onChain => RecoveryAction.CONTRIBUTE
     */
    @Test
    void shouldTellReplicateToContributeSinceNotDoneOnchain() {
        List<String> ids = List.of(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RUNNING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.CONTRIBUTING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        when(replicatesService.didReplicateContributeOnchain(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(false);

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3L, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_CONTRIBUTE);

        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    /**
     * CREATED, ..., CAN_CONTRIBUTE => RecoveryAction.CONTRIBUTE
     */
    @Test
    void shouldTellReplicateToContributeWhenComputing() {
        List<String> ids = List.of(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RUNNING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.COMPUTING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3L, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_CONTRIBUTE);

        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class));
    }

    /**
     * CONTRIBUTING + done onChain   => updateStatus to CONTRIBUTED
     * Task not in CONSENSUS_REACHED => RecoveryAction.WAIT
     */
    @Test
    void shouldTellReplicateToWaitSinceContributedOnchain() {
        long blockNumber = 3;
        // ChainReceipt chainReceipt = new ChainReceipt(blockNumber, "");
        List<String> ids = List.of(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RUNNING);
        Optional<Replicate> replicate1 = getStubReplicate(ReplicateStatus.CONTRIBUTING);
        Optional<Replicate> replicate2 = getStubReplicate(ReplicateStatus.CONTRIBUTED);
        final List<Replicate> replicates = List.of(replicate1.get(), replicate2.get());
        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, replicates);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(replicate1)
                .thenReturn(replicate2);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());
        when(replicatesService.didReplicateContributeOnchain(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(true);
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(taskService.isConsensusReached(replicatesList)).thenReturn(false);

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(blockNumber, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_WAIT);

        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class));
        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatus.class), // CONTRIBUTED
                        any(ReplicateStatusDetails.class));
    }

    /**
     * any status + Task in CONTRIBUTION_TIMEOUT => RecoveryAction.ABORT_CONTRIBUTION_TIMEOUT
     */
    @Test
    void shouldTellReplicateToAbortSinceContributionTimeout() {
        long blockNumber = 3;
        List<String> ids = List.of(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.CONTRIBUTION_TIMEOUT);
        Optional<Replicate> replicate1 = getStubReplicate(ReplicateStatus.CONTRIBUTING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(replicate1);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(blockNumber, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_ABORT);
        TaskNotificationExtra notificationExtra = missedTaskNotifications.get(0).getTaskNotificationExtra();
        assertThat(notificationExtra.getTaskAbortCause()).isEqualTo(TaskAbortCause.CONTRIBUTION_TIMEOUT);

        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    /**
     * CONTRIBUTING + done onChain => updateStatus to CONTRIBUTED
     * Task in CONSENSUS_REACHED   => RecoveryAction.REVEAL
     */
    @Test
    void shouldTellReplicateToRevealSinceConsensusReached() {
        long blockNumber = 3;
        // ChainReceipt chainReceipt = new ChainReceipt(blockNumber, "");
        List<String> ids = List.of(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RUNNING);
        Optional<Replicate> replicate1 = getStubReplicate(ReplicateStatus.CONTRIBUTING);
        Optional<Replicate> replicate2 = getStubReplicate(ReplicateStatus.CONTRIBUTED);
        List<Replicate> replicates = List.of(replicate1.get(), replicate2.get());
        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, replicates);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(replicate1)
                .thenReturn(replicate2);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());
        when(replicatesService.didReplicateContributeOnchain(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(true);
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(taskService.isConsensusReached(replicatesList)).thenReturn(true);

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(blockNumber, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_REVEAL);

        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class));
        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatus.class), // RECOVERING
                        any(ReplicateStatusDetails.class));
    }

    /**
     * !CONTRIBUTED + Task in CONSENSUS_REACHED => RecoveryAction.ABORT_CONSENSUS_REACHED
     */
    @Test
    void shouldTellReplicateToWaitSinceConsensusReachedAndItDidNotContribute() {
        long blockNumber = 3;
        List<String> ids = List.of(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.CONSENSUS_REACHED);
        Optional<Replicate> replicate1 = getStubReplicate(ReplicateStatus.STARTING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(replicate1);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(blockNumber, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_ABORT);
        TaskNotificationExtra notificationExtra = missedTaskNotifications.get(0).getTaskNotificationExtra();
        assertThat(notificationExtra.getTaskAbortCause()).isEqualTo(TaskAbortCause.CONSENSUS_REACHED);

        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    /**
     * CONTRIBUTED + Task in REVEAL phase => RecoveryAction.REVEAL
     */
    @Test
    void shouldTellReplicateToRevealSinceContributed() {
        List<String> ids = List.of(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.AT_LEAST_ONE_REVEALED);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.CONTRIBUTED);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3L, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_REVEAL);

        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    /**
     * REVEALING + !onChain => RecoveryAction.REVEAL
     */
    @Test
    void shouldTellReplicateToRevealSinceNotDoneOnchain() {
        List<String> ids = List.of(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.AT_LEAST_ONE_REVEALED);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.REVEALING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        when(replicatesService.didReplicateContributeOnchain(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(false);

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3L, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_REVEAL);

        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    /**
     * REVEALING + done onChain     => updateStatus to REVEALED
     * no RESULT_UPLOAD_REQUESTED   => RecoveryAction.WAIT
     */
    @Test
    void shouldTellReplicateToWaitSinceRevealed() {
        long blockNumber = 3;
        // ChainReceipt chainReceipt = new ChainReceipt(blockNumber, "");
        List<String> ids = List.of(CHAIN_TASK_ID);
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
        when(taskUpdateRequestManager.publishRequest(CHAIN_TASK_ID)).thenReturn(true);

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(blockNumber, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_WAIT);

        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatus.class), // REVEALED
                        any(ReplicateStatusDetails.class));
    }

    /**
     * REVEALING + done onChain     => updateStatus to REVEALED
     * RESULT_UPLOAD_REQUESTED   => RecoveryAction.UPLOAD_RESULT
     */
    @Test
    void shouldTellReplicateToUploadResultSinceRequestedAfterRevealing() {
        long blockNumber = 3;
        // ChainReceipt chainReceipt = new ChainReceipt(blockNumber, "");
        List<String> ids = List.of(CHAIN_TASK_ID);
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
        when(taskUpdateRequestManager.publishRequest(CHAIN_TASK_ID)).thenReturn(true);

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(blockNumber, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_UPLOAD);

        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatus.class), // REVEALED
                        any(ReplicateStatusDetails.class));
    }

    /**
     * RESULT_UPLOAD_REQUESTED => RecoveryAction.UPLOAD_RESULT
     */
    @Test
    void shouldTellReplicateToUploadResultSinceRequested() {
        List<String> ids = List.of(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RESULT_UPLOADING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.RESULT_UPLOAD_REQUESTED);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3L, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_UPLOAD);

        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    /**
     * RESULT_UPLOADING + not done yet => RecoveryAction.UPLOAD_RESULT
     */
    @Test
    void shouldTellReplicateToUploadResultSinceNotDoneYet() {
        List<String> ids = List.of(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RESULT_UPLOADING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.RESULT_UPLOADING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        when(replicatesService.isResultUploaded(CHAIN_TASK_ID)).thenReturn(false);

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3L, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_UPLOAD);

        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    /**
     * RESULT_UPLOADING + done => update to ReplicateStatus.RESULT_UPLOADED
     * RecoveryAction.WAIT
     */
    @Test
    void shouldTellReplicateToWaitSinceDetectedResultUpload() {
        List<String> ids = List.of(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RESULT_UPLOADING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.RESULT_UPLOADING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        when(replicatesService.isResultUploaded(CHAIN_TASK_ID)).thenReturn(true);

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3L, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_WAIT);

        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING

        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, RESULT_UPLOADED);
    }

    /**
     * RESULT_UPLOADED => RecoveryAction.WAIT
     */
    @Test
    void shouldTellReplicateToWaitSinceItUploadedResult() {
        List<String> ids = List.of(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RESULT_UPLOADING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.RESULT_UPLOADED);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        when(replicatesService.isResultUploaded(CHAIN_TASK_ID)).thenReturn(true);

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3L, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_WAIT);

        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING

        Mockito.verify(replicatesService, times(0))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, RESULT_UPLOADED);
    }

    /**
     * REVEALED + Task in completion phase => RecoveryAction.WAIT
     */
    @Test
    void shouldTellReplicateToWaitForCompletionSinceItRevealed() {
        List<String> ids = List.of(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.FINALIZING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.REVEALED);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID))
                .thenReturn(Optional.of(taskList.get(0)));
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3L, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_WAIT);

        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    /**
     * REVEALED + RESULT_UPLOADED + Task in completion phase => RecoveryAction.WAIT
     */
    @Test
    void shouldTellReplicateToWaitForCompletionSinceItRevealedAndUploaded() {
        List<String> ids = List.of(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.FINALIZING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.REVEALED);
        replicate.get().updateStatus(ReplicateStatus.RESULT_UPLOADED, ReplicateStatusModifier.POOL_MANAGER);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID))
                .thenReturn(Optional.of(taskList.get(0)));

        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3L, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_WAIT);

        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    @Test
    void shouldTellReplicateToCompleteSinceItRevealed() {
        List<String> ids = List.of(CHAIN_TASK_ID);
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

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3L, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_COMPLETE);

        Mockito.verify(replicatesService, times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    /**
     * !REVEALED + Task in completion phase => null / nothing
     */
    @Test
    void shouldNotTellReplicateToWaitForCompletionSinceItDidNotReveal() {
        List<String> ids = List.of(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.FINALIZING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.REVEALING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3L, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isEmpty();

        Mockito.verify(replicatesService, times(0))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, RECOVERING);
    }

    // region purgeTask
    @Test
    void shouldPurgeTaskWhenKnownTask() {
        final Map<String, Lock> taskAccessForNewReplicateLocks = new HashMap<>();
        taskAccessForNewReplicateLocks.put(CHAIN_TASK_ID, new ReentrantLock());
        ReflectionTestUtils.setField(replicateSupplyService, "taskAccessForNewReplicateLocks", taskAccessForNewReplicateLocks);

        assertTrue(replicateSupplyService.purgeTask(CHAIN_TASK_ID));
        assertThat(taskAccessForNewReplicateLocks).isEmpty();
    }

    @Test
    void shouldPurgeTaskWhenUnknownTask() {
        final Map<String, Lock> taskAccessForNewReplicateLocks = new HashMap<>();
        taskAccessForNewReplicateLocks.put(CHAIN_TASK_ID_2, new ReentrantLock());
        ReflectionTestUtils.setField(replicateSupplyService, "taskAccessForNewReplicateLocks", taskAccessForNewReplicateLocks);

        assertTrue(replicateSupplyService.purgeTask(CHAIN_TASK_ID));
        assertThat(taskAccessForNewReplicateLocks).containsOnlyKeys(CHAIN_TASK_ID_2);
    }

    @Test
    void shouldPurgeTaskWhenEmpty() {
        final Map<String, Lock> taskAccessForNewReplicateLocks = new HashMap<>();
        ReflectionTestUtils.setField(replicateSupplyService, "taskAccessForNewReplicateLocks", taskAccessForNewReplicateLocks);

        assertTrue(replicateSupplyService.purgeTask(CHAIN_TASK_ID));
        assertThat(taskAccessForNewReplicateLocks).isEmpty();
    }
    // endregion

    // region purgeAllTasksData
    @Test
    void shouldPurgeAllTasksDataWhenEmpty() {
        final Map<String, Lock> taskAccessForNewReplicateLocks = new HashMap<>();
        ReflectionTestUtils.setField(replicateSupplyService, "taskAccessForNewReplicateLocks", taskAccessForNewReplicateLocks);

        replicateSupplyService.purgeAllTasksData();
        assertThat(taskAccessForNewReplicateLocks).isEmpty();
    }

    @Test
    void shouldPurgeAllTasksDataWhenFull() {
        final Map<String, Lock> taskAccessForNewReplicateLocks = new HashMap<>();
        taskAccessForNewReplicateLocks.put(CHAIN_TASK_ID, new ReentrantLock());
        taskAccessForNewReplicateLocks.put(CHAIN_TASK_ID_2, new ReentrantLock());
        ReflectionTestUtils.setField(replicateSupplyService, "taskAccessForNewReplicateLocks", taskAccessForNewReplicateLocks);

        replicateSupplyService.purgeAllTasksData();
        assertThat(taskAccessForNewReplicateLocks).isEmpty();
    }
    // endregion

    List<Task> getStubTaskList(TaskStatus status) {
        Task task = Task.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .currentStatus(status)
                .build();

        return List.of(task);
    }

    Optional<Replicate> getStubReplicate(ReplicateStatus status) {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setStatusUpdateList(new ArrayList<>());
        replicate.updateStatus(status, ReplicateStatusModifier.WORKER);
        return Optional.of(replicate);
    }

    WorkerpoolAuthorization getStubAuth() {
        return new WorkerpoolAuthorization();
    }
}
