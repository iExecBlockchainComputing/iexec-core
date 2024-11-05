/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.detector.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.iexec.core.task.TaskStatus.*;
import static com.iexec.core.task.TaskTestsUtils.CHAIN_TASK_ID;
import static com.iexec.core.task.TaskTestsUtils.getStubTask;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RevealTimeoutDetectorTests {

    private static final String WALLET_WORKER_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private static final String WALLET_WORKER_2 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd249";

    @Mock
    private TaskService taskService;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private IexecHubService iexecHubService;

    @InjectMocks
    private RevealTimeoutDetector revealDetector;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldDetectTaskAfterRevealDealLineWithAtLeastOneReveal() {
        Date twoMinutesAgo = Date.from(Instant.now().minus(2L, ChronoUnit.MINUTES));

        final Task task = getStubTask(CONSENSUS_REACHED);
        task.setRevealDeadline(twoMinutesAgo);
        List<Task> taskList = Collections.singletonList(task);

        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        List<Replicate> replicateList = Arrays.asList(replicate1, replicate2);

        final List<TaskStatus> taskStatusList = List.of(AT_LEAST_ONE_REVEALED, RESULT_UPLOADING, RESULT_UPLOADED);

        when(taskService.findByCurrentStatus(taskStatusList)).thenReturn(taskList);
        when(replicatesService.getReplicates(task.getChainTaskId())).thenReturn(replicateList);
        when(taskService.findByCurrentStatus(CONSENSUS_REACHED)).thenReturn(Collections.emptyList());

        revealDetector.detect();

        verify(replicatesService).setRevealTimeoutStatusIfNeeded(CHAIN_TASK_ID, replicate1);
        verify(replicatesService).setRevealTimeoutStatusIfNeeded(CHAIN_TASK_ID, replicate2);
    }

    @Test
    void shouldDetectTaskAfterRevealDealLineWithZero() {
        Date twoMinutesAgo = Date.from(Instant.now().minus(2L, ChronoUnit.MINUTES));

        final Task task = getStubTask(CONSENSUS_REACHED);
        task.setRevealDeadline(twoMinutesAgo);
        List<Task> taskList = Collections.singletonList(task);

        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        List<Replicate> replicateList = Arrays.asList(replicate1, replicate2);

        final List<TaskStatus> taskStatusList = List.of(AT_LEAST_ONE_REVEALED, RESULT_UPLOADING, RESULT_UPLOADED);

        when(taskService.findByCurrentStatus(taskStatusList)).thenReturn(Collections.emptyList());
        when(replicatesService.getReplicates(task.getChainTaskId())).thenReturn(replicateList);
        when(taskService.findByCurrentStatus(CONSENSUS_REACHED)).thenReturn(taskList);

        revealDetector.detect();

        verify(replicatesService).setRevealTimeoutStatusIfNeeded(CHAIN_TASK_ID, replicate1);
        verify(replicatesService).setRevealTimeoutStatusIfNeeded(CHAIN_TASK_ID, replicate2);
    }

    @Test
    void shouldNotDetectAnyRevealTimeout() {
        final List<TaskStatus> taskStatusList = List.of(AT_LEAST_ONE_REVEALED, RESULT_UPLOADING, RESULT_UPLOADED);

        when(taskService.findByCurrentStatus(taskStatusList))
                .thenReturn(Collections.emptyList());
        when(taskService.findByCurrentStatus(CONSENSUS_REACHED))
                .thenReturn(Collections.emptyList());

        revealDetector.detect();

        verify(replicatesService, never()).getReplicates(Mockito.any());
        verify(replicatesService, never()).updateReplicateStatus(any(), any(), any(ReplicateStatusUpdate.class));
        verify(iexecHubService, never()).reOpen(Mockito.any());
    }


    @Test
    void shouldUpdateOneReplicateToRevealTimeout() {
        Date twoMinutesAgo = Date.from(Instant.now().minus(2L, ChronoUnit.MINUTES));

        final Task task = getStubTask(CONSENSUS_REACHED);
        task.setRevealDeadline(twoMinutesAgo);

        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);

        when(taskService.findByCurrentStatus(CONSENSUS_REACHED)).thenReturn(Collections.singletonList(task));
        when(replicatesService.getReplicates(task.getChainTaskId())).thenReturn(Arrays.asList(replicate1, replicate2));

        revealDetector.detect();

        verify(replicatesService).setRevealTimeoutStatusIfNeeded(CHAIN_TASK_ID, replicate1);
        verify(replicatesService).setRevealTimeoutStatusIfNeeded(CHAIN_TASK_ID, replicate2);
    }

    @Test
    void shouldNotUpdateSinceTaskIsNotTimedout() {
        Date twoMinutesInFuture = Date.from(Instant.now().plus(2L, ChronoUnit.MINUTES));

        final Task task = getStubTask(CONSENSUS_REACHED);
        task.setRevealDeadline(twoMinutesInFuture);

        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);


        when(taskService.findByCurrentStatus(CONSENSUS_REACHED)).thenReturn(Collections.singletonList(task));
        when(replicatesService.getReplicates(CHAIN_TASK_ID)).thenReturn(Arrays.asList(replicate1, replicate2));

        revealDetector.detect();

        verify(replicatesService, never()).updateReplicateStatus(any(), any(), any(ReplicateStatusUpdate.class));
    }
}
