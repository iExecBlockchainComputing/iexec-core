/*
 * Copyright 2021-2025 IEXEC BLOCKCHAIN TECH
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
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesList;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.sms.SmsService;
import com.iexec.core.task.*;
import com.iexec.core.task.event.PleaseUploadEvent;
import com.iexec.core.task.event.TaskFailedEvent;
import com.iexec.core.task.event.TaskInitializedEvent;
import com.iexec.core.task.event.TaskStatusesCountUpdatedEvent;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatus.RESULT_UPLOAD_REQUESTED;
import static com.iexec.core.TestUtils.*;
import static com.iexec.core.task.TaskStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataMongoTest
@TestPropertySource(properties = {"mongock.enabled=false"})
@Testcontainers
@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
class TaskUpdateManagerTests {
    private static final String SMS_URL = "smsUrl";
    private static final String ERROR_MSG = "Cannot update task [chainTaskId:%s, currentStatus:%s, expectedStatus:%s, method:%s]";

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse(System.getProperty("mongo.image")));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.host", mongoDBContainer::getHost);
        registry.add("spring.data.mongodb.port", () -> mongoDBContainer.getMappedPort(27017));
    }

    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private TaskRepository taskRepository;

    @Mock
    private WorkerService workerService;

    @Mock
    private IexecHubService iexecHubService;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private BlockchainAdapterService blockchainAdapterService;

    @Mock
    private SmsService smsService;

    private TaskUpdateManager taskUpdateManager;

    @Captor
    private ArgumentCaptor<ReplicateStatusUpdate> statusUpdate;

    @BeforeAll
    static void initRegistry() {
        Metrics.globalRegistry.add(new SimpleMeterRegistry());
    }

    @BeforeEach
    void init() {
        taskRepository.deleteAll();
        final TaskService taskService = new TaskService(mongoTemplate, taskRepository, iexecHubService, applicationEventPublisher);
        taskUpdateManager = new TaskUpdateManager(taskService, iexecHubService, replicatesService, applicationEventPublisher,
                workerService, blockchainAdapterService, smsService);
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
        taskRepository.save(task);
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);

        taskUpdateManager.consensusReached2Reopening(task);

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(RECEIVED);
    }

    @Test
    void shouldNotUpgrade2ReopenedSinceNotAfterRevealDeadline() {
        final Task task = getStubTask(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() + 500));
        taskRepository.save(task);
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);

        taskUpdateManager.consensusReached2Reopening(task);

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
        verifyNoInteractions(iexecHubService);
    }

    @Test
    void shouldNotUpgrade2ReopenedSinceNotWeHaveSomeRevealed() {
        final Task task = getStubTask(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        taskRepository.save(task);
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(1);

        taskUpdateManager.consensusReached2Reopening(task);

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    @Test
    void shouldNotUpgrade2ReopenedBut2ReopenFailedSinceTxFailed() {
        final Task task = getStubTask(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        taskRepository.save(task);

        taskUpdateManager.consensusReached2Reopening(task);

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, FAILED, List.of(RECEIVED, CONSENSUS_REACHED, REOPENING, REOPEN_FAILED, FAILED));
    }

    // endregion

    // region received2Initializing

    @Test
    void shouldNotUpdateReceived2InitializingSinceCurrentStatusIsNotReceived() {
        final Task task = getStubTask(INITIALIZED);
        taskRepository.save(task);
        taskUpdateManager.received2Initializing(task);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    void shouldNotUpdateReceived2InitializingSinceNoSmsClient() {
        final Task task = getStubTask();
        task.setTag(TeeUtils.TEE_SCONE_ONLY_TAG);
        taskRepository.save(task);

        when(smsService.getVerifiedSmsUrl(CHAIN_TASK_ID, task.getTag())).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    void shouldUpdateInitializing2InitializeFailedSinceChainTaskIdIsEmpty() {
        final Task task = getStubTask();
        taskRepository.save(task);

        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(task.getChainTaskId());

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, FAILED, List.of(RECEIVED, INITIALIZE_FAILED, FAILED));
    }

    @Test
    void shouldUpdateInitializing2InitializeFailedSinceEnclaveChallengeIsEmpty() {
        final Task task = getStubTask();
        task.setTag(TEE_TAG);
        taskRepository.save(task);

        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.of(CHAIN_TASK_ID));
        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.of(true));
        when(smsService.getVerifiedSmsUrl(CHAIN_TASK_ID, TEE_TAG)).thenReturn(Optional.of(SMS_URL));
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, SMS_URL)).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(task.getChainTaskId());

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, FAILED, List.of(RECEIVED, INITIALIZING, INITIALIZE_FAILED, FAILED));
    }

    @Test
    void shouldUpdateReceived2Initializing2InitializedOnStandard() {
        final Task task = getStubTask();
        task.setTag(NO_TEE_TAG);
        taskRepository.save(task);

        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.of(CHAIN_TASK_ID));
        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.of(true));
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, null)).thenReturn(Optional.of(BytesUtils.EMPTY_ADDRESS));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getChainDealId()).isEqualTo(CHAIN_DEAL_ID);
        assertThatTaskContainsStatuses(resultTask, INITIALIZED, List.of(RECEIVED, INITIALIZING, INITIALIZED));
        assertThat(resultTask.getEnclaveChallenge()).isEqualTo(BytesUtils.EMPTY_ADDRESS);
        assertThat(resultTask.getSmsUrl()).isNull();
        verify(applicationEventPublisher).publishEvent(new TaskInitializedEvent(CHAIN_TASK_ID));
    }

    @Test
    void shouldUpdateReceived2Initializing2InitializedOnTee() {
        final Task task = getStubTask();
        final String tag = TeeUtils.TEE_GRAMINE_ONLY_TAG;
        task.setTag(tag);// Any TEE would be fine
        taskRepository.save(task);

        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.of(CHAIN_TASK_ID));
        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.of(true));
        when(smsService.getVerifiedSmsUrl(CHAIN_TASK_ID, tag)).thenReturn(Optional.of(SMS_URL));
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, SMS_URL)).thenReturn(Optional.of(BytesUtils.EMPTY_ADDRESS));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getChainDealId()).isEqualTo(CHAIN_DEAL_ID);
        assertThatTaskContainsStatuses(resultTask, INITIALIZED, List.of(RECEIVED, INITIALIZING, INITIALIZED));
        assertThat(resultTask.getEnclaveChallenge()).isEqualTo(BytesUtils.EMPTY_ADDRESS);
        assertThat(resultTask.getSmsUrl()).isEqualTo(SMS_URL);
        verify(applicationEventPublisher).publishEvent(new TaskInitializedEvent(CHAIN_TASK_ID));
    }

    @Test
    void shouldNotUpdateReceived2Initializing2InitializedOnTeeSinceCannotRetrieveSmsUrl() {
        final Task task = getStubTask();
        final String tag = TeeUtils.TEE_GRAMINE_ONLY_TAG;
        task.setTag(tag);// Any TEE would be fine
        taskRepository.save(task);

        when(smsService.getVerifiedSmsUrl(CHAIN_TASK_ID, tag)).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getChainDealId()).isEqualTo(CHAIN_DEAL_ID);
        assertThatTaskContainsStatuses(resultTask, FAILED, List.of(RECEIVED, INITIALIZE_FAILED, FAILED));
        assertThat(resultTask.getEnclaveChallenge()).isNull();
        assertThat(resultTask.getSmsUrl()).isNull();
        verify(smsService, times(1)).getVerifiedSmsUrl(CHAIN_TASK_ID, tag);
        verify(smsService, times(0)).getEnclaveChallenge(anyString(), anyString());
    }

    // endregion

    // region initializing2Initialized

    @Test
    void shouldUpdateInitializing2Initialized() {
        final Task task = getStubTask(INITIALIZING);
        taskRepository.save(task);

        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.of(true));
        when(smsService.getEnclaveChallenge(CHAIN_TASK_ID, null)).thenReturn(Optional.of(BytesUtils.EMPTY_ADDRESS));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, INITIALIZED, List.of(RECEIVED, INITIALIZING, INITIALIZED));
    }

    @Test
    void shouldNotUpdateInitializing2InitializedSinceNotInitializing(CapturedOutput output) {
        final Task task = getStubTask();
        taskRepository.save(task);
        taskUpdateManager.initializing2Initialized(task);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(RECEIVED);
        assertThat(output.getOut()).contains(String.format(ERROR_MSG, CHAIN_TASK_ID, RECEIVED, INITIALIZING, "initializing2Initialized"));
    }

    @Test
    void shouldNotUpdateInitializing2InitializedSinceNotInitialized() {
        final Task task = getStubTask(INITIALIZING);
        taskRepository.save(task);

        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.of(false));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, FAILED, List.of(RECEIVED, INITIALIZING, INITIALIZE_FAILED, FAILED));
    }

    @Test
    void shouldNotUpdateInitializing2InitializedSinceFailedToCheck() {
        final Task task = getStubTask(INITIALIZING);
        taskRepository.save(task);

        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, INITIALIZING, List.of(RECEIVED, INITIALIZING));
    }

    // endregion

    // region initialized2Running

    @Test
    void shouldUpdateInitialized2Running() { // 1 RUNNING out of 2
        final Task task = getStubTask(INITIALIZED);
        taskRepository.save(task);

        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.STARTED, ReplicateStatusModifier.WORKER);
        final List<Replicate> replicates = List.of(replicate);

        mockChainTask();
        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(replicates);

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    void shouldNotUpdateInitialized2RunningSinceNotInitialized(CapturedOutput output) {
        final Task task = getStubTask(INITIALIZING);
        taskRepository.save(task);
        taskUpdateManager.initialized2Running(ChainTask.builder().build(), task);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(INITIALIZING);
        assertThat(output.getOut()).contains(String.format(ERROR_MSG, CHAIN_TASK_ID, INITIALIZING, INITIALIZED, "initialized2Running"));
    }

    @Test
    void shouldNotUpdateInitialized2RunningSinceNoReplicates() {
        final Task task = getStubTask(INITIALIZED);
        taskRepository.save(task);

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    void shouldNotUpdateToRunningSinceAllReplicatesInCreated() {
        final Task task = getStubTask(INITIALIZED);
        taskRepository.save(task);

        final List<Replicate> replicates = List.of(
                new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID), new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID));

        mockChainTask();
        when(replicatesService.getReplicates(task.getChainTaskId())).thenReturn(replicates);

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isNotEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void shouldNotUpdateInitialized2ContributionTimeoutSinceChainTaskIsNotActive() {
        Date timeoutInPast = Date.from(Instant.now().minus(1L, ChronoUnit.MINUTES));

        final Task task = getStubTask(INITIALIZED);
        task.setContributionDeadline(timeoutInPast);
        taskRepository.save(task);

        ChainTask chainTask = ChainTask.builder()
                .contributionDeadline(timeoutInPast.getTime())
                .status(ChainTaskStatus.REVEALING)
                .build();

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(chainTask));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, INITIALIZED, List.of(RECEIVED, INITIALIZED));
    }

    @Test
    void shouldUpdateFromInitialized2ContributionTimeout() {
        Date timeoutInPast = Date.from(Instant.now().minus(1L, ChronoUnit.MINUTES));

        final Task task = getStubTask(INITIALIZED);
        task.setContributionDeadline(timeoutInPast);
        taskRepository.save(task);

        ChainTask chainTask = ChainTask.builder()
                .contributionDeadline(timeoutInPast.getTime())
                .status(ChainTaskStatus.ACTIVE)
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, FAILED, List.of(RECEIVED, INITIALIZED, CONTRIBUTION_TIMEOUT, FAILED));
    }

    // endregion

    // region initializedOrRunning2ContributionTimeout

    // TODO check this, it may not be this true
    @Test
    void shouldNotReSendNotificationWhenAlreadyInContributionTimeout() {
        final Date timeoutInPast = Date.from(Instant.now().minus(1L, ChronoUnit.MINUTES));

        final Task task = getStubTask(CONTRIBUTION_TIMEOUT);
        task.setContributionDeadline(timeoutInPast);
        taskRepository.save(task);

        final ChainTask chainTask = ChainTask.builder()
                .contributionDeadline(timeoutInPast.getTime())
                .status(ChainTaskStatus.ACTIVE)
                .build();

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(chainTask));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        verify(applicationEventPublisher).publishEvent(any(TaskStatusesCountUpdatedEvent.class));
        verify(applicationEventPublisher).publishEvent(any(TaskFailedEvent.class));
    }

    // endregion

    // region transitionFromRunningState

    @Test
    void shouldNotTransitionFromRunningStateWhenTaskNotRunning() {
        final Task task = getStubTask(INITIALIZED);
        task.setTag(TeeUtils.TEE_SCONE_ONLY_TAG);
        taskRepository.save(task);

        taskUpdateManager.transitionFromRunningState(ChainTask.builder().build(), task);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    void shouldTransitionFromRunningToContributionTimeout() {
        Date timeoutInPast = Date.from(Instant.now().minus(1L, ChronoUnit.MINUTES));

        final Task task = getStubTask(RUNNING);
        task.setContributionDeadline(timeoutInPast);
        taskRepository.save(task);

        ChainTask chainTask = ChainTask.builder()
                .contributionDeadline(timeoutInPast.getTime())
                .status(ChainTaskStatus.ACTIVE)
                .build();

        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, FAILED, List.of(RECEIVED, RUNNING, CONTRIBUTION_TIMEOUT, FAILED));
    }

    @Test
    void shouldNotTransitionFromRunningStateWhenNoReplicates() {
        final Task task = getStubTask(RUNNING);
        task.setTag(TeeUtils.TEE_SCONE_ONLY_TAG);
        taskRepository.save(task);

        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        taskUpdateManager.transitionFromRunningState(
                ChainTask.builder().chainTaskId(CHAIN_TASK_ID).status(ChainTaskStatus.ACTIVE).build(), task);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    void shouldNotUpdateRunning2Finalized2CompletedWhenNoReplicatesOnContributeAndFinalizeStatus() {
        final Task task = getStubTask(RUNNING);
        task.setTag(TeeUtils.TEE_SCONE_ONLY_TAG);
        taskRepository.save(task);

        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID);
        final List<Worker> workersList = List.of(Worker.builder().walletAddress(WALLET_WORKER_1).build());

        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(workerService.getAliveWorkers()).thenReturn(workersList);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.transitionFromRunningState(
                ChainTask.builder().chainTaskId(CHAIN_TASK_ID).status(ChainTaskStatus.ACTIVE).build(), task);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    void shouldNotUpdateRunning2Finalized2CompletedWhenMoreThanOneReplicatesOnContributeAndFinalizeStatus() {
        final Task task = getStubTask(RUNNING);
        task.setTag(TeeUtils.TEE_SCONE_ONLY_TAG);
        taskRepository.save(task);

        final ReplicatesList replicatesList = spy(new ReplicatesList(CHAIN_TASK_ID));

        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(replicatesList.getNbReplicatesWithCurrentStatus(ReplicateStatus.CONTRIBUTE_AND_FINALIZE_DONE)).thenReturn(2);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.transitionFromRunningState(
                ChainTask.builder().chainTaskId(CHAIN_TASK_ID).status(ChainTaskStatus.ACTIVE).build(), task);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    void shouldUpdateRunning2ConsensusReached(final CapturedOutput output) {
        final Instant start = Instant.now();
        final Task task = getStubTask(RUNNING);
        taskRepository.save(task);

        final ReplicatesList replicatesList = spy(new ReplicatesList(CHAIN_TASK_ID));

        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .revealDeadline(Instant.now().toEpochMilli())
                .winnerCounter(2)
                .build()));
        when(iexecHubService.getConsensusBlock(anyString(), anyLong())).thenReturn(ChainReceipt.builder().blockNumber(1L).build());
        when(replicatesList.getNbValidContributedWinners(any())).thenReturn(2);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
        assertThat(resultTask.getConsensusReachedBlockNumber()).isOne();
        assertThat(resultTask.getRevealDeadline()).isBetween(Date.from(start), task.getFinalDeadline());
        assertThat(output.getOut()).contains(
                String.format("Task eligibility to contributeAndFinalize flow [chainTaskId:%s, contributeAndFinalize:%s]",
                        CHAIN_TASK_ID, false));
    }

    @Test
    void shouldUpdateTaskRunning2Finalized2Completed(final CapturedOutput output) {
        final Instant start = Instant.now();
        final Task task = getStubTask(RUNNING);
        task.setTag(TeeUtils.TEE_SCONE_ONLY_TAG);
        taskRepository.save(task);

        final ReplicatesList replicatesList = spy(new ReplicatesList(CHAIN_TASK_ID));

        mockChainTask();
        when(replicatesService.getReplicatesList(CHAIN_TASK_ID)).thenReturn(Optional.of(replicatesList));
        when(replicatesList.getNbReplicatesWithCurrentStatus(ReplicateStatus.CONTRIBUTE_AND_FINALIZE_DONE)).thenReturn(1);
        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.COMPLETED)
                .revealDeadline(Instant.now().toEpochMilli())
                .build()));
        when(iexecHubService.getConsensusBlock(anyString(), anyLong())).thenReturn(ChainReceipt.builder().blockNumber(1L).build());
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, COMPLETED, List.of(RECEIVED, RUNNING, FINALIZED, COMPLETED));
        assertThat(resultTask.getConsensusReachedBlockNumber()).isOne();
        assertThat(resultTask.getRevealDeadline()).isBetween(Date.from(start), task.getFinalDeadline());
        assertThat(output.getOut()).contains(
                String.format("Task eligibility to contributeAndFinalize flow [chainTaskId:%s, contributeAndFinalize:%s]",
                        CHAIN_TASK_ID, true));
    }

    @Test
    void shouldNotUpdateRunning2ConsensusReachedSinceWrongTaskStatus() {
        final Task task = getStubTask(INITIALIZED);
        taskRepository.save(task);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(2)
                .build()));

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    void shouldNotUpdateRunning2ConsensusReachedSinceCannotGetChainTask() {
        final Task task = getStubTask(RUNNING);
        taskRepository.save(task);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    void shouldNotUpdateRunning2ConsensusReachedSinceOnChainStatusNotRevealing() {
        final Task task = getStubTask(RUNNING);
        taskRepository.save(task);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.UNSET)
                .winnerCounter(2)
                .build()));

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    void shouldNotUpdateRunning2ConsensusReachedSinceWinnerContributorsDiffers() {
        final Task task = getStubTask(RUNNING);
        taskRepository.save(task);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(2)
                .build()));

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    void shouldUpdateRunning2RunningFailedOn1Worker() {
        final Task task = getStubTask(RUNNING);
        task.setTag(TEE_TAG);
        taskRepository.save(task);

        // 1 replicate has tried to run the task:
        // - R1 is in COMPUTE_FAILED status
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.setStatusUpdateList(new ArrayList<>());
        replicate1.updateStatus(ReplicateStatus.COMPUTE_FAILED, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList(CHAIN_TASK_ID);
        replicatesList.setReplicates(List.of(replicate1));
        final List<Worker> workersList = replicatesList
                .getReplicates()
                .stream()
                .map(r -> Worker.builder().walletAddress(r.getWalletAddress()).build())
                .toList();

        mockChainTask();
        when(replicatesService.getReplicatesList(task.getChainTaskId())).thenReturn(Optional.of(replicatesList));
        when(workerService.getAliveWorkers()).thenReturn(workersList);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, FAILED, List.of(RECEIVED, RUNNING, RUNNING_FAILED, FAILED));
    }

    @Test
    void shouldUpdateRunning2RunningFailedOn2Workers() {
        final Task task = getStubTask(RUNNING);
        task.setTag(TEE_TAG);
        taskRepository.save(task);

        // 2 replicates have tried to run the task:
        // - R1 is in COMPUTE_FAILED status
        // - R2 is in APP_DOWNLOAD_FAILED status
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
                .toList();

        mockChainTask();
        when(replicatesService.getReplicatesList(task.getChainTaskId())).thenReturn(Optional.of(replicatesList));
        when(workerService.getAliveWorkers()).thenReturn(workersList);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, FAILED, List.of(RECEIVED, RUNNING, RUNNING_FAILED, FAILED));
    }

    @Test
    void shouldNotUpdateRunning2RunningFailedOn1WorkerAsNonTeeTask() {
        final Task task = getStubTask(RUNNING);
        task.setTag(NO_TEE_TAG);
        taskRepository.save(task);

        // 1 replicate has tried to run the task:
        // - R1 is in COMPUTE_FAILED status
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.setStatusUpdateList(new ArrayList<>());
        replicate1.updateStatus(ReplicateStatus.COMPUTE_FAILED, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList();
        replicatesList.setReplicates(List.of(replicate1));

        mockChainTask();
        when(replicatesService.getReplicatesList(task.getChainTaskId())).thenReturn(Optional.of(replicatesList));
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask).usingRecursiveComparison().isEqualTo(task);
    }

    @Test
    void shouldNotUpdateRunning2RunningFailedOn2WorkersAsNonTeeTask() {
        final Task task = getStubTask(RUNNING);
        task.setTag(NO_TEE_TAG);
        taskRepository.save(task);

        // 2 replicates have tried to run the task:
        // - R1 is in COMPUTE_FAILED status
        // - R2 is in APP_DOWNLOAD_FAILED status
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.setStatusUpdateList(new ArrayList<>());
        replicate1.updateStatus(ReplicateStatus.COMPUTE_FAILED, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.setStatusUpdateList(new ArrayList<>());
        replicate2.updateStatus(ReplicateStatus.APP_DOWNLOAD_FAILED, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList();
        replicatesList.setReplicates(List.of(replicate1, replicate2));

        mockChainTask();
        when(replicatesService.getReplicatesList(task.getChainTaskId())).thenReturn(Optional.of(replicatesList));
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, RUNNING, List.of(RECEIVED, RUNNING));
    }

    @Test
    void shouldNotUpdateRunning2AllWorkersFailedSinceOneStillComputing() {
        final Task task = getStubTask(RUNNING);
        task.setTag(TEE_TAG);
        taskRepository.save(task);

        // 2 replicates have tried to run the task:
        // - R1 is in COMPUTE_FAILED status
        // - R2 is in COMPUTING` status
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
                .toList();

        mockChainTask();
        when(replicatesService.getReplicatesList(task.getChainTaskId())).thenReturn(Optional.of(replicatesList));
        when(workerService.getAliveWorkers()).thenReturn(workersList);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, RUNNING, List.of(RECEIVED, RUNNING));
    }

    @Test
    void shouldNotUpdateRunning2AllWorkersFailedSinceOneHasReachedComputed() {
        final Task task = getStubTask(RUNNING);
        task.setTag(TEE_TAG);
        taskRepository.save(task);

        // 2 replicates have tried to run the task:
        // - R1 is in COMPUTE_FAILED status
        // - R2 is in CONTRIBUTE_FAILED` status
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

        mockChainTask();
        when(replicatesService.getReplicatesList(task.getChainTaskId())).thenReturn(Optional.of(replicatesList));
        when(workerService.getAliveWorkers()).thenReturn(workersList);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, RUNNING, List.of(RECEIVED, RUNNING));
    }

    @Test
    void shouldNotUpdateRunning2AllWorkersFailedSinceOneStillHasToBeLaunched() {
        final Task task = getStubTask(RUNNING);
        task.setTag(TEE_TAG);
        taskRepository.save(task);

        // 1 replicates have tried to run the task and 1 is still to be run:
        // - R1 is in COMPUTE_FAILED status
        // - R2 has not started yet
        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.setStatusUpdateList(new ArrayList<>());
        replicate1.updateStatus(ReplicateStatus.COMPUTE_FAILED, ReplicateStatusModifier.WORKER);

        final ReplicatesList replicatesList = new ReplicatesList();
        replicatesList.setReplicates(List.of(replicate1));
        final List<Worker> workersList = List.of(
                Worker.builder().walletAddress(WALLET_WORKER_1).build(),
                Worker.builder().walletAddress(WALLET_WORKER_2).build()
        );

        mockChainTask();
        when(replicatesService.getReplicatesList(task.getChainTaskId())).thenReturn(Optional.of(replicatesList));
        when(workerService.getAliveWorkers()).thenReturn(workersList);
        mockTaskDescriptionFromTask(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, RUNNING, List.of(RECEIVED, RUNNING));
    }

    // endregion

    // region consensusReached2AtLeastOneReveal2UploadRequested

    @Test
    void shouldUpdateConsensusReached2AtLeastOneReveal2Uploading() {
        final Task task = getStubTask(CONSENSUS_REACHED);
        taskRepository.save(task);
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        mockChainTask();
        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.REVEALED)).thenReturn(1);
        when(replicatesService.getRandomReplicateWithRevealStatus(task.getChainTaskId())).thenReturn(Optional.of(replicate));

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getUploadingWorkerWalletAddress()).isEqualTo(replicate.getWalletAddress());
        assertThatTaskContainsStatuses(resultTask, RESULT_UPLOADING, List.of(RECEIVED, CONSENSUS_REACHED, AT_LEAST_ONE_REVEALED, RESULT_UPLOADING));
    }

    @Test
    void shouldNotUpdateConsensusReached2AtLeastOneReveal2ResultUploadingSinceNotConsensusReached(CapturedOutput output) {
        final Task task = getStubTask(RUNNING);
        task.setChainTaskId(CHAIN_TASK_ID);
        taskRepository.save(task);
        taskUpdateManager.consensusReached2AtLeastOneReveal2ResultUploading(task);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(RUNNING);
        assertThat(output.getOut()).contains(String.format(ERROR_MSG, CHAIN_TASK_ID, RUNNING, CONSENSUS_REACHED, "consensusReached2AtLeastOneReveal2ResultUploading"));
    }

    @Test
    void shouldNotUpdateConsensusReached2AtLeastOneRevealSinceNoRevealedReplicate() {
        final Task task = getStubTask(CONSENSUS_REACHED);
        taskRepository.save(task);
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    // endregion

    // region requestUpload

    @Test
    void shouldRequestUploadAfterOneRevealed() {
        final Task task = getStubTask(AT_LEAST_ONE_REVEALED);
        taskRepository.save(task);

        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        mockChainTask();
        when(replicatesService.getRandomReplicateWithRevealStatus(task.getChainTaskId())).thenReturn(Optional.of(replicate));

        taskUpdateManager.updateTask(task.getChainTaskId());

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);
    }

    @Test
    void shouldNotRequestUploadAfterOneRevealedAsWorkerLost() {
        final Task task = getStubTask(AT_LEAST_ONE_REVEALED);
        taskRepository.save(task);

        taskUpdateManager.updateTask(task.getChainTaskId());

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(AT_LEAST_ONE_REVEALED);
    }

    // endregion

    // region reopening2Reopened

    @Test
    void shouldUpdateReopening2Reopened() {
        final Task task = getStubTask(REOPENING);
        taskRepository.save(task);

        final ChainTask chainTask = ChainTask
                .builder()
                .chainTaskId(CHAIN_TASK_ID)
                .status(ChainTaskStatus.ACTIVE)
                .build();

        final Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);
        final Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));
        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(List.of(replicate1, replicate2));

        taskUpdateManager.updateTask(task.getChainTaskId());

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getConsensus()).isNull();
        assertThat(resultTask.getRevealDeadline().toInstant()).isEqualTo(new Date(0).toInstant());
        assertThatTaskContainsStatuses(resultTask, INITIALIZED, List.of(RECEIVED, REOPENING, REOPENED, INITIALIZED));

        verify(replicatesService, times(2)).setRevealTimeoutStatusIfNeeded(eq(CHAIN_TASK_ID), any());
    }

    @Test
    void shouldNotUpdateReopening2ReopenedSinceUnknownChainTask() {
        final Task task = getStubTask(REOPENING);
        taskRepository.save(task);

        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(REOPENING);
        verify(replicatesService, times(0)).setRevealTimeoutStatusIfNeeded(eq(CHAIN_TASK_ID), any());
    }

    // endregion

    // region reopened2Initialized

    @Test
    void shouldUpdateReopened() {
        final Task task = getStubTask(REOPENED);
        taskRepository.save(task);

        mockChainTask();

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    // endregion

    // region resultUploading2Uploaded2Finalizing2Finalized

    @Test
    void shouldUpdateResultUploading2Uploaded2Finalizing2Finalized() { //one worker uploaded
        final Task task = getStubTask(RESULT_UPLOADING);
        taskRepository.save(task);

        final ChainTask chainTask = ChainTask.builder().revealCounter(1).build();
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setResultLink(RESULT_LINK);

        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.of(replicate));
        when(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(1);
        when(iexecHubService.getChainTask(any())).thenReturn(Optional.of(chainTask));
        when(blockchainAdapterService.requestFinalize(any(), any(), any())).thenReturn(Optional.of(CHAIN_TASK_ID));
        when(blockchainAdapterService.isFinalized(any())).thenReturn(Optional.of(true));
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.COMPLETED)
                .revealCounter(1)
                .build()));

        taskUpdateManager.updateTask(task.getChainTaskId());

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, COMPLETED, List.of(RECEIVED, RESULT_UPLOADING, RESULT_UPLOADED, FINALIZING, FINALIZED, COMPLETED));
    }

    @Test
    void shouldUpdateResultUploading2Uploaded2Finalizing2FinalizeFail() { //one worker uploaded && finalize FAIL
        final Task task = getStubTask(RESULT_UPLOADING);
        taskRepository.save(task);
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setResultLink(RESULT_LINK);
        replicate.updateStatus(ReplicateStatus.RESULT_UPLOADED, ReplicateStatusModifier.POOL_MANAGER);

        when(replicatesService.getReplicateWithResultUploadedStatus(task.getChainTaskId())).thenReturn(Optional.of(replicate));
        when(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(1);
        when(blockchainAdapterService.requestFinalize(any(), any(), any())).thenReturn(Optional.empty());
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.FAILED)
                .revealCounter(1)
                .build()));

        taskUpdateManager.updateTask(task.getChainTaskId());

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, FAILED, List.of(RECEIVED, RESULT_UPLOADING, RESULT_UPLOADED, FINALIZE_FAILED, FAILED));
    }

    @Test
    void shouldUpdateResultUploading2UploadedFailAndRequestUploadAgain() {
        final Task task = getStubTask(RESULT_UPLOADING);
        taskRepository.save(task);

        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        mockChainTask();
        when(replicatesService.getRandomReplicateWithRevealStatus(task.getChainTaskId())).thenReturn(Optional.of(replicate));

        taskUpdateManager.updateTask(task.getChainTaskId());

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getUploadingWorkerWalletAddress()).isEqualTo(replicate.getWalletAddress());
        assertThatTaskContainsStatuses(resultTask, RESULT_UPLOADING, List.of(RECEIVED, RESULT_UPLOADING, RESULT_UPLOADING));

        verify(replicatesService).updateReplicateStatus(eq(CHAIN_TASK_ID), eq(WALLET_WORKER_1), statusUpdate.capture());
        verify(applicationEventPublisher).publishEvent(any(PleaseUploadEvent.class));
        assertThat(statusUpdate.getValue().getStatus()).isEqualTo(RESULT_UPLOAD_REQUESTED);
    }

    @Test
    void shouldNotUpdateResultUploading2UploadedSinceNotResultUploading(CapturedOutput output) {
        final Task task = getStubTask(CONSENSUS_REACHED);
        taskRepository.save(task);
        taskUpdateManager.resultUploading2Uploaded(ChainTask.builder().build(), task);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
        assertThat(output.getOut()).contains(String.format(ERROR_MSG, CHAIN_TASK_ID, CONSENSUS_REACHED, RESULT_UPLOADING, "resultUploading2Uploaded"));
    }

    @Test
    void shouldNotUpdateResultUploading2UploadedSinceNoUploadingReplicate() {
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);

        final Task task = getStubTask(RESULT_UPLOADING);
        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);
        taskRepository.save(task);

        mockChainTask();
        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.empty());
        when(replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID)).thenReturn(Optional.of(replicate));

        taskUpdateManager.updateTask(task.getChainTaskId());

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);

        verify(replicatesService).updateReplicateStatus(eq(CHAIN_TASK_ID), eq(WALLET_WORKER_1), statusUpdate.capture());
        assertThat(statusUpdate.getValue().getStatus()).isEqualTo(RESULT_UPLOAD_REQUESTED);
    }

    @Test
    void shouldNotUpdateResultUploading2UploadedSinceUploadingReplicateInUploadingStatus() {
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatusUpdate.builder().status(ReplicateStatus.RESULT_UPLOADING).build());

        final Task task = getStubTask(RESULT_UPLOADING);
        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);
        taskRepository.save(task);

        taskUpdateManager.updateTask(task.getChainTaskId());

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);
    }

    @Test
    void shouldNotUpdateResultUploading2UploadedSinceUploadingReplicateInRecoveringUploadingStatus() {
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatusUpdate.builder().status(ReplicateStatus.RESULT_UPLOADING).build());
        replicate.updateStatus(ReplicateStatusUpdate.builder().status(ReplicateStatus.RECOVERING).build());

        final Task task = getStubTask(RESULT_UPLOADING);
        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);
        taskRepository.save(task);

        taskUpdateManager.updateTask(task.getChainTaskId());

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);
    }

    @Test
    void shouldNotUpdateResultUploading2UploadedSinceUploadingReplicateNotInUploadingNorRecoveringUploadingStatus() {
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatusUpdate.builder().status(ReplicateStatus.REVEALED).build());

        final Task task = getStubTask(RESULT_UPLOADING);
        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);
        taskRepository.save(task);

        mockChainTask();
        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(replicate));
        when(replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID)).thenReturn(Optional.of(replicate));

        taskUpdateManager.updateTask(task.getChainTaskId());

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);

        verify(replicatesService).updateReplicateStatus(eq(CHAIN_TASK_ID), eq(WALLET_WORKER_1), statusUpdate.capture());
        assertThat(statusUpdate.getValue().getStatus()).isEqualTo(RESULT_UPLOAD_REQUESTED);
    }

    @Test
    void shouldUpdateResultUploading2UploadTimeout() {
        final Task task = getStubTask(RESULT_UPLOADING);
        task.setFinalDeadline(new Date(0));
        taskRepository.save(task);

        taskUpdateManager.resultUploading2Uploaded(ChainTask.builder().build(), task);

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getDateStatusList().get(resultTask.getDateStatusList().size() - 2).getStatus()).isEqualTo(RESULT_UPLOAD_TIMEOUT);
        assertThat(resultTask.getCurrentStatus()).isEqualTo(TaskStatus.FAILED);
    }

    // endregion

    // region resultUploaded2Finalizing

    @Test
    void shouldUpdateResultUploaded2Finalizing() {
        final Task task = getStubTask(RESULT_UPLOADED);
        taskRepository.save(task);

        final ChainTask chainTask = ChainTask.builder().revealCounter(1).build();
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setResultLink(RESULT_LINK);

        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));
        when(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(1);
        when(blockchainAdapterService.requestFinalize(any(), any(), any())).thenReturn(Optional.of(CHAIN_TASK_ID));

        when(blockchainAdapterService.isFinalized(any())).thenReturn(Optional.of(true));

        taskUpdateManager.updateTask(task.getChainTaskId());

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, COMPLETED, List.of(RECEIVED, RESULT_UPLOADED, FINALIZING, FINALIZED, COMPLETED));
    }

    @Test
    void shouldNotUpdateResultUploaded2FinalizingSinceNotResultUploaded(CapturedOutput output) {
        final Task task = getStubTask(RESULT_UPLOADING);
        taskRepository.save(task);
        taskUpdateManager.resultUploaded2Finalizing(ChainTask.builder().build(), task);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);
        assertThat(output.getOut()).contains(String.format(ERROR_MSG, CHAIN_TASK_ID, RESULT_UPLOADING, RESULT_UPLOADED, "resultUploaded2Finalizing"));
    }

    @Test
    void shouldNotUpdateResultUploaded2FinalizingSinceRevealsNotEqual() {
        final Task task = getStubTask(RESULT_UPLOADED);
        taskRepository.save(task);

        final ChainTask chainTask = ChainTask.builder().revealCounter(1).build();
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setResultLink(RESULT_LINK);

        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));
        when(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(2);

        taskUpdateManager.updateTask(task.getChainTaskId());

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(RESULT_UPLOADED);
    }

    @Test
    void shouldNotUpdateResultUploaded2FinalizingSinceCantRequestFinalize() {
        final Task task = getStubTask(RESULT_UPLOADED);
        taskRepository.save(task);

        final ChainTask chainTask = ChainTask.builder().revealCounter(1).build();
        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.setResultLink(RESULT_LINK);

        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));
        when(replicatesService.getNbReplicatesContainingStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(1);
        when(blockchainAdapterService.requestFinalize(eq(CHAIN_TASK_ID), any(), any())).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(task.getChainTaskId());

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, FAILED, List.of(RECEIVED, RESULT_UPLOADED, FINALIZE_FAILED, FAILED));
    }

    // endregion

    // region finalizing2Finalized

    @Test
    void shouldUpdateFinalized2Completed() {
        final Task task = getStubTask(FINALIZED);
        taskRepository.save(task);

        mockChainTask();

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(COMPLETED);
    }

    @Test
    void shouldUpdateFinalizing2FinalizeFailedSinceNotFinalized() {
        final Task task = getStubTask(FINALIZING);
        taskRepository.save(task);

        mockChainTask();
        when(blockchainAdapterService.isFinalized(CHAIN_TASK_ID)).thenReturn(Optional.of(false));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, FAILED, List.of(RECEIVED, FINALIZING, FINALIZE_FAILED, FAILED));
    }

    // endregion

    // region finalizingToFinalizedToCompleted

    @Test
    void shouldUpdateFinalizing2Finalized2Completed() {
        final Task task = getStubTask(FINALIZING);
        taskRepository.save(task);

        mockChainTask();
        when(blockchainAdapterService.isFinalized(CHAIN_TASK_ID)).thenReturn(Optional.of(true));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, COMPLETED, List.of(RECEIVED, FINALIZING, FINALIZED, COMPLETED));
    }

    @Test
    void shouldNotUpdateFinalizing2Finalized2CompletedSinceNotFinalizing(CapturedOutput output) {
        final Task task = getStubTask(RESULT_UPLOADED);
        taskRepository.save(task);
        taskUpdateManager.finalizing2Finalized2Completed(task);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(RESULT_UPLOADED);
        assertThat(output.getOut()).contains(String.format(ERROR_MSG, CHAIN_TASK_ID, RESULT_UPLOADED, FINALIZING, "finalizing2Finalized2Completed"));
    }

    @Test
    void shouldNotUpdateFinalizing2FinalizedSinceFailedToCheck() {
        final Task task = getStubTask(FINALIZING);
        taskRepository.save(task);

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, FINALIZING, List.of(RECEIVED, FINALIZING));
    }

    // endregion

    // region finalizedToCompleted

    @Test
    void shouldNotUpdateFinalized2CompletedSinceNotFinalized(CapturedOutput output) {
        final Task task = getStubTask(FINALIZING);
        taskRepository.save(task);
        taskUpdateManager.finalizedToCompleted(task);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(FINALIZING);
        assertThat(output.getOut()).contains(String.format(ERROR_MSG, CHAIN_TASK_ID, FINALIZING, FINALIZED, "finalizedToCompleted"));
    }

    // endregion

    // 3 replicates in RUNNING 0 in COMPUTED
    @Test
    void shouldUpdateTaskToRunningFromWorkersInRunning() {
        final Task task = getStubTask(INITIALIZED);
        taskRepository.save(task);

        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.APP_DOWNLOADING, ReplicateStatusModifier.WORKER);
        final List<Replicate> replicates = List.of(replicate);

        mockChainTask();
        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(replicates);

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    // 2 replicates in RUNNING and 2 in COMPUTED
    @Test
    void shouldUpdateTaskToRunningFromWorkersInRunningAndComputed() {
        final Task task = getStubTask(INITIALIZED);
        taskRepository.save(task);

        final Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);
        final List<Replicate> replicates = List.of(replicate);

        mockChainTask();
        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(replicates);

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    // Two replicates in COMPUTED BUT numWorkersNeeded = 2, so the task should not be able to move directly from
    // INITIALIZED to COMPUTED
    @Test
    void shouldNotUpdateToRunningCase2() {
        final Task task = getStubTask(INITIALIZED);
        taskRepository.save(task);

        mockChainTask();

        taskUpdateManager.updateTask(task.getChainTaskId());
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isNotEqualTo(TaskStatus.RUNNING);
    }

    @Test
    void shouldUpdateFromAnyInProgressStatus2FinalDeadlineReached() {
        final Task task = getStubTask();
        task.setFinalDeadline(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        taskRepository.save(task);

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThatTaskContainsStatuses(resultTask, FAILED, List.of(RECEIVED, FINAL_DEADLINE_REACHED, FAILED));
    }

    @Test
    void shouldUpdateFromFinalDeadlineReached2Failed() {
        final Task task = getStubTask(FINAL_DEADLINE_REACHED);
        task.setFinalDeadline(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        taskRepository.save(task);

        mockChainTask();

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    void shouldNotUpdateToFinalDeadlineReachedIfAlreadyFailed() {
        final Task task = getStubTask(FAILED);
        task.setFinalDeadline(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        taskRepository.save(task);

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    void shouldNotUpdateToFinalDeadlineReachedIfAlreadyCompleted() {
        final Task task = getStubTask(COMPLETED);
        task.setFinalDeadline(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        taskRepository.save(task);

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(COMPLETED);
    }

    // Tests on toFailed() automatic transitions
    @Test
    void shouldUpdateToFailed() {
        mockChainTask();
        final TaskStatus[] statusThatAutomaticallyFail = new TaskStatus[]{
                INITIALIZE_FAILED,
                RUNNING_FAILED,
                CONTRIBUTION_TIMEOUT,
                REOPEN_FAILED,
                RESULT_UPLOAD_TIMEOUT,
                FINALIZE_FAILED};

        for (TaskStatus status : statusThatAutomaticallyFail) {
            final Task task = getStubTask(status);
            taskRepository.save(task);

            taskUpdateManager.updateTask(task.getChainTaskId());

            final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
            assertThat(resultTask.getCurrentStatus()).isEqualTo(FAILED);
            taskRepository.delete(resultTask);
        }
    }

    // region requestUpload

    @Test
    void shouldRequestUpload() {
        final Task task = getStubTask();
        task.setChainTaskId(CHAIN_TASK_ID);
        taskRepository.save(task);

        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(replicatesService.getNbReplicatesWithCurrentStatus(
                CHAIN_TASK_ID,
                ReplicateStatus.RESULT_UPLOADING,
                RESULT_UPLOAD_REQUESTED)
        ).thenReturn(0);
        when(replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID)).thenReturn(Optional.of(replicate));

        taskUpdateManager.requestUpload(task);

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);
        verify(replicatesService).updateReplicateStatus(eq(CHAIN_TASK_ID), eq(WALLET_WORKER_1), statusUpdate.capture());
        verify(applicationEventPublisher).publishEvent(any(PleaseUploadEvent.class));
        assertThat(statusUpdate.getValue().getStatus()).isEqualTo(RESULT_UPLOAD_REQUESTED);
    }

    @Test
    void shouldNotRequestUpload() {
        final Task task = getStubTask(AT_LEAST_ONE_REVEALED);
        taskRepository.save(task);

        when(replicatesService.getNbReplicatesWithCurrentStatus(
                CHAIN_TASK_ID,
                ReplicateStatus.RESULT_UPLOADING,
                RESULT_UPLOAD_REQUESTED)
        ).thenReturn(0);
        // For example, this could happen if replicate is lost after having revealed.
        when(replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        taskUpdateManager.requestUpload(task);

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(AT_LEAST_ONE_REVEALED);
        verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class));
        verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any(PleaseUploadEvent.class));
    }

    @Test
    void shouldNotRequestUploadSinceUploadInProgress() {
        final Task task = getStubTask(AT_LEAST_ONE_REVEALED);
        task.setChainTaskId(CHAIN_TASK_ID);
        taskRepository.save(task);

        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(replicatesService.getNbReplicatesWithCurrentStatus(
                CHAIN_TASK_ID,
                ReplicateStatus.RESULT_UPLOADING,
                RESULT_UPLOAD_REQUESTED)
        ).thenReturn(1);

        taskUpdateManager.requestUpload(task);

        final Task resultTask = taskRepository.findByChainTaskId(task.getChainTaskId()).orElseThrow();
        assertThat(resultTask.getCurrentStatus()).isEqualTo(AT_LEAST_ONE_REVEALED);
        verify(replicatesService, Mockito.times(0))
                .getRandomReplicateWithRevealStatus(CHAIN_TASK_ID);
        verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(anyString(), anyString(), any(ReplicateStatusUpdate.class));
        verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any(PleaseUploadEvent.class));
    }

    // endregion

    // region utils
    private static void assertThatTaskContainsStatuses(Task task, TaskStatus currentStatus, List<TaskStatus> changesList) {
        assertThat(task.getCurrentStatus()).isEqualTo(currentStatus);
        assertThat(task.getDateStatusList().stream().map(TaskStatusChange::getStatus).toList()).isEqualTo(changesList);
    }

    private void mockChainTask() {
        final ChainTask chainTask = ChainTask.builder().chainTaskId(CHAIN_TASK_ID).status(ChainTaskStatus.ACTIVE).build();
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));
    }

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
