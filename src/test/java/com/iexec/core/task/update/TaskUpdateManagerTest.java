/*
 * Copyright 2021-2024 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.task.update;

import com.iexec.blockchain.api.BlockchainAdapterService;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.commons.poco.chain.ChainReceipt;
import com.iexec.commons.poco.chain.ChainTask;
import com.iexec.commons.poco.chain.ChainTaskStatus;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.tee.TeeUtils;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.configuration.ResultRepositoryConfiguration;
import com.iexec.core.detector.replicate.RevealTimeoutDetector;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesList;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.sms.SmsService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.event.PleaseUploadEvent;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.iexec.common.replicate.ReplicateStatus.RESULT_UPLOAD_REQUESTED;
import static com.iexec.core.task.TaskStatus.*;
import static com.iexec.core.task.TaskTestsUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class TaskUpdateManagerTest {
    private static final String smsUrl = "smsUrl";
    private static final String ERROR_MSG = "Cannot update task [chainTaskId:%s, currentStatus:%s, expectedStatus:%s, method:%s]";

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

    @Mock
    private Web3jService web3jService;

    @Mock
    private RevealTimeoutDetector revealTimeoutDetector;

    @Mock
    private BlockchainAdapterService blockchainAdapterService;

    @Mock
    private TaskService taskService;

    @Mock
    private TaskUpdateRequestManager taskUpdateRequestManager;

    @Mock
    private SmsService smsService;

    @InjectMocks
    private TaskUpdateManager taskUpdateManager;

    @Captor
    private ArgumentCaptor<ReplicateStatusUpdate> statusUpdate;

    @BeforeAll
    static void initRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
    }

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        ChainTask chainTask = ChainTask.builder().chainTaskId(CHAIN_TASK_ID).status(ChainTaskStatus.ACTIVE).build();
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));
    }

    @AfterEach
    void afterEach() {
        Metrics.globalRegistry.clear();
    }

    // region consensusReached2Reopening

    @Test
    void shouldNotUpgrade2ReopenedSinceCurrentStatusWrong() {
        final Task task = getStubTask();

        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.of(ChainReceipt.builder().build()));

        taskUpdateManager.consensusReached2Reopening(task);

        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
    }

    @Test
    void shouldNotUpgrade2ReopenedSinceNotAfterRevealDeadline() {
        final Task task = getStubTask(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() + 100));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.of(ChainReceipt.builder().build()));

        taskUpdateManager.consensusReached2Reopening(task);

        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    @Test
    void shouldNotUpgrade2ReopenedSinceNotWeHaveSomeRevealed() {
        final Task task = getStubTask(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(1);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.of(ChainReceipt.builder().build()));

        taskUpdateManager.consensusReached2Reopening(task);

        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    @Test
    void shouldNotUpgrade2ReopenedSinceCantReopenOnChain() {
        final Task task = getStubTask(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(false);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.of(ChainReceipt.builder().build()));

        taskUpdateManager.consensusReached2Reopening(task);

        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    @Test
    void shouldNotUpgrade2ReopenedSinceNotEnoughGas() {
        final Task task = getStubTask(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(false);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.of(ChainReceipt.builder().build()));

        taskUpdateManager.consensusReached2Reopening(task);

        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    @Test
    void shouldNotUpgrade2ReopenedBut2ReopenFailedSinceTxFailed() {
        final Task task = getStubTask(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.empty());

        taskUpdateManager.consensusReached2Reopening(task);

        assertThat(task.getLastButOneStatus()).isEqualTo(REOPEN_FAILED);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    //TODO: Update reopen call
    //@Test
    void shouldUpgrade2Reopened() {
        final Task task = getStubTask(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.of(ChainReceipt.builder().build()));
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.ACTIVE)
                .build()));

        taskUpdateManager.consensusReached2Reopening(task);

        assertThat(task.getDateStatusList().get(0).getStatus()).isEqualTo(RECEIVED);
        assertThat(task.getDateStatusList().get(1).getStatus()).isEqualTo(CONSENSUS_REACHED);
        assertThat(task.getDateStatusList().get(2).getStatus()).isEqualTo(REOPENING);
        assertThat(task.getDateStatusList().get(3).getStatus()).isEqualTo(REOPENED);
        assertThat(task.getDateStatusList().get(4).getStatus()).isEqualTo(INITIALIZED);
    }

    // endregion

    // region received2Initializing

    @Test
    void shouldNotUpdateReceived2InitializingSinceChainTaskIdIsNotEmpty() {
        final Task task = getStubTask();
        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
    }

    @Test
    void shouldNotUpdateReceived2InitializingSinceCurrentStatusIsNotReceived() {
        final Task task = getStubTask(INITIALIZED);

        taskUpdateManager.received2Initializing(task);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    void shouldNotUpdateReceived2InitializingSinceNoEnoughGas() {
        final Task task = getStubTask();
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(false);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(true);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(true);
        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 1)).thenReturn(Optional.of(CHAIN_TASK_ID));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
    }

    @Test
    void shouldNotUpdateReceived2InitializingSinceTaskNotInUnsetStatusOnChain() {
        final Task task = getStubTask();
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(false);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(true);
        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.of(CHAIN_TASK_ID));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
    }

    @Test
    void shouldNotUpdateReceived2InitializingSinceAfterContributionDeadline() {
        final Task task = getStubTask();
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(true);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(false);
        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.of(CHAIN_TASK_ID));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
    }

    @Test
    void shouldNotUpdateReceived2InitializingSinceNoSmsClient() {
        final Task task = getStubTask();
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(true);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(true);
        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.of(CHAIN_TASK_ID));
        when(smsService.getVerifiedSmsUrl(CHAIN_TASK_ID, task.getTag()))
                .thenReturn(Optional.of(smsUrl));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    void shouldUpdateInitializing2InitializeFailedSinceChainTaskIdIsEmpty() {
        final Task task = getStubTask();
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(true);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(true);
        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getLastButOneStatus()).isEqualTo(INITIALIZE_FAILED);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    void shouldUpdateInitializing2InitializeFailedSinceEnclaveChallengeIsEmpty() {
        final Task task = getStubTask();
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(true);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(true);
        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.of(CHAIN_TASK_ID));
        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.of(true));
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(ChainTask.builder()
                .contributionDeadline(Instant.now().plus(60L, ChronoUnit.MINUTES).toEpochMilli())
                .build()));
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, smsUrl)).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getLastButOneStatus()).isEqualTo(INITIALIZE_FAILED);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    void shouldUpdateReceived2Initializing2InitializedOnStandard() {
        final Task task = getStubTask();
        String tag = NO_TEE_TAG;
        task.setChainTaskId(CHAIN_TASK_ID);
        task.setTag(tag);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(true);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(true);

        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.of(CHAIN_TASK_ID));
        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.of(true));
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(ChainTask.builder()
                .contributionDeadline(Instant.now().plus(60L, ChronoUnit.MINUTES).toEpochMilli())
                .build()));
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, null)).thenReturn(Optional.of(BytesUtils.EMPTY_ADDRESS));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getChainDealId()).isEqualTo(CHAIN_DEAL_ID);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 1).getStatus()).isEqualTo(INITIALIZED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(INITIALIZING);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus()).isEqualTo(RECEIVED);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
        assertThat(task.getEnclaveChallenge()).isEqualTo(BytesUtils.EMPTY_ADDRESS);
        assertThat(task.getSmsUrl()).isNull();
        verify(smsService, times(0)).getVerifiedSmsUrl(anyString(), anyString());
        verify(taskService, times(2)).updateTaskStatus(any(), any(), any()); // INITIALIZING & INITIALIZED
    }

    @Test
    void shouldUpdateReceived2Initializing2InitializedOnTee() {
        final Task task = getStubTask();
        String tag = TeeUtils.TEE_GRAMINE_ONLY_TAG;
        task.setChainTaskId(CHAIN_TASK_ID);
        task.setTag(tag);// Any TEE would be fine

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(true);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(true);

        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.of(CHAIN_TASK_ID));
        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.of(true));
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(ChainTask.builder()
                .contributionDeadline(Instant.now().plus(60L, ChronoUnit.MINUTES).toEpochMilli())
                .build()));
        when(smsService.getVerifiedSmsUrl(CHAIN_TASK_ID, tag))
                .thenReturn(Optional.of(smsUrl));
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, smsUrl)).thenReturn(Optional.of(BytesUtils.EMPTY_ADDRESS));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getChainDealId()).isEqualTo(CHAIN_DEAL_ID);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 1).getStatus()).isEqualTo(INITIALIZED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(INITIALIZING);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus()).isEqualTo(RECEIVED);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
        assertThat(task.getEnclaveChallenge()).isEqualTo(BytesUtils.EMPTY_ADDRESS);
        assertThat(task.getSmsUrl()).isEqualTo(smsUrl);
        verify(smsService, times(1)).getVerifiedSmsUrl(CHAIN_TASK_ID, tag);
        verify(taskService, times(2)).updateTaskStatus(any(), any(), any()); // INITIALIZING & INITIALIZED
    }

    @Test
    void shouldNotUpdateReceived2Initializing2InitializedOnTeeSinceCannotRetrieveSmsUrl() {
        final Task task = getStubTask();
        String tag = TeeUtils.TEE_GRAMINE_ONLY_TAG;
        task.setChainTaskId(CHAIN_TASK_ID);
        task.setTag(tag);// Any TEE would be fine

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(true);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(true);

        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.of(CHAIN_TASK_ID));
        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.of(true));
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(ChainTask.builder()
                .contributionDeadline(Instant.now().plus(60L, ChronoUnit.MINUTES).toEpochMilli())
                .build()));
        when(smsService.getVerifiedSmsUrl(CHAIN_TASK_ID, tag)).thenReturn(Optional.empty());
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, null)).thenReturn(Optional.of(BytesUtils.EMPTY_ADDRESS));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getChainDealId()).isEqualTo(CHAIN_DEAL_ID);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 1).getStatus()).isEqualTo(FAILED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(INITIALIZE_FAILED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus()).isEqualTo(RECEIVED);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
        assertThat(task.getEnclaveChallenge()).isNull();
        assertThat(task.getSmsUrl()).isNull();
        verify(smsService, times(1)).getVerifiedSmsUrl(CHAIN_TASK_ID, tag);
        verify(smsService, times(0)).getEnclaveChallenge(anyString(), anyString());
        verify(taskService, times(1)).updateTaskStatus(any(), any(), any()); // INITIALIZE_FAILED & FAILED
    }

    // endregion

    // region initializing2Initialized

    @Test
    void shouldUpdateInitializing2Initialized() {
        final Task task = getStubTask(INITIALIZING);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.of(true));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 1).getStatus()).isEqualTo(INITIALIZED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(INITIALIZING);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    void shouldNotUpdateInitializing2InitializedSinceNotInitializing(CapturedOutput output) {
        final Task task = getStubTask();
        task.setChainTaskId(CHAIN_TASK_ID);
        taskUpdateManager.initializing2Initialized(task);
        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
        assertThat(output.getOut()).contains(String.format(ERROR_MSG, CHAIN_TASK_ID, RECEIVED, INITIALIZING, "initializing2Initialized"));
    }

    @Test
    void shouldNotUpdateInitializing2InitializedSinceNotInitialized() {
        final Task task = getStubTask(INITIALIZING);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.of(false));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 4).getStatus()).isEqualTo(RECEIVED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus()).isEqualTo(INITIALIZING);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(INITIALIZE_FAILED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 1).getStatus()).isEqualTo(FAILED);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    void shouldNotUpdateInitializing2InitializedSinceFailedToCheck() {
        final Task task = getStubTask(INITIALIZING);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(RECEIVED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 1).getStatus()).isEqualTo(INITIALIZING);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZING);
    }

    // endregion

    // region initialized2Running

    @Test
    void shouldUpdateInitialized2Running() { // 1 RUNNING out of 2
        final Task task = getStubTask(INITIALIZED);

        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.STARTED, ReplicateStatusModifier.WORKER);
        final List<Replicate> replicates = List.of(replicate);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(replicates);

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    void shouldNotUpdateInitialized2RunningSinceNotInitialized(CapturedOutput output) {
        final Task task = getStubTask(INITIALIZING);
        task.setChainTaskId(CHAIN_TASK_ID);
        taskUpdateManager.initialized2Running(task, ChainTask.builder().build());
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZING);
        assertThat(output.getOut()).contains(String.format(ERROR_MSG, CHAIN_TASK_ID, INITIALIZING, INITIALIZED, "initialized2Running"));
    }

    @Test
    void shouldNotUpdateInitialized2RunningSinceNoRunningOrComputedReplicates() {
        final Task task = getStubTask(INITIALIZED);

        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.STARTING, ReplicateStatus.COMPUTED)).thenReturn(0);
        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.COMPUTED)).thenReturn(0);

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(RUNNING);
    }

    @Test
    void shouldNotUpdateInitialized2RunningSinceComputedIsMoreThanNeeded() {
        final Task task = getStubTask(INITIALIZED);

        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.STARTING, ReplicateStatus.COMPUTED)).thenReturn(2);
        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.COMPUTED)).thenReturn(4);

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    void shouldNotUpdateInitialized2ContributionTimeoutSinceChainTaskIsNotActive() {
        Date timeoutInPast = Date.from(Instant.now().minus(1L, ChronoUnit.MINUTES));

        final Task task = getStubTask(INITIALIZED);
        task.setContributionDeadline(timeoutInPast);

        ChainTask chainTask = ChainTask.builder()
                .contributionDeadline(timeoutInPast.getTime())
                .status(ChainTaskStatus.REVEALING)
                .build();

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(chainTask));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(CONTRIBUTION_TIMEOUT);
    }

    @Test
    void shouldUpdateFromInitialized2ContributionTimeout() {
        Date timeoutInPast = Date.from(Instant.now().minus(1L, ChronoUnit.MINUTES));

        final Task task = getStubTask(INITIALIZED);
        task.setContributionDeadline(timeoutInPast);

        ChainTask chainTask = ChainTask.builder()
                .contributionDeadline(timeoutInPast.getTime())
                .status(ChainTaskStatus.ACTIVE)
                .build();

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);

        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
        assertThat(task.getLastButOneStatus()).isEqualTo(CONTRIBUTION_TIMEOUT);
    }

    // endregion

    // region initializedOrRunning2ContributionTimeout

    // TODO check this, it may not be this true
    @Test
    void shouldNotReSendNotificationWhenAlreadyInContributionTimeout() {
        Date timeoutInPast = Date.from(Instant.now().minus(1L, ChronoUnit.MINUTES));

        final Task task = getStubTask(CONTRIBUTION_TIMEOUT);
        task.setContributionDeadline(timeoutInPast);

        ChainTask chainTask = ChainTask.builder()
                .contributionDeadline(timeoutInPast.getTime())
                .status(ChainTaskStatus.ACTIVE)
                .build();

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(chainTask));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        Mockito.verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any());
    }

    // endregion

    // region transitionFromRunningState

    @Test
    void shouldNotTransitionFromRunningStateWhenTaskNotRunning() {
        final Task task = getStubTask(INITIALIZED);
        task.setTag(TeeUtils.TEE_SCONE_ONLY_TAG);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.transitionFromRunningState(task, ChainTask.builder().build());
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    void shouldTransitionFromRunningToContributionTimeout() {
        Date timeoutInPast = Date.from(Instant.now().minus(1L, ChronoUnit.MINUTES));

        final Task task = getStubTask(RUNNING);
        task.setContributionDeadline(timeoutInPast);

        ChainTask chainTask = ChainTask.builder()
                .contributionDeadline(timeoutInPast.getTime())
                .status(ChainTaskStatus.ACTIVE)
                .build();

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);

        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
        assertThat(task.getLastButOneStatus()).isEqualTo(CONTRIBUTION_TIMEOUT);
    }

    @Test
    void shouldNotTransitionFromRunningStateWhenNoReplicates() {
        final Task task = getStubTask(RUNNING);
        task.setTag(TeeUtils.TEE_SCONE_ONLY_TAG);

        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.transitionFromRunningState(task,
                ChainTask.builder().chainTaskId(CHAIN_TASK_ID).status(ChainTaskStatus.ACTIVE).build());
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    void shouldNotUpdateRunning2Finalized2CompletedWhenNoReplicatesOnContributeAndFinalizeStatus() {
        final Task task = getStubTask(RUNNING);
        task.setTag(TeeUtils.TEE_SCONE_ONLY_TAG);

        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID);
        final List<Worker> workersList = List.of(Worker.builder().walletAddress(WALLET_WORKER_1).build());

        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(workerService.getAliveWorkers()).thenReturn(workersList);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.transitionFromRunningState(task,
                ChainTask.builder().chainTaskId(CHAIN_TASK_ID).status(ChainTaskStatus.ACTIVE).build());
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    void shouldNotUpdateRunning2Finalized2CompletedWhenMoreThanOneReplicatesOnContributeAndFinalizeStatus() {
        final Task task = getStubTask(RUNNING);
        task.setTag(TeeUtils.TEE_SCONE_ONLY_TAG);

        final ReplicatesList replicatesList = spy(new ReplicatesList(CHAIN_TASK_ID));

        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(replicatesList.getNbReplicatesWithCurrentStatus(ReplicateStatus.CONTRIBUTE_AND_FINALIZE_DONE)).thenReturn(2);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.transitionFromRunningState(task,
                ChainTask.builder().chainTaskId(CHAIN_TASK_ID).status(ChainTaskStatus.ACTIVE).build());
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    void shouldUpdateRunning2ConsensusReached() {
        final Task task = getStubTask(RUNNING);

        final ReplicatesList replicatesList = spy(new ReplicatesList(CHAIN_TASK_ID));

        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(2)
                .build()));
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(web3jService.getLatestBlockNumber()).thenReturn(2L);
        when(taskService.isConsensusReached(any())).thenReturn(true);
        when(iexecHubService.getConsensusBlock(anyString(), anyLong())).thenReturn(ChainReceipt.builder().blockNumber(1L).build());
        when(replicatesList.getNbValidContributedWinners(any())).thenReturn(2);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    @Test
    void shouldNotUpdateRunning2ConsensusReachedSinceWrongTaskStatus() {
        final Task task = getStubTask(INITIALIZED);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(2)
                .build()));

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    void shouldNotUpdateRunning2ConsensusReachedSinceCannotGetChainTask() {
        final Task task = getStubTask(RUNNING);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    void shouldNotUpdateRunning2ConsensusReachedSinceOnChainStatusNotRevealing() {
        final Task task = getStubTask(RUNNING);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.UNSET)
                .winnerCounter(2)
                .build()));

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    void shouldNotUpdateRunning2ConsensusReachedSinceWinnerContributorsDiffers() {
        final Task task = getStubTask(RUNNING);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(2)
                .build()));

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    void shouldUpdateRunning2RunningFailedOn1Worker() {
        final Task task = getStubTask(RUNNING);
        task.setTag(TEE_TAG);

        // 1 replicate has tried to run the task:
        // - R1 is in `COMPUTE_FAILED` status;
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.setStatusUpdateList(new ArrayList<>());
        replicate1.updateStatus(ReplicateStatus.COMPUTE_FAILED, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID);
        replicatesList.setReplicates(List.of(replicate1));
        final List<Worker> workersList = replicatesList
                .getReplicates()
                .stream()
                .map(r -> Worker.builder().walletAddress(r.getWalletAddress()).build())
                .collect(Collectors.toList());

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicatesList(task.getChainTaskId())).thenReturn(Optional.of(replicatesList));
        when(workerService.getAliveWorkers()).thenReturn(workersList);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getDateOfStatus(RUNNING_FAILED)).isPresent();
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    void shouldUpdateRunning2RunningFailedOn2Workers() {
        final Task task = getStubTask(RUNNING);
        task.setTag(TEE_TAG);

        // 2 replicates have tried to run the task:
        // - R1 is in `COMPUTE_FAILED` status;
        // - R2 is in `APP_DOWNLOAD_FAILED` status.
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.setStatusUpdateList(new ArrayList<>());
        replicate1.updateStatus(ReplicateStatus.COMPUTE_FAILED, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.setStatusUpdateList(new ArrayList<>());
        replicate2.updateStatus(ReplicateStatus.APP_DOWNLOAD_FAILED, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID);
        replicatesList.setReplicates(List.of(replicate1, replicate2));
        final List<Worker> workersList = replicatesList
                .getReplicates()
                .stream()
                .map(r -> Worker.builder().walletAddress(r.getWalletAddress()).build())
                .collect(Collectors.toList());

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicatesList(task.getChainTaskId())).thenReturn(Optional.of(replicatesList));
        when(workerService.getAliveWorkers()).thenReturn(workersList);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getDateOfStatus(RUNNING_FAILED)).isPresent();
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    void shouldNotUpdateRunning2RunningFailedOn1WorkerAsNonTeeTask() {
        final Task task = getStubTask(RUNNING);
        task.setTag(NO_TEE_TAG);

        // 1 replicate has tried to run the task:
        // - R1 is in `COMPUTE_FAILED` status;
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.setStatusUpdateList(new ArrayList<>());
        replicate1.updateStatus(ReplicateStatus.COMPUTE_FAILED, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList();
        replicatesList.setReplicates(List.of(replicate1));
        final List<Worker> workersList = replicatesList
                .getReplicates()
                .stream()
                .map(r -> Worker.builder().walletAddress(r.getWalletAddress()).build())
                .collect(Collectors.toList());

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.empty());
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicatesList(task.getChainTaskId())).thenReturn(Optional.of(replicatesList));
        when(workerService.getAliveWorkers()).thenReturn(workersList);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getDateOfStatus(RUNNING)).isPresent();
    }

    @Test
    void shouldNotUpdateRunning2RunningFailedOn2WorkersAsNonTeeTask() {
        final Task task = getStubTask(RUNNING);
        task.setTag(NO_TEE_TAG);

        // 2 replicates have tried to run the task:
        // - R1 is in `COMPUTE_FAILED` status;
        // - R2 is in `APP_DOWNLOAD_FAILED` status.
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.setStatusUpdateList(new ArrayList<>());
        replicate1.updateStatus(ReplicateStatus.COMPUTE_FAILED, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.setStatusUpdateList(new ArrayList<>());
        replicate2.updateStatus(ReplicateStatus.APP_DOWNLOAD_FAILED, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList();
        replicatesList.setReplicates(List.of(replicate1, replicate2));
        final List<Worker> workersList = replicatesList
                .getReplicates()
                .stream()
                .map(r -> Worker.builder().walletAddress(r.getWalletAddress()).build())
                .collect(Collectors.toList());

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.empty());
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicatesList(task.getChainTaskId())).thenReturn(Optional.of(replicatesList));
        when(workerService.getAliveWorkers()).thenReturn(workersList);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getDateOfStatus(RUNNING)).isPresent();
    }

    @Test
    void shouldNotUpdateRunning2AllWorkersFailedSinceOneStillComputing() {
        final Task task = getStubTask(RUNNING);
        task.setTag(TEE_TAG);

        // 2 replicates have tried to run the task:
        // - R1 is in `COMPUTE_FAILED` status;
        // - R2 is in `COMPUTING` status.
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.setStatusUpdateList(new ArrayList<>());
        replicate1.updateStatus(ReplicateStatus.COMPUTE_FAILED, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.setStatusUpdateList(new ArrayList<>());
        replicate2.updateStatus(ReplicateStatus.COMPUTING, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList();
        replicatesList.setReplicates(List.of(replicate1, replicate2));
        final List<Worker> workersList = replicatesList
                .getReplicates()
                .stream()
                .map(r -> Worker.builder().walletAddress(r.getWalletAddress()).build())
                .collect(Collectors.toList());

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.empty());
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicatesList(task.getChainTaskId())).thenReturn(Optional.of(replicatesList));
        when(workerService.getAliveWorkers()).thenReturn(workersList);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getDateOfStatus(RUNNING_FAILED)).isEmpty();
    }

    @Test
    void shouldNotUpdateRunning2AllWorkersFailedSinceOneHasReachedComputed() {
        final Task task = getStubTask(RUNNING);
        task.setTag(TEE_TAG);

        // 2 replicates have tried to run the task:
        // - R1 is in `COMPUTE_FAILED` status;
        // - R2 is in `CONTRIBUTE_FAILED` status.
        // Worker of R2 has been lost.
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.setStatusUpdateList(new ArrayList<>());
        replicate1.updateStatus(ReplicateStatus.COMPUTE_FAILED, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.setStatusUpdateList(new ArrayList<>());
        replicate2.updateStatus(ReplicateStatus.CONTRIBUTE_FAILED, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList();
        replicatesList.setReplicates(List.of(replicate1, replicate2));
        final List<Worker> workersList = List.of(
                Worker.builder().walletAddress(replicate2.getWalletAddress()).build()
        );

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.empty());
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicatesList(task.getChainTaskId())).thenReturn(Optional.of(replicatesList));
        when(workerService.getAliveWorkers()).thenReturn(workersList);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getDateOfStatus(RUNNING_FAILED)).isEmpty();
    }

    @Test
    void shouldNotUpdateRunning2AllWorkersFailedSinceOneStillHasToBeLaunched() {
        final Task task = getStubTask(RUNNING);
        task.setTag(TEE_TAG);

        // 1 replicates have tried to run the task and 1 is still to be run:
        // - R1 is in `COMPUTE_FAILED` status;
        // - R2 has not started yet.
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.setStatusUpdateList(new ArrayList<>());
        replicate1.updateStatus(ReplicateStatus.COMPUTE_FAILED, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList();
        replicatesList.setReplicates(List.of(replicate1));
        final List<Worker> workersList = List.of(
                Worker.builder().walletAddress(WALLET_WORKER_1).build(),
                Worker.builder().walletAddress(WALLET_WORKER_2).build()
        );

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.empty());
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicatesList(task.getChainTaskId())).thenReturn(Optional.of(replicatesList));
        when(workerService.getAliveWorkers()).thenReturn(workersList);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getDateOfStatus(RUNNING_FAILED)).isEmpty();
    }

    // endregion

    // region consensusReached2AtLeastOneReveal2UploadRequested

    @Test
    void shouldUpdateConsensusReached2AtLeastOneReveal2Uploading() {
        final Task task = getStubTask(CONSENSUS_REACHED);
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.REVEALED)).thenReturn(1);
        when(replicatesService.getRandomReplicateWithRevealStatus(task.getChainTaskId())).thenReturn(Optional.of(replicate));

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getUploadingWorkerWalletAddress()).isEqualTo(replicate.getWalletAddress());
        int size = task.getDateStatusList().size();
        assertThat(task.getDateStatusList().get(size - 2).getStatus()).isEqualTo(AT_LEAST_ONE_REVEALED);
        assertThat(task.getDateStatusList().get(size - 1).getStatus()).isEqualTo(RESULT_UPLOADING);
    }

    @Test
    void shouldNotUpdateConsensusReached2AtLeastOneReveal2ResultUploadingSinceNotConsensusReached(CapturedOutput output) {
        final Task task = getStubTask(RUNNING);
        task.setChainTaskId(CHAIN_TASK_ID);
        taskUpdateManager.consensusReached2AtLeastOneReveal2ResultUploading(task);
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
        assertThat(output.getOut()).contains(String.format(ERROR_MSG, CHAIN_TASK_ID, RUNNING, CONSENSUS_REACHED, "consensusReached2AtLeastOneReveal2ResultUploading"));
    }

    @Test
    void shouldNotUpdateConsensusReached2AtLeastOneRevealSinceNoRevealedReplicate() {
        final Task task = getStubTask(CONSENSUS_REACHED);
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.REVEALED)).thenReturn(0);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    // endregion

    // region requestUpload

    @Test
    void shouldRequestUploadAfterOneRevealed() {
        final Task task = getStubTask(AT_LEAST_ONE_REVEALED);
        task.setChainTaskId(CHAIN_TASK_ID);

        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getRandomReplicateWithRevealStatus(task.getChainTaskId())).thenReturn(Optional.of(replicate));

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);
    }

    @Test
    void shouldNotRequestUploadAfterOneRevealedAsWorkerLost() {
        final Task task = getStubTask(AT_LEAST_ONE_REVEALED);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getRandomReplicateWithRevealStatus(task.getChainTaskId())).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getCurrentStatus()).isEqualTo(AT_LEAST_ONE_REVEALED);
    }

    // endregion

    // region reopening2Reopened

    @Test
    void shouldUpdateReopening2Reopened() {
        final Task task = getStubTask(REOPENING);

        final ChainTask chainTask = ChainTask
                .builder()
                .chainTaskId(CHAIN_TASK_ID)
                .status(ChainTaskStatus.ACTIVE)
                .build();

        final Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);
        final Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));
        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(List.of(replicate1, replicate2));

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getConsensus()).isNull();
        assertThat(task.getRevealDeadline().toInstant()).isEqualTo(new Date(0).toInstant());
        assertThat(task.getDateOfStatus(REOPENED)).isPresent();
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);

        verify(replicatesService, times(2)).setRevealTimeoutStatusIfNeeded(eq(CHAIN_TASK_ID), any());
    }

    @Test
    void shouldNotUpdateReopening2ReopenedSinceUnknownChainTask() {
        final Task task = getStubTask(REOPENING);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(REOPENING);

        verify(replicatesService, times(0)).setRevealTimeoutStatusIfNeeded(eq(CHAIN_TASK_ID), any());
    }

    // endregion

    // region reopened2Initialized

    @Test
    void shouldUpdateReopened() {
        final Task task = getStubTask(REOPENED);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    // endregion

    // region resultUploading2Uploaded2Finalizing2Finalized

    @Test
    void shouldUpdateResultUploading2Uploaded2Finalizing2Finalized() { //one worker uploaded
        final Task task = getStubTask(RESULT_UPLOADING);

        ChainTask chainTask = ChainTask.builder().revealCounter(1).build();
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setResultLink(RESULT_LINK);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.of(replicate));
        when(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.RESULT_UPLOADED)).thenReturn(1);
        when(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(1);
        when(iexecHubService.canFinalize(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.getChainTask(any())).thenReturn(Optional.of(chainTask));
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(blockchainAdapterService.requestFinalize(any(), any(), any())).thenReturn(Optional.of(CHAIN_TASK_ID));
        when(blockchainAdapterService.isFinalized(any())).thenReturn(Optional.of(true));
        when(resulRepositoryConfig.getResultRepositoryURL()).thenReturn("http://foo:bar");
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.COMPLETED)
                .revealCounter(1)
                .build()));

        taskUpdateManager.updateTask(task.getChainTaskId());

        TaskStatus lastButOneStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus();
        TaskStatus lastButTwoStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus();
        TaskStatus lastButThreeStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 4).getStatus();

        assertThat(task.getCurrentStatus()).isEqualTo(COMPLETED);
        assertThat(lastButOneStatus).isEqualTo(FINALIZED);
        assertThat(lastButTwoStatus).isEqualTo(FINALIZING);
        assertThat(lastButThreeStatus).isEqualTo(RESULT_UPLOADED);
    }

    // endregion

    // region finalizing2Finalized

    @Test
    void shouldUpdateFinalized2Completed() {
        final Task task = getStubTask(FINALIZED);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(COMPLETED);
    }

    @Test
    void shouldUpdateFinalizing2FinalizedFailed() {
        final Task task = getStubTask(FINALIZING);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(blockchainAdapterService.isFinalized(CHAIN_TASK_ID)).thenReturn(Optional.of(false));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(FINALIZE_FAILED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus()).isEqualTo(FINALIZING);
    }

    // endregion

    // region resultUploading2Uploaded
    @Test
    void shouldUpdateResultUploading2UploadedButNot2Finalizing() { //one worker uploaded
        final Task task = getStubTask(RESULT_UPLOADING);

        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setResultLink(RESULT_LINK);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.of(replicate));
        when(iexecHubService.canFinalize(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(false);

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(FINALIZING);
    }

    @Test
    void shouldUpdateResultUploading2Uploaded2Finalizing2FinalizeFail() { //one worker uploaded && finalize FAIL
        final Task task = getStubTask(RESULT_UPLOADING);
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setResultLink(RESULT_LINK);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.of(replicate));
        when(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.RESULT_UPLOADED)).thenReturn(1);
        when(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(1);
        when(iexecHubService.canFinalize(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(blockchainAdapterService.requestFinalize(any(), any(), any())).thenReturn(Optional.empty());
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.FAILLED)
                .revealCounter(1)
                .build()));

        taskUpdateManager.updateTask(task.getChainTaskId());

        TaskStatus lastButOneStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus();
        TaskStatus lastButTwoStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus();

        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
        assertThat(lastButOneStatus).isEqualTo(FINALIZE_FAILED);
        assertThat(lastButTwoStatus).isEqualTo(RESULT_UPLOADED);
    }

    @Test
    void shouldUpdateResultUploading2UploadedFailAndRequestUploadAgain() {
        final Task task = getStubTask(RESULT_UPLOADING);

        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getRandomReplicateWithRevealStatus(task.getChainTaskId())).thenReturn(Optional.of(replicate));

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getUploadingWorkerWalletAddress()).isEqualTo(replicate.getWalletAddress());
        assertThat(task.getLatestStatusChange().getStatus()).isEqualTo(RESULT_UPLOADING);

        verify(replicatesService).updateReplicateStatus(eq(CHAIN_TASK_ID), eq(WALLET_WORKER_1), statusUpdate.capture());
        verify(applicationEventPublisher).publishEvent(any(PleaseUploadEvent.class));
        assertThat(statusUpdate.getValue().getStatus()).isEqualTo(RESULT_UPLOAD_REQUESTED);
    }

    @Test
    void shouldNotUpdateResultUploading2UploadedSinceNotResultUploading(CapturedOutput output) {
        final Task task = getStubTask(CONSENSUS_REACHED);
        task.setChainTaskId(CHAIN_TASK_ID);
        taskUpdateManager.resultUploading2Uploaded(ChainTask.builder().build(), task);
        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
        assertThat(output.getOut()).contains(String.format(ERROR_MSG, CHAIN_TASK_ID, CONSENSUS_REACHED, RESULT_UPLOADING, "resultUploading2Uploaded"));
    }

    @Test
    void shouldNotUpdateResultUploading2UploadedSinceNoUploadingReplicate() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);

        final Task task = getStubTask(RESULT_UPLOADING);
        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.empty());
        when(replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID)).thenReturn(Optional.of(replicate));

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);

        verify(replicatesService).updateReplicateStatus(eq(CHAIN_TASK_ID), eq(WALLET_WORKER_1), statusUpdate.capture());
        assertThat(statusUpdate.getValue().getStatus()).isEqualTo(RESULT_UPLOAD_REQUESTED);
    }

    @Test
    void shouldNotUpdateResultUploading2UploadedSinceUploadingReplicateInUploadingStatus() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatusUpdate.builder().status(ReplicateStatus.RESULT_UPLOADING).build());

        final Task task = getStubTask(RESULT_UPLOADING);
        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(replicate));

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);
    }

    @Test
    void shouldNotUpdateResultUploading2UploadedSinceUploadingReplicateInRecoveringUploadingStatus() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatusUpdate.builder().status(ReplicateStatus.RESULT_UPLOADING).build());
        replicate.updateStatus(ReplicateStatusUpdate.builder().status(ReplicateStatus.RECOVERING).build());

        final Task task = getStubTask(RESULT_UPLOADING);
        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(replicate));

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);
    }

    @Test
    void shouldNotUpdateResultUploading2UploadedSinceUploadingReplicateNotInUploadingNorRecoveringUploadingStatus() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatusUpdate.builder().status(ReplicateStatus.REVEALED).build());

        final Task task = getStubTask(RESULT_UPLOADING);
        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(replicate));
        when(replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID)).thenReturn(Optional.of(replicate));

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);

        verify(replicatesService).updateReplicateStatus(eq(CHAIN_TASK_ID), eq(WALLET_WORKER_1), statusUpdate.capture());
        assertThat(statusUpdate.getValue().getStatus()).isEqualTo(RESULT_UPLOAD_REQUESTED);
    }
    // endregion

    @Test
    void shouldUpdateResultUploading2UploadTimeout() {
        final Task task = getStubTask(RESULT_UPLOADING);
        task.setFinalDeadline(new Date(0));

        taskUpdateManager.resultUploading2Uploaded(ChainTask.builder().build(), task);

        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(RESULT_UPLOAD_TIMEOUT);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.FAILED);
    }
    // endregion

    // region resultUploaded2Finalizing
    @Test
    void shouldUpdateResultUploaded2Finalizing() {
        final Task task = getStubTask(RESULT_UPLOADED);

        ChainTask chainTask = ChainTask.builder().revealCounter(1).build();
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setResultLink(RESULT_LINK);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.canFinalize(CHAIN_TASK_ID)).thenReturn(true);
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));
        when(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(1);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(blockchainAdapterService.requestFinalize(any(), any(), any())).thenReturn(Optional.of(CHAIN_TASK_ID));

        when(blockchainAdapterService.isFinalized(any())).thenReturn(Optional.of(true));

        taskUpdateManager.updateTask(task.getChainTaskId());

        TaskStatus lastButOneStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus();
        TaskStatus lastButTwoStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus();
        TaskStatus lastButThreeStatus = task.getDateStatusList().get(task.getDateStatusList().size() - 4).getStatus();

        assertThat(task.getCurrentStatus()).isEqualTo(COMPLETED);
        assertThat(lastButOneStatus).isEqualTo(FINALIZED);
        assertThat(lastButTwoStatus).isEqualTo(FINALIZING);
        assertThat(lastButThreeStatus).isEqualTo(RESULT_UPLOADED);
    }

    @Test
    void shouldNotUpdateResultUploaded2FinalizingSinceNotResultUploaded(CapturedOutput output) {
        final Task task = getStubTask(RESULT_UPLOADING);
        task.setChainTaskId(CHAIN_TASK_ID);
        taskUpdateManager.resultUploaded2Finalizing(ChainTask.builder().build(), task);
        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);
        assertThat(output.getOut()).contains(String.format(ERROR_MSG, CHAIN_TASK_ID, RESULT_UPLOADING, RESULT_UPLOADED, "resultUploaded2Finalizing"));
    }

    @Test
    void shouldNotUpdateResultUploaded2FinalizingSinceCantFinalize() {
        final Task task = getStubTask(RESULT_UPLOADED);

        ChainTask chainTask = ChainTask.builder().revealCounter(1).build();
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setResultLink(RESULT_LINK);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.canFinalize(CHAIN_TASK_ID)).thenReturn(false);
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));
        when(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(1);

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADED);
    }

    @Test
    void shouldNotUpdateResultUploaded2FinalizingSinceRevealsNotEqual() {
        final Task task = getStubTask(RESULT_UPLOADED);

        ChainTask chainTask = ChainTask.builder().revealCounter(1).build();
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setResultLink(RESULT_LINK);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.canFinalize(CHAIN_TASK_ID)).thenReturn(true);
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));
        when(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(2);

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADED);
    }

    @Test
    void shouldNotUpdateResultUploaded2FinalizingSinceNotEnoughGas() {
        final Task task = getStubTask(RESULT_UPLOADED);

        ChainTask chainTask = ChainTask.builder().revealCounter(1).build();
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setResultLink(RESULT_LINK);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.canFinalize(CHAIN_TASK_ID)).thenReturn(true);
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));
        when(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(1);
        when(iexecHubService.hasEnoughGas()).thenReturn(false);

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADED);
    }

    @Test
    void shouldNotUpdateResultUploaded2FinalizingSinceCantRequestFinalize() {
        final Task task = getStubTask(RESULT_UPLOADED);

        ChainTask chainTask = ChainTask.builder().revealCounter(1).build();
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setResultLink(RESULT_LINK);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.canFinalize(CHAIN_TASK_ID)).thenReturn(true);
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));
        when(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(1);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(blockchainAdapterService.requestFinalize(eq(CHAIN_TASK_ID), any(), any())).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getDateOfStatus(FINALIZE_FAILED)).isPresent();
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    // endregion

    // region finalizingToFinalizedToCompleted

    @Test
    void shouldUpdateFinalizing2Finalized2Completed() {
        final Task task = getStubTask(FINALIZING);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(blockchainAdapterService.isFinalized(CHAIN_TASK_ID)).thenReturn(Optional.of(true));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(COMPLETED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(FINALIZED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus()).isEqualTo(FINALIZING);
    }

    @Test
    void shouldNotUpdateFinalizing2Finalized2CompletedSinceNotFinalizing(CapturedOutput output) {
        final Task task = getStubTask(RESULT_UPLOADED);
        task.setChainTaskId(CHAIN_TASK_ID);
        taskUpdateManager.finalizing2Finalized2Completed(task);
        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADED);
        assertThat(output.getOut()).contains(String.format(ERROR_MSG, CHAIN_TASK_ID, RESULT_UPLOADED, FINALIZING, "finalizing2Finalized2Completed"));
    }

    @Test
    void shouldNotUpdateFinalizing2FinalizedSinceNotFinalized() {
        final Task task = getStubTask(FINALIZING);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(blockchainAdapterService.isFinalized(CHAIN_TASK_ID)).thenReturn(Optional.of(false));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 4).getStatus()).isEqualTo(RECEIVED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus()).isEqualTo(FINALIZING);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(FINALIZE_FAILED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 1).getStatus()).isEqualTo(FAILED);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    void shouldNotUpdateFinalizing2FinalizedSinceFailedToCheck() {
        final Task task = getStubTask(FINALIZING);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(blockchainAdapterService.isFinalized(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(RECEIVED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 1).getStatus()).isEqualTo(FINALIZING);
        assertThat(task.getCurrentStatus()).isEqualTo(FINALIZING);
    }

    // endregion

    // region finalizedToCompleted

    @Test
    void shouldNotUpdateFinalized2CompletedSinceNotFinalized(CapturedOutput output) {
        final Task task = getStubTask(FINALIZING);
        task.setChainTaskId(CHAIN_TASK_ID);
        taskUpdateManager.finalizedToCompleted(task);
        assertThat(task.getCurrentStatus()).isEqualTo(FINALIZING);
        assertThat(output.getOut()).contains(String.format(ERROR_MSG, CHAIN_TASK_ID, FINALIZING, FINALIZED, "finalizedToCompleted"));
    }

    // endregion

    // 3 replicates in RUNNING 0 in COMPUTED
    @Test
    void shouldUpdateTaskToRunningFromWorkersInRunning() {
        final Task task = getStubTask(INITIALIZED);
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.APP_DOWNLOADING, ReplicateStatusModifier.WORKER);
        final List<Replicate> replicates = List.of(replicate);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(replicates);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    // 2 replicates in RUNNING and 2 in COMPUTED
    @Test
    void shouldUpdateTaskToRunningFromWorkersInRunningAndComputed() {
        final Task task = getStubTask(INITIALIZED);
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        final List<Replicate> replicates = List.of(replicate);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(replicates);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    // all replicates in INITIALIZED
    @Test
    void shouldNotUpdateToRunningSinceAllReplicatesInCreated() {
        final Task task = getStubTask();
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.STARTING, ReplicateStatus.COMPUTED)).thenReturn(0);
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(0);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RUNNING);
    }

    // Two replicates in COMPUTED BUT numWorkersNeeded = 2, so the task should not be able to move directly from
    // INITIALIZED to COMPUTED
    @Test
    void shouldNotUpdateToRunningCase2() {
        final Task task = getStubTask();
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.STARTING, ReplicateStatus.COMPUTED)).thenReturn(2);
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(2);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RUNNING);
    }


    // at least one UPLOADED
    @Test
    void shouldUpdateFromUploadingResultToResultUploaded() {
        final Task task = getStubTask(RESULT_UPLOADING);

        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setResultLink(RESULT_LINK);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.of(replicate));

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RESULT_UPLOADED);
        assertThat(task.getResultLink()).isEqualTo(RESULT_LINK);
    }

    // No worker in UPLOADED
    @Test
    void shouldNotUpdateToResultUploaded() {
        final Task task = getStubTask(RESULT_UPLOADING);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.RESULT_UPLOADED)).thenReturn(0);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RESULT_UPLOADED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    void shouldNotUpdateFromResultUploadedToFinalizingSinceNotEnoughGas() {
        final Task task = getStubTask(RESULT_UPLOADED);
        ChainTask chainTask = ChainTask.builder().revealCounter(1).build();

        when(iexecHubService.canFinalize(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(chainTask));
        when(replicatesService.getNbReplicatesContainingStatus(task.getChainTaskId(), ReplicateStatus.REVEALED)).thenReturn(1);
        when(iexecHubService.hasEnoughGas()).thenReturn(false);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADED);
    }

    @Test
    void shouldUpdateTaskRunning2Finalized2Completed() {
        final Task task = getStubTask(RUNNING);
        task.setTag(TeeUtils.TEE_SCONE_ONLY_TAG);

        final ReplicatesList replicatesList = spy(new ReplicatesList(CHAIN_TASK_ID));

        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(replicatesList.getNbReplicatesWithCurrentStatus(ReplicateStatus.CONTRIBUTE_AND_FINALIZE_DONE)).thenReturn(1);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(COMPLETED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(FINALIZED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus()).isEqualTo(RUNNING);
    }

    @Test
    void shouldUpdateFromAnyInProgressStatus2FinalDeadlineReached() {
        final Task task = getStubTask();
        task.setFinalDeadline(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getLastButOneStatus()).isEqualTo(FINAL_DEADLINE_REACHED);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    void shouldUpdateFromFinalDeadlineReached2Failed() {
        final Task task = getStubTask(FINAL_DEADLINE_REACHED);
        task.setFinalDeadline(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    void shouldNotUpdateToFinalDeadlineReachedIfAlreadyFailed() {
        final Task task = getStubTask(FAILED);
        task.setFinalDeadline(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    void shouldNotUpdateToFinalDeadlineReachedIfAlreadyCompleted() {
        final Task task = getStubTask(COMPLETED);
        task.setFinalDeadline(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(COMPLETED);
    }

    // Tests on toFailed() automatic transitions
    @Test
    void shouldUpdateToFailed() {
        final TaskStatus[] statusThatAutomaticallyFail = new TaskStatus[]{
                INITIALIZE_FAILED,
                RUNNING_FAILED,
                CONTRIBUTION_TIMEOUT,
                REOPEN_FAILED,
                RESULT_UPLOAD_TIMEOUT,
                FINALIZE_FAILED};

        for (TaskStatus status : statusThatAutomaticallyFail) {
            final Task task = getStubTask(status);
            task.setChainTaskId(CHAIN_TASK_ID);

            when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));

            taskUpdateManager.updateTask(task.getChainTaskId());

            assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
        }
    }

    // region requestUpload

    @Test
    void shouldRequestUpload() {
        final Task task = getStubTask();
        task.setChainTaskId(CHAIN_TASK_ID);

        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getNbReplicatesWithCurrentStatus(
                CHAIN_TASK_ID,
                ReplicateStatus.RESULT_UPLOADING,
                RESULT_UPLOAD_REQUESTED)
        ).thenReturn(0);
        when(replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID)).thenReturn(Optional.of(replicate));

        taskUpdateManager.requestUpload(task);

        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);
        verify(replicatesService).updateReplicateStatus(eq(CHAIN_TASK_ID), eq(WALLET_WORKER_1), statusUpdate.capture());
        verify(applicationEventPublisher).publishEvent(any(PleaseUploadEvent.class));
        assertThat(statusUpdate.getValue().getStatus()).isEqualTo(RESULT_UPLOAD_REQUESTED);
    }

    @Test
    void shouldNotRequestUpload() {
        final Task task = getStubTask(AT_LEAST_ONE_REVEALED);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(replicatesService.getNbReplicatesWithCurrentStatus(
                CHAIN_TASK_ID,
                ReplicateStatus.RESULT_UPLOADING,
                RESULT_UPLOAD_REQUESTED)
        ).thenReturn(0);
        // For example, this could happen if replicate is lost after having revealed.
        when(replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        taskUpdateManager.requestUpload(task);

        assertThat(task.getCurrentStatus()).isEqualTo(AT_LEAST_ONE_REVEALED);
        verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class));
        verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any(PleaseUploadEvent.class));
    }

    @Test
    void shouldNotRequestUploadSinceUploadInProgress() {
        final Task task = getStubTask(AT_LEAST_ONE_REVEALED);
        task.setChainTaskId(CHAIN_TASK_ID);

        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getNbReplicatesWithCurrentStatus(
                CHAIN_TASK_ID,
                ReplicateStatus.RESULT_UPLOADING,
                RESULT_UPLOAD_REQUESTED)
        ).thenReturn(1);

        taskUpdateManager.requestUpload(task);

        assertThat(task.getCurrentStatus()).isEqualTo(AT_LEAST_ONE_REVEALED);
        verify(replicatesService, Mockito.times(0))
                .getRandomReplicateWithRevealStatus(CHAIN_TASK_ID);
        verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class));
        verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any(PleaseUploadEvent.class));
    }

    // endregion

    // region publishRequest

    @Test
    void shouldTriggerUpdateTaskAsynchronously() {
        taskUpdateRequestManager.publishRequest(CHAIN_TASK_ID);
        verify(taskUpdateRequestManager).publishRequest(CHAIN_TASK_ID);
    }

    // endregion

    // region utils
    private void mockTaskDescriptionFromTask(Task task) {
        final TaskDescription taskDescription = TaskDescription.builder()
                .chainTaskId(task.getChainTaskId())
                .isTeeTask(task.isTeeTask())
                .trust(BigInteger.valueOf(task.getTrust()))
                .callback("")
                .build();
        when(iexecHubService.getTaskDescription(task.getChainTaskId())).thenReturn(taskDescription);
    }
    // endregion
}
