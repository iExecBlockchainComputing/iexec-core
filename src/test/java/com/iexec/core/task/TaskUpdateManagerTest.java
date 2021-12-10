/*
 * Copyright 2021 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.task;

import com.iexec.common.chain.*;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.common.utils.DateTimeUtils;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.chain.adapter.BlockchainAdapterService;
import com.iexec.core.configuration.ResultRepositoryConfiguration;
import com.iexec.core.detector.replicate.RevealTimeoutDetector;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesList;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.event.PleaseUploadEvent;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.iexec.common.utils.DateTimeUtils.sleep;
import static com.iexec.core.task.TaskStatus.*;
import static com.iexec.core.task.TaskTestsUtils.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TaskUpdateManagerTest {
    private final long maxExecutionTime = 60000;

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
    private TaskRepository taskRepository;

    @Mock
    private TaskService taskService;

    @Mock
    private TaskUpdateRequestManager taskUpdateRequestManager;

    @InjectMocks
    private TaskUpdateManager taskUpdateManager;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    // Tests on consensusReached2Reopening transition

    @Test
    public void shouldNotUpgrade2ReopenedSinceCurrentStatusWrong() {
        Task task = getStubTask(maxExecutionTime);

        task.changeStatus(RECEIVED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.of(new ChainReceipt()));

        taskUpdateManager.consensusReached2Reopening(task);

        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
    }

    @Test
    public void shouldNotUpgrade2ReopenedSinceNotAfterRevealDeadline() {
        Task task = getStubTask(maxExecutionTime);

        task.changeStatus(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() + 100));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.of(new ChainReceipt()));

        taskUpdateManager.consensusReached2Reopening(task);

        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    @Test
    public void shouldNotUpgrade2ReopenedSinceNotWeHaveSomeRevealed() {
        Task task = getStubTask(maxExecutionTime);

        task.changeStatus(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(1);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.of(new ChainReceipt()));

        taskUpdateManager.consensusReached2Reopening(task);

        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    @Test
    public void shouldNotUpgrade2ReopenedSinceCantReopenOnChain() {
        Task task = getStubTask(maxExecutionTime);

        task.changeStatus(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(false);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.of(new ChainReceipt()));

        taskUpdateManager.consensusReached2Reopening(task);

        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    @Test
    public void shouldNotUpgrade2ReopenedSinceNotEnoughtGas() {
        Task task = getStubTask(maxExecutionTime);

        task.changeStatus(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(false);
        when(taskRepository.save(task)).thenReturn(task);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.of(new ChainReceipt()));

        taskUpdateManager.consensusReached2Reopening(task);

        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }


    @Test
    public void shouldNotUpgrade2ReopenedBut2ReopendedFailedSinceTxFailed() {
        Task task = getStubTask(maxExecutionTime);

        task.changeStatus(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.empty());

        taskUpdateManager.consensusReached2Reopening(task);

        assertThat(task.getLastButOneStatus()).isEqualTo(REOPEN_FAILED);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    //TODO: Update reopen call
    //@Test
    public void shouldUpgrade2Reopened() {
        Task task = getStubTask(maxExecutionTime);

        task.changeStatus(CONSENSUS_REACHED);
        task.setRevealDeadline(new Date(new Date().getTime() - 10));
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.REVEALED)).thenReturn(0);
        when(iexecHubService.canReopen(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(iexecHubService.reOpen(task.getChainTaskId())).thenReturn(Optional.of(new ChainReceipt()));
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.ACTIVE)
                .build()));
        doNothing().when(revealTimeoutDetector).detect();

        taskUpdateManager.consensusReached2Reopening(task);

        assertThat(task.getDateStatusList().get(0).getStatus()).isEqualTo(RECEIVED);
        assertThat(task.getDateStatusList().get(1).getStatus()).isEqualTo(CONSENSUS_REACHED);
        assertThat(task.getDateStatusList().get(2).getStatus()).isEqualTo(REOPENING);
        assertThat(task.getDateStatusList().get(3).getStatus()).isEqualTo(REOPENED);
        assertThat(task.getDateStatusList().get(4).getStatus()).isEqualTo(INITIALIZED);
    }

    // Tests on received2Initializing transition

    @Test
    public void shouldNotUpdateReceived2InitializingSinceChainTaskIdIsNotEmpty() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RECEIVED);

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
    }

    @Test
    public void shouldNotUpdateReceived2InitializingSinceCurrentStatusIsNotReceived() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(INITIALIZED);

        taskUpdateManager.received2Initializing(task);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    public void shouldNotUpdateReceived2InitializingSinceNoEnoughGas() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RECEIVED);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(false);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(true);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 1)).thenReturn(Optional.of(CHAIN_TASK_ID));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
    }

    @Test
    public void shouldNotUpdateReceived2InitializingSinceTaskNotInUnsetStatusOnChain() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RECEIVED);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(false);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.of(CHAIN_TASK_ID));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
    }

    @Test
    public void shouldNotUpdateReceived2InitializingSinceAfterContributionDeadline() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RECEIVED);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(true);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(false);
        when(taskRepository.save(task)).thenReturn(task);
        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.of(CHAIN_TASK_ID));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(RECEIVED);
    }

    @Test
    public void shouldUpdateInitializing2InitailizeFailedSinceChainTaskIdIsEmpty() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RECEIVED);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(true);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);
        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getLastButOneStatus()).isEqualTo(INITIALIZE_FAILED);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    public void shouldUpdateReceived2Initializing2Initialized() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RECEIVED);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.hasEnoughGas()).thenReturn(true);
        when(iexecHubService.isTaskInUnsetStatusOnChain(CHAIN_DEAL_ID, 0)).thenReturn(true);
        when(iexecHubService.isBeforeContributionDeadline(task.getChainDealId()))
                .thenReturn(true);

        when(taskRepository.save(task)).thenReturn(task);
        when(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, 0)).thenReturn(Optional.of(CHAIN_TASK_ID));
        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.of(true));
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(ChainTask.builder()
                .contributionDeadline(DateTimeUtils.addMinutesToDate(new Date(), 60).getTime())
                .build()));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getChainDealId()).isEqualTo(CHAIN_DEAL_ID);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 1).getStatus()).isEqualTo(INITIALIZED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(INITIALIZING);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus()).isEqualTo(RECEIVED);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    // Tests on initializing2Initialized transition

    @Test
    public void shouldUpdateInitializing2Initialized() {
        Task task = getStubTask(maxExecutionTime);
        task.setChainTaskId(CHAIN_TASK_ID);
        task.changeStatus(INITIALIZING);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.of(true));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 1).getStatus()).isEqualTo(INITIALIZED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(INITIALIZING);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    public void shouldNotUpdateInitializing2InitializedSinceNotInitialized() {
        Task task = getStubTask(maxExecutionTime);
        task.setChainTaskId(CHAIN_TASK_ID);
        task.changeStatus(INITIALIZING);

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
    public void shouldNotUpdateInitializing2InitializedSinceFailedToCheck() {
        Task task = getStubTask(maxExecutionTime);
        task.setChainTaskId(CHAIN_TASK_ID);
        task.changeStatus(INITIALIZING);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(blockchainAdapterService.isInitialized(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(RECEIVED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 1).getStatus()).isEqualTo(INITIALIZING);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZING);
    }

    // Tests on initialized2Running transition

    @Test
    public void shouldUpdateInitialized2Running() { // 1 RUNNING out of 2
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(INITIALIZED);

        final ReplicateStatus[] acceptableStatus = new ReplicateStatus[]{
                ReplicateStatus.STARTED,
                ReplicateStatus.APP_DOWNLOADING,
                ReplicateStatus.APP_DOWNLOAD_FAILED,
                ReplicateStatus.APP_DOWNLOADED,
                ReplicateStatus.DATA_DOWNLOADING,
                ReplicateStatus.DATA_DOWNLOAD_FAILED,
                ReplicateStatus.DATA_DOWNLOADED,
                ReplicateStatus.COMPUTING,
                ReplicateStatus.COMPUTE_FAILED,
                ReplicateStatus.COMPUTED,
                ReplicateStatus.CONTRIBUTING,
                ReplicateStatus.CONTRIBUTE_FAILED,
                ReplicateStatus.CONTRIBUTED
        };

        when(replicatesService.getNbReplicatesWithLastRelevantStatus(task.getChainTaskId(), acceptableStatus))
                .thenReturn(2);
        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.COMPUTED)).thenReturn(0);
        when(taskRepository.save(task)).thenReturn(task);
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    public void shouldNotUpdateInitialized2RunningSinceNoRunningOrComputedReplicates() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(INITIALIZED);

        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.STARTING, ReplicateStatus.COMPUTED)).thenReturn(0);
        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.COMPUTED)).thenReturn(0);
        when(taskRepository.save(task)).thenReturn(task);

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(RUNNING);
    }

    @Test
    public void shouldNotUpdateInitialized2RunningSinceComputedIsMoreThanNeeded() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(INITIALIZED);

        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.STARTING, ReplicateStatus.COMPUTED)).thenReturn(2);
        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.COMPUTED)).thenReturn(4);
        when(taskRepository.save(task)).thenReturn(task);

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    // initializedOrRunning2ContributionTimeout

    @Test
    public void shouldNotUpdateInitializedOrRunning2ContributionTimeoutSinceBeforeTimeout() {
        Date now = new Date();
        Date timeoutInFuture = DateTimeUtils.addMinutesToDate(now, 1);

        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(INITIALIZED);
        task.setContributionDeadline(timeoutInFuture);

        ChainTask chainTask = ChainTask.builder()
                .contributionDeadline(timeoutInFuture.getTime())
                .status(ChainTaskStatus.ACTIVE)
                .build();

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(chainTask));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(CONTRIBUTION_TIMEOUT);
    }

    @Test
    public void shouldNotUpdateInitializedOrRunning2ContributionTimeoutSinceChainTaskIsntActive() {
        Date now = new Date();
        Date timeoutInPast = DateTimeUtils.addMinutesToDate(now, -1);

        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(INITIALIZED);
        task.setContributionDeadline(timeoutInPast);

        ChainTask chainTask = ChainTask.builder()
                .contributionDeadline(timeoutInPast.getTime())
                .status(ChainTaskStatus.REVEALING)
                .build();

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(chainTask));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(CONTRIBUTION_TIMEOUT);
    }

    @Test
    public void shouldNotReSendNotificationWhenAlreadyInContributionTimeout() {
        Date now = new Date();
        Date timeoutInPast = DateTimeUtils.addMinutesToDate(now, -1);

        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(CONTRIBUTION_TIMEOUT);
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


    @Test
    public void shouldUpdateFromInitializedOrRunning2ContributionTimeout() {
        Date now = new Date();
        Date timeoutInPast = DateTimeUtils.addMinutesToDate(now, -1);

        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(INITIALIZED);
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


    // Tests on running2ConsensusReached transition

    @Test
    public void shouldUpdateRunning2ConsensusReached() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RUNNING);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(2)
                .build()));
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getNbValidContributedWinners(any(), any())).thenReturn(2);
        when(taskRepository.save(task)).thenReturn(task);
        when(web3jService.getLatestBlockNumber()).thenReturn(2L);
        when(iexecHubService.getConsensusBlock(anyString(), anyLong())).thenReturn(ChainReceipt.builder().blockNumber(1L).build());
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    @Test
    public void shouldNotUpdateRunning2ConsensusReachedSinceWrongTaskStatus() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(INITIALIZED);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(2)
                .build()));
        when(replicatesService.getNbOffChainReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.CONTRIBUTED)).thenReturn(2);
        when(taskRepository.save(task)).thenReturn(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    @Test
    public void shouldNotUpdateRunning2ConsensusReachedSinceCannotGetChainTask() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RUNNING);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    public void shouldNOTUpdateRunning2ConsensusReachedSinceOnChainStatusNotRevealing() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RUNNING);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.UNSET)
                .winnerCounter(2)
                .build()));
        when(replicatesService.getNbOffChainReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.CONTRIBUTED)).thenReturn(2);
        when(taskRepository.save(task)).thenReturn(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
    }

    @Test
    public void shouldNOTUpdateRunning2ConsensusReachedSinceWinnerContributorsDiffers() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RUNNING);

        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(ChainTask.builder()
                .status(ChainTaskStatus.REVEALING)
                .winnerCounter(2)
                .build()));
        when(replicatesService.getNbOffChainReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.CONTRIBUTED)).thenReturn(1);
        when(taskRepository.save(task)).thenReturn(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(RUNNING);
    }

    // Tests on running2RunningFailed transition
    @Test
    public void shouldUpdateRunning2RunningFailedOn1Worker() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RUNNING);
        task.setTag(TEE_TAG);

        // 1 replicate has tried to run the task:
        // - R1 is in `COMPUTE_FAILED` status;
        Replicate replicate1 = new Replicate();
        replicate1.setWalletAddress(WALLET_WORKER_1);
        replicate1.setChainTaskId(CHAIN_TASK_ID);
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
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getDateOfStatus(RUNNING_FAILED)).isPresent();
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    public void shouldUpdateRunning2RunningFailedOn2Workers() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RUNNING);
        task.setTag(TEE_TAG);

        // 2 replicates have tried to run the task:
        // - R1 is in `COMPUTE_FAILED` status;
        // - R2 is in `APP_DOWNLOAD_FAILED` status.
        Replicate replicate1 = new Replicate();
        replicate1.setWalletAddress(WALLET_WORKER_1);
        replicate1.setChainTaskId(CHAIN_TASK_ID);
        replicate1.setStatusUpdateList(new ArrayList<>());
        replicate1.updateStatus(ReplicateStatus.COMPUTE_FAILED, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate();
        replicate2.setWalletAddress(WALLET_WORKER_2);
        replicate2.setChainTaskId(CHAIN_TASK_ID);
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
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getDateOfStatus(RUNNING_FAILED)).isPresent();
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    public void shouldNotUpdateRunning2RunningFailedOn1WorkerAsNonTeeTask() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RUNNING);
        task.setTag(NO_TEE_TAG);

        // 1 replicate has tried to run the task:
        // - R1 is in `COMPUTE_FAILED` status;
        Replicate replicate1 = new Replicate();
        replicate1.setWalletAddress(WALLET_WORKER_1);
        replicate1.setChainTaskId(CHAIN_TASK_ID);
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
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getDateOfStatus(RUNNING)).isPresent();
    }

    @Test
    public void shouldNotUpdateRunning2RunningFailedOn2WorkersAsNonTeeTask() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RUNNING);
        task.setTag(NO_TEE_TAG);

        // 2 replicates have tried to run the task:
        // - R1 is in `COMPUTE_FAILED` status;
        // - R2 is in `APP_DOWNLOAD_FAILED` status.
        Replicate replicate1 = new Replicate();
        replicate1.setWalletAddress(WALLET_WORKER_1);
        replicate1.setChainTaskId(CHAIN_TASK_ID);
        replicate1.setStatusUpdateList(new ArrayList<>());
        replicate1.updateStatus(ReplicateStatus.COMPUTE_FAILED, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate();
        replicate2.setWalletAddress(WALLET_WORKER_2);
        replicate2.setChainTaskId(CHAIN_TASK_ID);
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
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getDateOfStatus(RUNNING)).isPresent();
    }

    @Test
    public void shouldNotUpdateRunning2AllWorkersFailedSinceOneStillComputing() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RUNNING);
        task.setTag(TEE_TAG);

        // 2 replicates have tried to run the task:
        // - R1 is in `COMPUTE_FAILED` status;
        // - R2 is in `COMPUTING` status.
        Replicate replicate1 = new Replicate();
        replicate1.setWalletAddress(WALLET_WORKER_1);
        replicate1.setChainTaskId(CHAIN_TASK_ID);
        replicate1.setStatusUpdateList(new ArrayList<>());
        replicate1.updateStatus(ReplicateStatus.COMPUTE_FAILED, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate();
        replicate2.setWalletAddress(WALLET_WORKER_2);
        replicate2.setChainTaskId(CHAIN_TASK_ID);
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
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getDateOfStatus(RUNNING_FAILED)).isEmpty();
    }

    @Test
    public void shouldNotUpdateRunning2AllWorkersFailedSinceOneHasReachedComputed() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RUNNING);
        task.setTag(TEE_TAG);

        // 2 replicates have tried to run the task:
        // - R1 is in `COMPUTE_FAILED` status;
        // - R2 is in `CONTRIBUTE_FAILED` status.
        // Worker of R2 has been lost.
        Replicate replicate1 = new Replicate();
        replicate1.setWalletAddress(WALLET_WORKER_1);
        replicate1.setChainTaskId(CHAIN_TASK_ID);
        replicate1.setStatusUpdateList(new ArrayList<>());
        replicate1.updateStatus(ReplicateStatus.COMPUTE_FAILED, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate();
        replicate2.setWalletAddress(WALLET_WORKER_2);
        replicate2.setChainTaskId(CHAIN_TASK_ID);
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
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getDateOfStatus(RUNNING_FAILED)).isEmpty();
    }

    @Test
    public void shouldNotUpdateRunning2AllWorkersFailedSinceOneStillHasToBeLaunched() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RUNNING);
        task.setTag(TEE_TAG);

        // 1 replicates have tried to run the task and 1 is still to be run:
        // - R1 is in `COMPUTE_FAILED` status;
        // - R2 has not started yet.
        Replicate replicate1 = new Replicate();
        replicate1.setWalletAddress(WALLET_WORKER_1);
        replicate1.setChainTaskId(CHAIN_TASK_ID);
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
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getDateOfStatus(RUNNING_FAILED)).isEmpty();
    }

    // Tests on consensusReached2AtLeastOneReveal2UploadRequested transition

    @Test
    public void shouldUpdateConsensusReached2AtLeastOneReveal2Uploading() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(CONSENSUS_REACHED);
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.REVEALED)).thenReturn(1);
        when(taskRepository.save(task)).thenReturn(task);
        when(replicatesService.getRandomReplicateWithRevealStatus(task.getChainTaskId())).thenReturn(Optional.of(replicate));
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getUploadingWorkerWalletAddress()).isEqualTo(replicate.getWalletAddress());
        int size = task.getDateStatusList().size();
        assertThat(task.getDateStatusList().get(size - 2).getStatus()).isEqualTo(AT_LEAST_ONE_REVEALED);
        assertThat(task.getDateStatusList().get(size - 1).getStatus()).isEqualTo(RESULT_UPLOADING);
    }

    @Test
    public void shouldNOTUpdateConsensusReached2AtLeastOneRevealSinceNoRevealedReplicate() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(CONSENSUS_REACHED);
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.REVEALED)).thenReturn(0);
        when(taskRepository.save(task)).thenReturn(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(CONSENSUS_REACHED);
    }

    // Tests on AT_LEAST_ONE_REVEALED
    @Test
    public void shouldRequestUploadAfterOneRevealed() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(AT_LEAST_ONE_REVEALED);
        task.setChainTaskId(CHAIN_TASK_ID);

        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);
        when(replicatesService.getRandomReplicateWithRevealStatus(task.getChainTaskId())).thenReturn(Optional.of(replicate));
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);
    }

    @Test
    public void shouldNotRequestUploadAfterOneRevealedAsWorkerLost() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(AT_LEAST_ONE_REVEALED);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getRandomReplicateWithRevealStatus(task.getChainTaskId())).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getCurrentStatus()).isEqualTo(AT_LEAST_ONE_REVEALED);
    }

    // Tests on reopening2Reopened transition
    @Test
    public void shouldUpdateReopening2Reopened() {
        final Task task = getStubTask(maxExecutionTime);
        task.setCurrentStatus(REOPENING);

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

        doNothing().when(replicatesService).setRevealTimeoutStatusIfNeeded(eq(CHAIN_TASK_ID), any());

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getConsensus()).isNull();
        assertThat(task.getRevealDeadline().toInstant()).isEqualTo(new Date(0).toInstant());
        assertThat(task.getDateOfStatus(REOPENED)).isPresent();
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);

        verify(replicatesService, times(2)).setRevealTimeoutStatusIfNeeded(eq(CHAIN_TASK_ID), any());
    }

    @Test
    public void shouldNotUpdateReopening2ReopenedSinceUnknownChainTask() {
        final Task task = getStubTask(maxExecutionTime);
        task.setCurrentStatus(REOPENING);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(REOPENING);

        verify(replicatesService, times(0)).setRevealTimeoutStatusIfNeeded(eq(CHAIN_TASK_ID), any());
    }

    // Tests on REOPENED
    @Test
    public void shouldUpdateReopened() {
        final Task task = getStubTask(maxExecutionTime);
        task.setCurrentStatus(REOPENED);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(INITIALIZED);
    }

    // Test on resultUploading2Uploaded2Finalizing2Finalized

    @Test
    public void shouldUpdateResultUploading2Uploaded2Finalizing2Finalized() { //one worker uploaded
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RESULT_UPLOADING);

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

    // Tests on finalizing2Finalized transition

    @Test
    public void shouldUpdateFinalized2Completed() {
        Task task = getStubTask(maxExecutionTime);
        task.setChainTaskId(CHAIN_TASK_ID);
        task.changeStatus(FINALIZED);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(COMPLETED);
    }

    @Test
    public void shouldUpdateFinalizing2FinalizedFailed() {
        Task task = getStubTask(maxExecutionTime);
        task.setChainTaskId(CHAIN_TASK_ID);
        task.changeStatus(FINALIZING);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(blockchainAdapterService.isFinalized(CHAIN_TASK_ID)).thenReturn(Optional.of(false));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(FINALIZE_FAILED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus()).isEqualTo(FINALIZING);
    }

    @Test
    public void shouldUpdateResultUploading2UploadedButNot2Finalizing() { //one worker uploaded
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RESULT_UPLOADING);

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
    public void shouldUpdateResultUploading2Uploaded2Finalizing2FinalizeFail() { //one worker uploaded && finalize FAIL
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RESULT_UPLOADING);
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
    public void shouldUpdateResultUploading2UploadedFailAndRequestUploadAgain() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RESULT_UPLOADING);

        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getRandomReplicateWithRevealStatus(task.getChainTaskId())).thenReturn(Optional.of(replicate));
        doNothing().when(replicatesService).updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, ReplicateStatus.RESULT_UPLOAD_REQUESTED);
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getUploadingWorkerWalletAddress()).isEqualTo(replicate.getWalletAddress());
        assertThat(task.getLatestStatusChange().getStatus()).isEqualTo(RESULT_UPLOADING);

        verify(replicatesService).updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, ReplicateStatus.RESULT_UPLOAD_REQUESTED);
        verify(applicationEventPublisher).publishEvent(any(PleaseUploadEvent.class));
    }

    @Test
    public void shouldNotUpdateResultUploading2UploadedSinceNoUploadingReplicate() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);

        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RESULT_UPLOADING);
        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.empty());
        when(replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID)).thenReturn(Optional.of(replicate));

        doNothing().when(replicatesService)
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, ReplicateStatus.RESULT_UPLOAD_REQUESTED);
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);

        verify(replicatesService).updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, ReplicateStatus.RESULT_UPLOAD_REQUESTED);
    }

    @Test
    public void shouldNotUpdateResultUploading2UploadedSinceUploadingReplicateInUploadingStatus() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatusUpdate.builder().status(ReplicateStatus.RESULT_UPLOADING).build());

        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RESULT_UPLOADING);
        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(replicate));

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);
    }

    @Test
    public void shouldNotUpdateResultUploading2UploadedSinceUploadingReplicateInRecoveringUploadingStatus() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatusUpdate.builder().status(ReplicateStatus.RESULT_UPLOADING).build());
        replicate.updateStatus(ReplicateStatusUpdate.builder().status(ReplicateStatus.RECOVERING).build());

        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RESULT_UPLOADING);
        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(replicate));

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);
    }

    @Test
    public void shouldNotUpdateResultUploading2UploadedSinceUploadingReplicateNotInUploadingNorRecoveringUploadingStatus() {
        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatusUpdate.builder().status(ReplicateStatus.REVEALED).build());

        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RESULT_UPLOADING);
        task.setUploadingWorkerWalletAddress(WALLET_WORKER_1);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getReplicateWithResultUploadedStatus(CHAIN_TASK_ID)).thenReturn(Optional.empty());
        when(replicatesService.getReplicate(CHAIN_TASK_ID, WALLET_WORKER_1)).thenReturn(Optional.of(replicate));
        when(replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID)).thenReturn(Optional.of(replicate));

        doNothing().when(replicatesService)
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, ReplicateStatus.RESULT_UPLOAD_REQUESTED);
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskUpdateManager.updateTask(task.getChainTaskId());

        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);

        verify(replicatesService).updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, ReplicateStatus.RESULT_UPLOAD_REQUESTED);
    }

    // Tests on resultUploading2UploadTimeout transition
    @Test
    public void shouldUpdateResultUploading2UploadTimeout() {
        Task task = getStubTask(maxExecutionTime);
        task.setCurrentStatus(RESULT_UPLOADING);
        task.setFinalDeadline(new Date(0));

        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskUpdateManager.resultUploading2UploadTimeout(task);

        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(RESULT_UPLOAD_TIMEOUT);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.FAILED);
    }

    // Tests on resultUploaded2Finalizing transition
    @Test
    public void shouldUpdateResultUploaded2Finalizing() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RESULT_UPLOADED);

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
        doNothing().when(applicationEventPublisher).publishEvent(any());

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
    public void shouldNotUpdateResultUploaded2FinalizingSinceCantFinalize() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RESULT_UPLOADED);

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
    public void shouldNotUpdateResultUploaded2FinalizingSinceRevealsNotEqual() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RESULT_UPLOADED);

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
    public void shouldNotUpdateResultUploaded2FinalizingSinceNotEnoughGas() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RESULT_UPLOADED);

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
    public void shouldNotUpdateResultUploaded2FinalizingSinceCantRequestFinalize() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RESULT_UPLOADED);

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

    // Tests on finalizedToCompleted transition
    @Test
    public void shouldUpdateFinalizing2Finalized2Completed() {
        Task task = getStubTask(maxExecutionTime);
        task.setChainTaskId(CHAIN_TASK_ID);
        task.changeStatus(FINALIZING);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(blockchainAdapterService.isFinalized(CHAIN_TASK_ID)).thenReturn(Optional.of(true));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(COMPLETED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 2).getStatus()).isEqualTo(FINALIZED);
        assertThat(task.getDateStatusList().get(task.getDateStatusList().size() - 3).getStatus()).isEqualTo(FINALIZING);
    }


    @Test
    public void shouldWaitUpdateReplicateStatusFromUnsetToContributed() {
        List<Replicate> replicates = new ArrayList<>();
        replicates.add(new Replicate("0x1", "chainTaskId"));

        replicates.get(0).updateStatus(ReplicateStatus.COMPUTED, ReplicateStatusModifier.WORKER);

        List<TaskStatusChange> dateStatusList = new ArrayList<>();
        dateStatusList.add(new TaskStatusChange(TaskStatus.RUNNING));

        Task task = Task.builder()
                .id("taskId")
                .chainTaskId("chainTaskId")
                .currentStatus(TaskStatus.RUNNING)
                .commandLine("ls")
                .dateStatusList(dateStatusList)
                .build();

        when(taskRepository.findByChainTaskId("chainTaskId")).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);
        ChainContribution chainContribution = ChainContribution.builder().status(ChainContributionStatus.UNSET).build();
        when(iexecHubService.getChainContribution("chainTaskId", "0x1")).thenReturn(Optional.of(chainContribution));


        Runnable runnable1 = () -> {
            // taskUpdateManager.updateReplicateStatus("chainTaskId", "0x1", ReplicateStatus.CONTRIBUTED);
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
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(INITIALIZED);
        final ReplicateStatus[] acceptableStatus = new ReplicateStatus[]{
                ReplicateStatus.STARTED,
                ReplicateStatus.APP_DOWNLOADING,
                ReplicateStatus.APP_DOWNLOAD_FAILED,
                ReplicateStatus.APP_DOWNLOADED,
                ReplicateStatus.DATA_DOWNLOADING,
                ReplicateStatus.DATA_DOWNLOAD_FAILED,
                ReplicateStatus.DATA_DOWNLOADED,
                ReplicateStatus.COMPUTING,
                ReplicateStatus.COMPUTE_FAILED,
                ReplicateStatus.COMPUTED,
                ReplicateStatus.CONTRIBUTING,
                ReplicateStatus.CONTRIBUTE_FAILED,
                ReplicateStatus.CONTRIBUTED
        };

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getNbReplicatesWithLastRelevantStatus(task.getChainTaskId(), acceptableStatus))
                .thenReturn(3);
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(0);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    // 2 replicates in RUNNING and and 2 in COMPUTED
    @Test
    public void shouldUpdateTaskToRunningFromWorkersInRunningAndComputed() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(INITIALIZED);
        final ReplicateStatus[] acceptableStatus = new ReplicateStatus[]{
                ReplicateStatus.STARTED,
                ReplicateStatus.APP_DOWNLOADING,
                ReplicateStatus.APP_DOWNLOAD_FAILED,
                ReplicateStatus.APP_DOWNLOADED,
                ReplicateStatus.DATA_DOWNLOADING,
                ReplicateStatus.DATA_DOWNLOAD_FAILED,
                ReplicateStatus.DATA_DOWNLOADED,
                ReplicateStatus.COMPUTING,
                ReplicateStatus.COMPUTE_FAILED,
                ReplicateStatus.COMPUTED,
                ReplicateStatus.CONTRIBUTING,
                ReplicateStatus.CONTRIBUTE_FAILED,
                ReplicateStatus.CONTRIBUTED
        };

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getNbReplicatesWithLastRelevantStatus(task.getChainTaskId(), acceptableStatus))
                .thenReturn(4);
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(2);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    // all replicates in INITIALIZED
    @Test
    public void shouldNotUpdateToRunningSinceAllReplicatesInCreated() {
        Task task = getStubTask(maxExecutionTime);
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.STARTING, ReplicateStatus.COMPUTED)).thenReturn(0);
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(0);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RUNNING);
    }

    // Two replicates in COMPUTED BUT numWorkersNeeded = 2, so the task should not be able to move directly from
    // INITIALIZED to COMPUTED
    @Test
    public void shouldNotUpdateToRunningCase2() {
        Task task = getStubTask(maxExecutionTime);
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.STARTING, ReplicateStatus.COMPUTED)).thenReturn(2);
        when(replicatesService.getNbReplicatesWithCurrentStatus(CHAIN_TASK_ID, ReplicateStatus.COMPUTED)).thenReturn(2);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RUNNING);
    }


    // at least one UPLOADED
    @Test
    public void shouldUpdateFromUploadingResultToResultUploaded() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(TaskStatus.RUNNING);
        task.changeStatus(TaskStatus.RESULT_UPLOADING);

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
    public void shouldNotUpdateToResultUploaded() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(TaskStatus.RUNNING);
        task.changeStatus(TaskStatus.RESULT_UPLOADING);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.RESULT_UPLOADED)).thenReturn(0);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.RESULT_UPLOADED);
        assertThat(task.getCurrentStatus()).isNotEqualTo(TaskStatus.COMPLETED);
    }

    @Test
    public void shouldNotUpdateFromResultUploadedToFinalizingSinceNotEnoughGas() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(RUNNING);
        task.changeStatus(RESULT_UPLOADING);
        task.changeStatus(RESULT_UPLOADED);
        ChainTask chainTask = ChainTask.builder().revealCounter(1).build();

        when(iexecHubService.canFinalize(task.getChainTaskId())).thenReturn(true);
        when(iexecHubService.getChainTask(task.getChainTaskId())).thenReturn(Optional.of(chainTask));
        when(replicatesService.getNbReplicatesContainingStatus(task.getChainTaskId(), ReplicateStatus.REVEALED)).thenReturn(1);
        when(iexecHubService.hasEnoughGas()).thenReturn(false);

        taskUpdateManager.updateTask(task.getChainTaskId());
        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADED);
    }


    @Test
    public void shouldUpdateFromAnyInProgressStatus2FinalDeadlineReached() {
        Task task = getStubTask(maxExecutionTime);
        task.setFinalDeadline(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        task.changeStatus(RECEIVED);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(FINAL_DEADLINE_REACHED);
    }

    @Test
    public void shouldUpdateFromFinalDeadlineReached2Failed() {
        Task task = getStubTask(maxExecutionTime);
        task.setFinalDeadline(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        task.changeStatus(FINAL_DEADLINE_REACHED);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    public void shouldNotUpdateToFinalDeadlineReachedIfAlreadyFailed() {
        Task task = getStubTask(maxExecutionTime);
        task.setFinalDeadline(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        task.changeStatus(FAILED);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
    }

    @Test
    public void shouldNotUpdateToFinalDeadlineReachedIfAlreadyCompleted() {
        Task task = getStubTask(maxExecutionTime);
        task.setFinalDeadline(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)));
        task.changeStatus(COMPLETED);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));

        taskUpdateManager.updateTask(CHAIN_TASK_ID);
        assertThat(task.getCurrentStatus()).isEqualTo(COMPLETED);
    }

    // Tests on toFailed() automatic transitions
    @Test
    public void shouldUpdateToFailed() {
        final TaskStatus[] statusThatAutomaticallyFail = new TaskStatus[]{
                INITIALIZE_FAILED,
                RUNNING_FAILED,
                CONTRIBUTION_TIMEOUT,
                REOPEN_FAILED,
                RESULT_UPLOAD_TIMEOUT,
                FINALIZE_FAILED};

        for (TaskStatus status : statusThatAutomaticallyFail) {
            Task task = getStubTask(maxExecutionTime);
            task.changeStatus(status);
            task.setChainTaskId(CHAIN_TASK_ID);

            when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
            when(taskRepository.save(task)).thenReturn(task);

            taskUpdateManager.updateTask(task.getChainTaskId());

            assertThat(task.getCurrentStatus()).isEqualTo(FAILED);
        }
    }

    // Tests on requestUpload
    @Test
    public void shouldRequestUpload() {
        Task task = getStubTask(maxExecutionTime);
        task.setChainTaskId(CHAIN_TASK_ID);

        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);
        when(replicatesService.getNbReplicatesWithCurrentStatus(
                CHAIN_TASK_ID,
                ReplicateStatus.RESULT_UPLOADING,
                ReplicateStatus.RESULT_UPLOAD_REQUESTED)
        ).thenReturn(0);
        when(replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID)).thenReturn(Optional.of(replicate));
        doNothing().when(replicatesService).updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, ReplicateStatus.RESULT_UPLOAD_REQUESTED);
        doNothing().when(applicationEventPublisher).publishEvent(any());

        taskUpdateManager.requestUpload(task);

        assertThat(task.getCurrentStatus()).isEqualTo(RESULT_UPLOADING);
        verify(replicatesService).updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, ReplicateStatus.RESULT_UPLOAD_REQUESTED);
        verify(applicationEventPublisher).publishEvent(any(PleaseUploadEvent.class));
    }

    @Test
    public void shouldNotRequestUpload() {
        Task task = getStubTask(maxExecutionTime);
        task.changeStatus(AT_LEAST_ONE_REVEALED);
        task.setChainTaskId(CHAIN_TASK_ID);

        when(replicatesService.getNbReplicatesWithCurrentStatus(
                CHAIN_TASK_ID,
                ReplicateStatus.RESULT_UPLOADING,
                ReplicateStatus.RESULT_UPLOAD_REQUESTED)
        ).thenReturn(0);
        // For example, this could happen if replicate is lost after having revealed.
        when(replicatesService.getRandomReplicateWithRevealStatus(CHAIN_TASK_ID)).thenReturn(Optional.empty());

        taskUpdateManager.requestUpload(task);

        assertThat(task.getCurrentStatus()).isEqualTo(AT_LEAST_ONE_REVEALED);
        verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, ReplicateStatus.RESULT_UPLOAD_REQUESTED);
        verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any(PleaseUploadEvent.class));
    }

    @Test
    public void shouldNotRequestUploadSinceUploadInProgress() {
        Task task = getStubTask(maxExecutionTime);
        task.setChainTaskId(CHAIN_TASK_ID);
        task.changeStatus(AT_LEAST_ONE_REVEALED);

        Replicate replicate = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(taskRepository.save(task)).thenReturn(task);
        when(replicatesService.getNbReplicatesWithCurrentStatus(
                CHAIN_TASK_ID,
                ReplicateStatus.RESULT_UPLOADING,
                ReplicateStatus.RESULT_UPLOAD_REQUESTED)
        ).thenReturn(1);

        taskUpdateManager.requestUpload(task);

        assertThat(task.getCurrentStatus()).isEqualTo(AT_LEAST_ONE_REVEALED);
        verify(replicatesService, Mockito.times(0))
                .getRandomReplicateWithRevealStatus(CHAIN_TASK_ID);
        verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(CHAIN_TASK_ID, WALLET_WORKER_1, ReplicateStatus.RESULT_UPLOAD_REQUESTED);
        verify(applicationEventPublisher, Mockito.times(0))
                .publishEvent(any(PleaseUploadEvent.class));
    }

    // publishRequest

    @Test
    public void shouldTriggerUpdateTaskAsynchronously() {
        taskUpdateManager.publishUpdateTaskRequest(CHAIN_TASK_ID);
        verify(taskUpdateRequestManager).publishRequest(eq(CHAIN_TASK_ID));
    }
}
