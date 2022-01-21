/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationExtra;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.common.task.TaskAbortCause;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.DateTimeUtils;
import com.iexec.core.chain.SignatureService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.detector.task.ContributionTimeoutTaskDetector;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.TaskUpdateManager;
import com.iexec.core.tools.ContextualLock;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.core.task.Task.LONGEST_TASK_TIMEOUT;
import static com.iexec.core.task.TaskStatus.CONSENSUS_REACHED;
import static com.iexec.core.task.TaskStatus.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;


class ReplicateSupplyServiceTests {

    private final static String WALLET_WORKER_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private final static String WALLET_WORKER_2 = "0xdcfeffee1443fbf9277e6fa3b50cf3b38f7101af";

    private final static String CHAIN_TASK_ID   = "0x65bc5e94ed1486b940bd6cc0013c418efad58a0a52a3d08cee89faaa21970426";
    private final static String CHAIN_TASK_ID_2 = "0xc536af16737e02bb28100452a932056d499be3c462619751a9ed36515de64d50";

    private final static String DAPP_NAME = "dappName";
    private final static String COMMAND_LINE = "commandLine";
    private final static String NO_TEE_TAG = BytesUtils.EMPTY_HEX_STRING_32;
    private final static String TEE_TAG = "0x0000000000000000000000000000000000000000000000000000000000000001";
    private final static String ENCLAVE_CHALLENGE = "dummyEnclave";
    private final static long maxExecutionTime = 60000;
    long workerLastBlock = 12;

    @Mock private ReplicatesService replicatesService;
    @Mock private SignatureService signatureService;
    @Mock private TaskService taskService;
    @Mock private TaskUpdateManager taskUpdateManager;
    @Mock private WorkerService workerService;
    @Mock private Web3jService web3jService;
    @Mock private ContributionTimeoutTaskDetector contributionTimeoutTaskDetector;
    @Spy private ContextualLock<String> taskAccessForNewReplicateLock = new ContextualLock<>(LONGEST_TASK_TIMEOUT);

    @Spy
    @InjectMocks
    private ReplicateSupplyService replicateSupplyService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        replicateSupplyService.taskAccessForNewReplicateLock = taskAccessForNewReplicateLock;
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
        Optional<WorkerpoolAuthorization> oAuthorization =
                replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);
        assertThat(oAuthorization).isEmpty();
        Mockito.verifyNoInteractions(web3jService, taskService, contributionTimeoutTaskDetector, taskUpdateManager, replicatesService, signatureService);
    }

    @Test
    void shouldNotGetReplicateSinceWorkerLastBlockNotAvailable() {
        workerCanWorkAndHasGas(WALLET_WORKER_1);
        Optional<WorkerpoolAuthorization> oAuthorization =
                replicateSupplyService.getAuthOfAvailableReplicate(0, WALLET_WORKER_1);
        assertThat(oAuthorization).isEmpty();
        Mockito.verifyNoInteractions(web3jService, taskService, contributionTimeoutTaskDetector, taskUpdateManager, replicatesService, signatureService);
    }

    @Test
    void shouldNotGetReplicateSinceNoRunningTask() {
        workerCanWorkAndHasGas(WALLET_WORKER_1);
        when(taskService.getFirstInitializedOrRunningTask(false, Collections.emptyList())).thenReturn(Optional.empty());
        Optional<WorkerpoolAuthorization> oAuthorization =
                replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);
        assertThat(oAuthorization).isEmpty();
        Mockito.verify(taskService, Mockito.never()).getTaskByChainTaskId(CHAIN_TASK_ID);
        Mockito.verifyNoInteractions(contributionTimeoutTaskDetector, taskUpdateManager, replicatesService, signatureService);
    }

    @Test
    void shouldNotGetReplicateWhenTaskIsEmpty() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
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

        workerCanWorkAndHasGas(WALLET_WORKER_1);
        when(taskService.getFirstInitializedOrRunningTask(true, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(new ReplicatesList(CHAIN_TASK_ID, Collections.emptyList())));
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        Optional<WorkerpoolAuthorization> oAuthorization =
                replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);
        assertThat(oAuthorization).isEmpty();
        Mockito.verify(taskService).getTaskByChainTaskId(CHAIN_TASK_ID);
        Mockito.verifyNoInteractions(signatureService);
    }

    @Test
    void shouldNotGetReplicateWhenTaskNoMoreInitializedOrRunning() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
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

        Task completedTask = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        completedTask.setMaxExecutionTime(maxExecutionTime);
        completedTask.changeStatus(CONSENSUS_REACHED);
        completedTask.setTag(NO_TEE_TAG);
        completedTask.setContributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), 60));

        workerCanWorkAndHasGas(WALLET_WORKER_1);
        when(taskService.getFirstInitializedOrRunningTask(true, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(new ReplicatesList(CHAIN_TASK_ID, Collections.emptyList())));
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(completedTask));

        Optional<WorkerpoolAuthorization> oAuthorization =
                replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);
        assertThat(oAuthorization).isEmpty();
        Mockito.verify(taskService).getTaskByChainTaskId(CHAIN_TASK_ID);
        Mockito.verifyNoInteractions(signatureService);
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
        when(taskService.getFirstInitializedOrRunningTask(true, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_2)).thenReturn(Optional.of(worker));
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(runningTask));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        Optional<WorkerpoolAuthorization> oAuthorization =
                replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_2);
        assertThat(oAuthorization).isEmpty();
        Mockito.verify(taskUpdateManager, Mockito.never()).isConsensusReached(any());
        Mockito.verifyNoInteractions(signatureService);
        assertTaskAccessForNewReplicateLockNeverUsed();
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
        List<Replicate> replicates = List.of(new Replicate());
        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, replicates);

        Task runningTask = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        runningTask.setMaxExecutionTime(maxExecutionTime);
        runningTask.changeStatus(RUNNING);
        runningTask.setTag(NO_TEE_TAG);
        runningTask.setContributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), 60));
        runningTask.setEnclaveChallenge(BytesUtils.EMPTY_ADDRESS);

        workerCanWorkAndHasGas(WALLET_WORKER_2);
        when(taskService.getFirstInitializedOrRunningTask(true, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_2)).thenReturn(Optional.of(worker));
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(runningTask));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(taskUpdateManager.isConsensusReached(replicatesList)).thenReturn(true);

        Optional<WorkerpoolAuthorization> oAuthorization =
                replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_2);
        assertThat(oAuthorization).isEmpty();
        Mockito.verify(taskUpdateManager).isConsensusReached(replicatesList);
        Mockito.verifyNoInteractions(signatureService);
        assertTaskAccessForNewReplicateNotDeadLocking();
    }

    @Test
    void shouldNotGetAnyReplicateSinceWorkerIsFull() {
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(false);
        Optional<WorkerpoolAuthorization> oAuthorization =
                replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);
        assertThat(oAuthorization).isEmpty();
        Mockito.verifyNoInteractions(taskService, contributionTimeoutTaskDetector, taskUpdateManager, replicatesService, signatureService);
    }

    @Test
    void shouldNotGetAnyReplicateSinceWorkerDoesNotHaveEnoughGas() {
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(web3jService.hasEnoughGas(WALLET_WORKER_1)).thenReturn(false);
        Optional<WorkerpoolAuthorization> oAuthorization =
                replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);
        assertThat(oAuthorization).isEmpty();
        Mockito.verify(web3jService).hasEnoughGas(WALLET_WORKER_1);
        Mockito.verifyNoInteractions(taskService, contributionTimeoutTaskDetector, taskUpdateManager, replicatesService, signatureService);
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

        final ReplicatesList replicatesList = new ReplicatesList(
                CHAIN_TASK_ID,
                Collections.singletonList(new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID))
        );

        workerCanWorkAndHasGas(WALLET_WORKER_1);
        when(taskService.getFirstInitializedOrRunningTask(true, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(runningTask));
        when(replicatesService.hasWorkerAlreadyParticipated(replicatesList, WALLET_WORKER_1)).thenReturn(true);

        Optional<WorkerpoolAuthorization> oAuthorization =
                replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);

        assertThat(oAuthorization).isEmpty();
        assertTaskAccessForNewReplicateLockNeverUsed();
        Mockito.verify(replicatesService).hasWorkerAlreadyParticipated(replicatesList, WALLET_WORKER_1);
        Mockito.verifyNoInteractions(contributionTimeoutTaskDetector, signatureService);
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

        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, List.of(replicate));

        // Try to see if a replicate of the task can be scheduled on worker2
        workerCanWorkAndHasGas(WALLET_WORKER_2);
        when(taskService.getFirstInitializedOrRunningTask(true, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_2)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(runningTask));
        when(replicatesService.hasWorkerAlreadyParticipated(replicatesList, WALLET_WORKER_2)).thenReturn(false);

        Optional<WorkerpoolAuthorization> oAuthorization =
                replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_2);
        assertThat(oAuthorization).isEmpty();
        Mockito.verifyNoInteractions(contributionTimeoutTaskDetector, signatureService);
        assertTaskAccessForNewReplicateNotDeadLocking();
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

        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.emptyList());

        workerCanWorkAndHasGas(WALLET_WORKER_1);
        when(taskService.getFirstInitializedOrRunningTask(false, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(runningTask));
        when(replicatesService.hasWorkerAlreadyParticipated(replicatesList, WALLET_WORKER_1)).thenReturn(false);

        Optional<WorkerpoolAuthorization> oAuthorization =
                replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);

        assertThat(oAuthorization).isEmpty();
        Mockito.verify(replicatesService, Mockito.never()).addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService, Mockito.never()).addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verifyNoInteractions(contributionTimeoutTaskDetector, signatureService);
        assertTaskAccessForNewReplicateLockNeverUsed();
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

        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate.setWorkerWeight(trust);
        replicate.setContributionHash("test");

        ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, List.of(replicate));

        workerCanWorkAndHasGas(WALLET_WORKER_1);
        when(taskService.getFirstInitializedOrRunningTask(true, Collections.emptyList()))
                .thenReturn(Optional.of(task1));
        when(taskService.getFirstInitializedOrRunningTask(true, List.of(CHAIN_TASK_ID)))
                .thenReturn(Optional.of(taskDeadlineReached));
        doNothing().when(contributionTimeoutTaskDetector).detect();
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task1));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));

        replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);

        Mockito.verify(taskUpdateManager).isConsensusReached(replicatesList);
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
        when(taskService.getFirstInitializedOrRunningTask(true, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(new ReplicatesList(CHAIN_TASK_ID, Collections.emptyList())));
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(runningTask));
        when(taskAccessForNewReplicateLock.lockIfPossible(CHAIN_TASK_ID)).thenReturn(false);

        Optional<WorkerpoolAuthorization> oAuthorization =
                replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);

        assertThat(oAuthorization).isEmpty();
        Mockito.verify(taskAccessForNewReplicateLock).lockIfPossible(CHAIN_TASK_ID);
        Mockito.verifyNoInteractions(contributionTimeoutTaskDetector, signatureService);
        Mockito.verify(taskAccessForNewReplicateLock, Mockito.times(1)).lockIfPossible(CHAIN_TASK_ID);
        Mockito.verify(taskAccessForNewReplicateLock, Mockito.times(0)).unlock(CHAIN_TASK_ID);
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

        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.emptyList());

        workerCanWorkAndHasGas(WALLET_WORKER_1);
        when(taskService.getFirstInitializedOrRunningTask(true, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(runningTask));
        when(replicatesService.hasWorkerAlreadyParticipated(replicatesList, WALLET_WORKER_1)).thenReturn(false);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, BytesUtils.EMPTY_ADDRESS))
                .thenReturn(new WorkerpoolAuthorization());

        Optional<WorkerpoolAuthorization> oAuthorization =
                replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);

        assertThat(oAuthorization).isPresent();
        Mockito.verify(replicatesService).addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService).addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(signatureService).createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, BytesUtils.EMPTY_ADDRESS);
        Mockito.verifyNoInteractions(contributionTimeoutTaskDetector);
        assertTaskAccessForNewReplicateNotDeadLocking();
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

        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.emptyList());

        workerCanWorkAndHasGas(WALLET_WORKER_1);
        when(taskService.getFirstInitializedOrRunningTask(false, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(runningTask));
        when(replicatesService.hasWorkerAlreadyParticipated(replicatesList, WALLET_WORKER_1)).thenReturn(false);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(new WorkerpoolAuthorization());

        Optional<WorkerpoolAuthorization> oAuthorization =
                replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);

        assertThat(oAuthorization).isPresent();
        Mockito.verify(replicatesService).addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService).addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verifyNoInteractions(contributionTimeoutTaskDetector);
        assertTaskAccessForNewReplicateNotDeadLocking();
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
        when(taskService.getFirstInitializedOrRunningTask(false, Collections.emptyList()))
                .thenReturn(Optional.empty());
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(runningTask));

        Optional<WorkerpoolAuthorization> oAuthorization =
                replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);

        assertThat(oAuthorization).isEmpty();
        Mockito.verify(taskAccessForNewReplicateLock, Mockito.never()).lockIfPossible(CHAIN_TASK_ID);
        Mockito.verifyNoInteractions(contributionTimeoutTaskDetector, signatureService);
        assertTaskAccessForNewReplicateLockNeverUsed();
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

        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID, Collections.emptyList());

        workerCanWorkAndHasGas(WALLET_WORKER_1);
        when(taskService.getFirstInitializedOrRunningTask(false, Collections.emptyList()))
                .thenReturn(Optional.of(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(runningTask));
        when(replicatesService.hasWorkerAlreadyParticipated(replicatesList, WALLET_WORKER_1)).thenReturn(false);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(new WorkerpoolAuthorization());

        Optional<WorkerpoolAuthorization> oAuthorization =
                replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);

        assertThat(oAuthorization).isPresent();
        Mockito.verify(replicatesService).addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService).addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verifyNoInteractions(contributionTimeoutTaskDetector);
        assertTaskAccessForNewReplicateNotDeadLocking();
    }

    private void assertTaskAccessForNewReplicateNotDeadLocking() {
        Mockito.verify(taskAccessForNewReplicateLock).lockIfPossible(CHAIN_TASK_ID);
        Mockito.verify(taskAccessForNewReplicateLock).unlock(CHAIN_TASK_ID);
    }

    private void assertTaskAccessForNewReplicateLockNeverUsed() {
        Mockito.verify(taskAccessForNewReplicateLock, Mockito.never()).lockIfPossible(CHAIN_TASK_ID);
        Mockito.verify(taskAccessForNewReplicateLock, Mockito.never()).unlock(CHAIN_TASK_ID);
    }

    // Tests on getMissedTaskNotifications()

    @Test
    void shouldReturnEmptyListSinceNotParticipatingToAnyTask() {

        when(taskService.getTasksByChainTaskIds(any()))
                .thenReturn(Collections.emptyList());

        List<TaskNotification> list =
                replicateSupplyService.getMissedTaskNotifications(1L, WALLET_WORKER_1);

        assertThat(list).isEmpty();
        Mockito.verify(replicatesService, Mockito.times(0))
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

        Mockito.verify(replicatesService, Mockito.times(0))
            .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }


    @Test
    // CREATED, ..., CAN_CONTRIBUTE => RecoveryAction.CONTRIBUTE
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

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class));
    }

    @Test
    // CONTRIBUTING + !onChain => RecoveryAction.CONTRIBUTE
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

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    @Test
    // CONTRIBUTING + done onChain   => updateStatus to CONTRIBUTED
    // Task not in CONSENSUS_REACHED => RecoveryAction.WAIT
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
        when(taskUpdateManager.isConsensusReached(replicatesList)).thenReturn(false);

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(blockNumber, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_WAIT);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class));
        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatus.class), // CONTRIBUTED
                        any(ReplicateStatusDetails.class));
    }

    @Test
    // CONTRIBUTING + done onChain => updateStatus to CONTRIBUTED
    // Task in CONSENSUS_REACHED   => RecoveryAction.REVEAL
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
        when(taskUpdateManager.isConsensusReached(replicatesList)).thenReturn(true);

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(blockNumber, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_REVEAL);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class));
        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatus.class), // RECOVERING
                        any(ReplicateStatusDetails.class));
    }

    @Test
    // any status + Task in CONTRIBUTION_TIMEOUT => RecoveryAction.ABORT_CONTRIBUTION_TIMEOUT
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

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    @Test
    // !CONTRIBUTED + Task in CONSENSUS_REACHED => RecoveryAction.ABORT_CONSENSUS_REACHED
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

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    @Test
    // CONTRIBUTED + Task in REVEAL phase => RecoveryAction.REVEAL
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

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    @Test
    // REVEALING + !onChain => RecoveryAction.REVEAL
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

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    @Test
    // REVEALING + done onChain     => updateStatus to REVEALED
    // no RESULT_UPLOAD_REQUESTED   => RecoveryAction.WAIT
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

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        when(taskUpdateManager.publishUpdateTaskRequest(CHAIN_TASK_ID)).thenReturn(future);
        future.complete(true);

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(blockNumber, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_WAIT);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatus.class), // REVEALED
                        any(ReplicateStatusDetails.class));
    }

    @Test
    // REVEALING + done onChain     => updateStatus to REVEALED
    // RESULT_UPLOAD_REQUESTED   => RecoveryAction.UPLOAD_RESULT
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
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        when(taskUpdateManager.publishUpdateTaskRequest(CHAIN_TASK_ID)).thenReturn(future);
        future.complete(true);

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(blockNumber, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_UPLOAD);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatus.class), // REVEALED
                        any(ReplicateStatusDetails.class));
    }

    @Test
    // RESULT_UPLOAD_REQUESTED => RecoveryAction.UPLOAD_RESULT
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

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    @Test
    // RESULT_UPLOADING + not done yet => RecoveryAction.UPLOAD_RESULT
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

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    @Test
    // RESULT_UPLOADING + done => update to ReplicateStatus.RESULT_UPLOADED
    //                            RecoveryAction.WAIT
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

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, RESULT_UPLOADED);
    }

    @Test
    // RESULT_UPLOADED => RecoveryAction.WAIT
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

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, RESULT_UPLOADED);
    }

    @Test
    // REVEALED + Task in completion phase => RecoveryAction.WAIT
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

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    @Test
    // REVEALED + RESULT_UPLOADED + Task in completion phase => RecoveryAction.WAIT
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

        Mockito.verify(replicatesService, Mockito.times(1))
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

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    @Test
    // !REVEALED + Task in completion phase => null / nothing
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

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, RECOVERING);
    }

    List<Task> getStubTaskList(TaskStatus status) {
        Task task = Task.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .currentStatus(status)
                .build();

        return List.of(task);
    }

    Optional<Replicate> getStubReplicate(ReplicateStatus status) {
        Replicate replicate = new Replicate();
        replicate.setWalletAddress(WALLET_WORKER_1);
        replicate.setChainTaskId(CHAIN_TASK_ID);
        replicate.setStatusUpdateList(new ArrayList<>());
        replicate.updateStatus(status, ReplicateStatusModifier.WORKER);
        return Optional.of(replicate);
    }

    WorkerpoolAuthorization getStubAuth() {
        return new WorkerpoolAuthorization();
    }
}