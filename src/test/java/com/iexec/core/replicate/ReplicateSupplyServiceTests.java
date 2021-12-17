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
import com.iexec.core.sms.SmsService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.TaskUpdateManager;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.core.task.TaskStatus.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;


class ReplicateSupplyServiceTests {

    private final static String WALLET_WORKER_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";

    private final static String CHAIN_TASK_ID = "0x65bc5e94ed1486b940bd6cc0013c418efad58a0a52a3d08cee89faaa21970426";

    private final static String DAPP_NAME = "dappName";
    private final static String COMMAND_LINE = "commandLine";
    private final static String NO_TEE_TAG = BytesUtils.EMPTY_HEXASTRING_64;
    private final static String TEE_TAG = "0x0000000000000000000000000000000000000000000000000000000000000001";
    private final static String ENCLAVE_CHALLENGE = "dummyEnclave";
    private final static long maxExecutionTime = 60000;
    long workerLastBlock = 12;

    @Mock private ReplicatesService replicatesService;
    @Mock private SignatureService signatureService;
    @Mock private TaskService taskService;
    @Mock private TaskUpdateManager taskUpdateManager;
    @Mock private WorkerService workerService;
    @Mock private SmsService smsService;
    @Mock private Web3jService web3jService;
    @Mock private ContributionTimeoutTaskDetector contributionTimeoutTaskDetector;

    @InjectMocks
    private ReplicateSupplyService replicateSupplyService;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }


    // Tests on getAuthOfAvailableReplicate()

    @Test
    void shouldNotGetAnyReplicateSinceWorkerDoesntExist() {
        when(workerService.getWorker(Mockito.anyString())).thenReturn(Optional.empty());

        Optional<WorkerpoolAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);
        assertThat(oAuthorization).isEmpty();
        assertTaskAccessForNewReplicateLockNeverUsed();
    }

    @Test
    void shouldNotGetReplicateSinceWorkerLastBlockNotAvailable() {
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

        when(taskService.isTaskBeingAccessedForNewReplicate(CHAIN_TASK_ID)).thenReturn(false);
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask));
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(false);
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, false)).thenReturn(BytesUtils.EMPTY_ADDRESS);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, BytesUtils.EMPTY_ADDRESS))
                .thenReturn(new WorkerpoolAuthorization());

        Optional<WorkerpoolAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(0, WALLET_WORKER_1);

        assertThat(oAuthorization).isEmpty();

        Mockito.verify(replicatesService, Mockito.times(0))
                .addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService, Mockito.times(0))
                .addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
        assertTaskAccessForNewReplicateLockNeverUsed();
    }

    @Test
    void shouldNotGetReplicateSinceNoRunningTask() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(1)
                .lastAliveDate(new Date())
                .build();

        when(taskService.isTaskBeingAccessedForNewReplicate(CHAIN_TASK_ID)).thenReturn(false);
        when(workerService.getWorker(Mockito.anyString())).thenReturn(Optional.of(existingWorker));
        when(taskService.getInitializedOrRunningTasks()).thenReturn(new ArrayList<>());

        Optional<WorkerpoolAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);
        assertThat(oAuthorization).isEmpty();
    }

    @Test
    void shouldNotGetAnyReplicateSinceWorkerIsFull() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(1)
                .lastAliveDate(new Date())
                .build();

        when(taskService.isTaskBeingAccessedForNewReplicate(CHAIN_TASK_ID)).thenReturn(false);
        Task runningTask1 = new Task(DAPP_NAME, COMMAND_LINE, 3, CHAIN_TASK_ID);
        runningTask1.changeStatus(RUNNING);

        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(false);
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask1));

        Optional<WorkerpoolAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);
        assertThat(oAuthorization).isEmpty();
        assertTaskAccessForNewReplicateLockNeverUsed();
    }

    @Test
    void shouldNotGetAnyReplicateSinceWorkerDoesNotHaveEnoughGas() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(1)
                .lastAliveDate(new Date())
                .build();

        when(taskService.isTaskBeingAccessedForNewReplicate(CHAIN_TASK_ID)).thenReturn(false);
        Task runningTask1 = new Task(DAPP_NAME, COMMAND_LINE, 3, CHAIN_TASK_ID);
        runningTask1.changeStatus(RUNNING);

        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(web3jService.hasEnoughGas(WALLET_WORKER_1)).thenReturn(false);
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask1));

        Optional<WorkerpoolAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);
        assertThat(oAuthorization).isEmpty();
        assertTaskAccessForNewReplicateLockNeverUsed();
    }

    @Test
    void shouldNotGetAnyReplicateSinceWorkerAlreadyParticipated() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(2)
                .lastAliveDate(new Date())
                .build();

        Task runningTask1 = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        runningTask1.setMaxExecutionTime(maxExecutionTime);
        runningTask1.changeStatus(RUNNING);
        runningTask1.setTag(NO_TEE_TAG);
        runningTask1.setContributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), 60));

        when(taskService.isTaskBeingAccessedForNewReplicate(CHAIN_TASK_ID)).thenReturn(false);
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(web3jService.hasEnoughGas(WALLET_WORKER_1)).thenReturn(true);
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask1));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(true);

        Optional<WorkerpoolAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);

        assertThat(oAuthorization).isEmpty();
        assertTaskAccessForNewReplicateNotDeadLocking();
    }

    @Test
    void shouldNotGetReplicateSinceNeedsMoreContributionsForConsensus() {
        Worker existingWorker = Worker.builder()
                .id("1")
                .walletAddress(WALLET_WORKER_1)
                .cpuNb(2)
                .lastAliveDate(new Date())
                .build();

        int trust = 5;
        Task runningTask = new Task(DAPP_NAME, COMMAND_LINE, trust, CHAIN_TASK_ID);
        runningTask.changeStatus(RUNNING);
        runningTask.setMaxExecutionTime(maxExecutionTime);
        runningTask.setTag(NO_TEE_TAG);
        runningTask.setContributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), 60));

        Replicate replicate = new Replicate("0x1", CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate.setWorkerWeight(trust);
        replicate.setContributionHash("test");

        when(replicatesService.getReplicates(CHAIN_TASK_ID))
                .thenReturn(List.of(replicate));

        when(taskService.isTaskBeingAccessedForNewReplicate(CHAIN_TASK_ID)).thenReturn(false);
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(web3jService.hasEnoughGas(WALLET_WORKER_1)).thenReturn(true);
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(false);

        Optional<WorkerpoolAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);
        assertThat(oAuthorization).isEmpty();
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

        when(taskService.isTaskBeingAccessedForNewReplicate(CHAIN_TASK_ID)).thenReturn(false);
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask));
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(web3jService.hasEnoughGas(WALLET_WORKER_1)).thenReturn(true);
        when(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(false);
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, true)).thenReturn("");
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, BytesUtils.EMPTY_ADDRESS))
                .thenReturn(new WorkerpoolAuthorization());

        Optional<WorkerpoolAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);

        assertThat(oAuthorization).isEmpty();

        Mockito.verify(replicatesService, Mockito.times(0))
                .addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService, Mockito.times(0))
                .addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
        assertTaskAccessForNewReplicateNotDeadLocking();
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

        Task taskDeadlineReached = new Task(DAPP_NAME, COMMAND_LINE, trust, CHAIN_TASK_ID);
        taskDeadlineReached.setMaxExecutionTime(maxExecutionTime);
        taskDeadlineReached.setContributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), -60));
        taskDeadlineReached.changeStatus(RUNNING);
        taskDeadlineReached.setTag(NO_TEE_TAG);

        Replicate replicate = new Replicate("0x1", CHAIN_TASK_ID);
        replicate.updateStatus(CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate.setWorkerWeight(trust);
        replicate.setContributionHash("test");

        when(replicatesService.getReplicates(CHAIN_TASK_ID))
                .thenReturn(List.of(replicate));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(web3jService.hasEnoughGas(WALLET_WORKER_1)).thenReturn(true);
        List<Task> tasks = new ArrayList<>();
        tasks.add(task1);
        tasks.add(taskDeadlineReached);
        when(taskService.getInitializedOrRunningTasks()).thenReturn(tasks);
        doNothing().when(contributionTimeoutTaskDetector).detect();

        replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);

        // the call should only happen once over the two tasks
        Mockito.verify(contributionTimeoutTaskDetector).detect();
        Mockito.verify(taskUpdateManager).isConsensusReached(task1);
        Mockito.verify(taskUpdateManager, Mockito.never()).isConsensusReached(taskDeadlineReached);
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

        when(taskService.isTaskBeingAccessedForNewReplicate(CHAIN_TASK_ID)).thenReturn(true);
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask));
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(false);
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, false)).thenReturn(BytesUtils.EMPTY_ADDRESS);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, BytesUtils.EMPTY_ADDRESS))
                .thenReturn(new WorkerpoolAuthorization());

        Optional<WorkerpoolAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);

        assertThat(oAuthorization).isEmpty();

        Mockito.verify(replicatesService, Mockito.times(0))
                .addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService, Mockito.times(0))
                .addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
        assertTaskAccessForNewReplicateLockNeverUsed();
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

        when(taskService.isTaskBeingAccessedForNewReplicate(CHAIN_TASK_ID)).thenReturn(false);
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask));
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(web3jService.hasEnoughGas(WALLET_WORKER_1)).thenReturn(true);
        when(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(false);
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, false)).thenReturn(BytesUtils.EMPTY_ADDRESS);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, BytesUtils.EMPTY_ADDRESS))
                .thenReturn(new WorkerpoolAuthorization());

        Optional<WorkerpoolAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);

        assertThat(oAuthorization).isPresent();

        Mockito.verify(replicatesService, Mockito.times(1))
                .addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService, Mockito.times(1))
                .addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
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

        when(taskService.isTaskBeingAccessedForNewReplicate(CHAIN_TASK_ID)).thenReturn(false);
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask));
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(web3jService.hasEnoughGas(WALLET_WORKER_1)).thenReturn(true);
        when(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1))
                .thenReturn(false);
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, true)).thenReturn(ENCLAVE_CHALLENGE);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(new WorkerpoolAuthorization());

        Optional<WorkerpoolAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);

        assertThat(oAuthorization).isPresent();

        Mockito.verify(replicatesService, Mockito.times(1))
                .addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService, Mockito.times(1))
                .addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
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

        when(taskService.isTaskBeingAccessedForNewReplicate(CHAIN_TASK_ID)).thenReturn(false);
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));

        Optional<WorkerpoolAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);

        assertThat(oAuthorization).isEmpty();

        Mockito.verify(replicatesService, Mockito.times(0))
                .addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService, Mockito.times(0))
                .addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
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

        when(taskService.isTaskBeingAccessedForNewReplicate(CHAIN_TASK_ID)).thenReturn(false);
        when(workerService.canAcceptMoreWorks(WALLET_WORKER_1)).thenReturn(true);
        when(web3jService.hasEnoughGas(WALLET_WORKER_1)).thenReturn(true);
        when(taskService.getInitializedOrRunningTasks())
                .thenReturn(Collections.singletonList(runningTask));
        when(workerService.getWorker(WALLET_WORKER_1)).thenReturn(Optional.of(existingWorker));
        when(replicatesService.hasWorkerAlreadyParticipated(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(false);
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, true)).thenReturn(ENCLAVE_CHALLENGE);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(new WorkerpoolAuthorization());

        Optional<WorkerpoolAuthorization> oAuthorization = replicateSupplyService.getAuthOfAvailableReplicate(workerLastBlock, WALLET_WORKER_1);

        assertThat(oAuthorization).isPresent();

        Mockito.verify(replicatesService, Mockito.times(1))
                .addNewReplicate(CHAIN_TASK_ID, WALLET_WORKER_1);
        Mockito.verify(workerService, Mockito.times(1))
                .addChainTaskIdToWorker(CHAIN_TASK_ID, WALLET_WORKER_1);
        assertTaskAccessForNewReplicateNotDeadLocking();
    }

    private void assertTaskAccessForNewReplicateNotDeadLocking() {
        Mockito.verify(taskService, Mockito.times(1)).lockTaskAccessForNewReplicate(CHAIN_TASK_ID);
        Mockito.verify(taskService, Mockito.times(1)).unlockTaskAccessForNewReplicate(CHAIN_TASK_ID);
    }

    private void assertTaskAccessForNewReplicateLockNeverUsed() {
        Mockito.verify(taskService, Mockito.times(0)).lockTaskAccessForNewReplicate(CHAIN_TASK_ID);
        Mockito.verify(taskService, Mockito.times(0)).unlockTaskAccessForNewReplicate(CHAIN_TASK_ID);
    }

    // Tests on getMissedTaskNotifications()

    @Test
    void shouldReturnEmptyListSinceNotParticipatingToAnyTask() {

        when(taskService.getTasksByChainTaskIds(any()))
                .thenReturn(Collections.emptyList());

        List<TaskNotification> list =
                replicateSupplyService.getMissedTaskNotifications(1l, WALLET_WORKER_1);

        assertThat(list).isEmpty();
        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }

    @Test
    void shouldNotGetInterruptedReplicateSinceEnclaveChallengeNeededButNotGenerated() {

        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        Task teeTask = new Task(DAPP_NAME, COMMAND_LINE, 5, CHAIN_TASK_ID);
        Optional<Replicate> noTeeReplicate = getStubReplicate(ReplicateStatus.COMPUTING);
        teeTask.setTag(TEE_TAG);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(Arrays.asList(teeTask));
        when(replicatesService.getReplicate(any(), any())).thenReturn(noTeeReplicate);
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, true)).thenReturn("");

        List<TaskNotification> taskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3l, WALLET_WORKER_1);

        assertThat(taskNotifications).isEmpty();

        Mockito.verify(replicatesService, Mockito.times(0))
            .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }


    @Test
    // CREATED, ..., CAN_CONTRIBUTE => RecoveryAction.CONTRIBUTE
    void shouldTellReplicateToContributeWhenComputing() {
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RUNNING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.COMPUTING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3l, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_CONTRIBUTE);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class));
    }

    @Test
    // CONTRIBUTING + !onChain => RecoveryAction.CONTRIBUTE
    void shouldTellReplicateToContributeSinceNotDoneOnchain() {
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

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3l, WALLET_WORKER_1);

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
        when(taskUpdateManager.isConsensusReached(taskList.get(0))).thenReturn(false);

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
        when(taskUpdateManager.isConsensusReached(taskList.get(0))).thenReturn(true);

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
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
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
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
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
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.AT_LEAST_ONE_REVEALED);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.CONTRIBUTED);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3l, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_REVEAL);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    @Test
    // REVEALING + !onChain => RecoveryAction.REVEAL
    void shouldTellReplicateToRevealSinceNotDoneOnchain() {
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

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3l, WALLET_WORKER_1);

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
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RESULT_UPLOADING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.RESULT_UPLOAD_REQUESTED);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3l, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_UPLOAD);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    @Test
    // RESULT_UPLOADING + not done yet => RecoveryAction.UPLOAD_RESULT
    void shouldTellReplicateToUploadResultSinceNotDoneYet() {
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RESULT_UPLOADING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.RESULT_UPLOADING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        when(replicatesService.isResultUploaded(CHAIN_TASK_ID)).thenReturn(false);

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3l, WALLET_WORKER_1);

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
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RESULT_UPLOADING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.RESULT_UPLOADING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        when(replicatesService.isResultUploaded(CHAIN_TASK_ID)).thenReturn(true);

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3l, WALLET_WORKER_1);

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
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.RESULT_UPLOADING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.RESULT_UPLOADED);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        when(replicatesService.isResultUploaded(CHAIN_TASK_ID)).thenReturn(true);

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3l, WALLET_WORKER_1);

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

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3l, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_WAIT);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    @Test
    // REVEALED + RESULT_UPLOADED + Task in completion phase => RecoveryAction.WAIT
    void shouldTellReplicateToWaitForCompletionSinceItRevealedAndUploaded() {
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
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
                replicateSupplyService.getMissedTaskNotifications(3l, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_WAIT);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    @Test
    void shouldTellReplicateToCompleteSinceItRevealed() {
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

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3l, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isNotEmpty();
        TaskNotificationType taskNotificationType = missedTaskNotifications.get(0).getTaskNotificationType();
        assertThat(taskNotificationType).isEqualTo(TaskNotificationType.PLEASE_COMPLETE);

        Mockito.verify(replicatesService, Mockito.times(1))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class)); // RECOVERING
    }

    @Test
    // !REVEALED + Task in completion phase => null / nothing
    void shouldNotTellReplicateToWaitForCompletionSinceItDidNotReveal() {
        List<String> ids = Arrays.asList(CHAIN_TASK_ID);
        List<Task> taskList = getStubTaskList(TaskStatus.FINALIZING);
        Optional<Replicate> replicate = getStubReplicate(ReplicateStatus.REVEALING);

        when(workerService.getChainTaskIds(WALLET_WORKER_1)).thenReturn(ids);
        when(taskService.getTasksByChainTaskIds(ids)).thenReturn(taskList);
        when(replicatesService.getReplicate(any(), any())).thenReturn(replicate);
        when(signatureService.createAuthorization(WALLET_WORKER_1, CHAIN_TASK_ID, ENCLAVE_CHALLENGE))
                .thenReturn(getStubAuth());

        List<TaskNotification> missedTaskNotifications =
                replicateSupplyService.getMissedTaskNotifications(3l, WALLET_WORKER_1);

        assertThat(missedTaskNotifications).isEmpty();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, RECOVERING);
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
        replicate.setStatusUpdateList(new ArrayList<ReplicateStatusUpdate>());
        replicate.updateStatus(status, ReplicateStatusModifier.WORKER);
        return Optional.of(replicate);
    }

    WorkerpoolAuthorization getStubAuth() {
        return new WorkerpoolAuthorization();
    }
}