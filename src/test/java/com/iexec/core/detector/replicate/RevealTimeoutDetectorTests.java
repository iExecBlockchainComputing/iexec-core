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

package com.iexec.core.detector.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.replicate.ReplicateStatusModifier;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.iexec.common.utils.DateTimeUtils.addMinutesToDate;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class RevealTimeoutDetectorTests {

    private final static String WALLET_WORKER_1 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";
    private final static String WALLET_WORKER_2 = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd249";
    private final static String CHAIN_TASK_ID = "chainTaskId";

    @Mock
    private TaskService taskService;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private IexecHubService iexecHubService;

    @InjectMocks
    private RevealTimeoutDetector revealDetector;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void souldDetectTaskAfterRevealDealLineWithAtLeastOneReveal() {
        Date twoMinutesAgo = addMinutesToDate(new Date(), -2);

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.CONSENSUS_REACHED);
        task.setRevealDeadline(twoMinutesAgo);
        List<Task> taskList = Collections.singletonList(task);

        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        List<Replicate> replicateList = Arrays.asList(replicate1, replicate2);

        List<TaskStatus> taskStatusList = Arrays.asList(TaskStatus.AT_LEAST_ONE_REVEALED,
                TaskStatus.RESULT_UPLOADING, TaskStatus.RESULT_UPLOADED);

        when(taskService.findByCurrentStatus(taskStatusList)).thenReturn(taskList);
        when(replicatesService.getReplicates(task.getChainTaskId())).thenReturn(replicateList);
        when(taskService.findByCurrentStatus(TaskStatus.CONSENSUS_REACHED)).thenReturn(Collections.emptyList());

        revealDetector.detect();

        Mockito.verify(replicatesService, Mockito.times(1))
                .setRevealTimeoutStatusIfNeeded(CHAIN_TASK_ID, replicate1);

        Mockito.verify(replicatesService, Mockito.times(1))
                .setRevealTimeoutStatusIfNeeded(CHAIN_TASK_ID, replicate2);
    }

    @Test
    public void shouldDetectTaskAfterRevealDealLineWithZero() {
        Date twoMinutesAgo = addMinutesToDate(new Date(), -2);

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.CONSENSUS_REACHED);
        task.setRevealDeadline(twoMinutesAgo);
        List<Task> taskList = Collections.singletonList(task);

        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);
        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        List<Replicate> replicateList = Arrays.asList(replicate1, replicate2);

        List<TaskStatus> taskStatusList = Arrays.asList(TaskStatus.AT_LEAST_ONE_REVEALED,
                TaskStatus.RESULT_UPLOADING, TaskStatus.RESULT_UPLOADED);

        when(taskService.findByCurrentStatus(taskStatusList)).thenReturn(Collections.emptyList());
        when(replicatesService.getReplicates(task.getChainTaskId())).thenReturn(replicateList);
        when(taskService.findByCurrentStatus(TaskStatus.CONSENSUS_REACHED)).thenReturn(taskList);

        revealDetector.detect();

        Mockito.verify(replicatesService, Mockito.times(1))
                .setRevealTimeoutStatusIfNeeded(CHAIN_TASK_ID, replicate1);

        Mockito.verify(replicatesService, Mockito.times(1))
                .setRevealTimeoutStatusIfNeeded(CHAIN_TASK_ID, replicate2);
    }

    @Test
    public void shouldNotDetectAnyRevealTimeout() {
        List<TaskStatus> taskStatusList = Arrays.asList(TaskStatus.AT_LEAST_ONE_REVEALED,
                TaskStatus.RESULT_UPLOADING, TaskStatus.RESULT_UPLOADED);

        when(taskService.findByCurrentStatus(taskStatusList))
                .thenReturn(Collections.emptyList());
        when(taskService.findByCurrentStatus(TaskStatus.CONSENSUS_REACHED))
                .thenReturn(Collections.emptyList());

        revealDetector.detect();

        Mockito.verify(replicatesService, Mockito.times(0))
                .getReplicates(Mockito.any());

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));

        Mockito.verify(iexecHubService, Mockito.times(0))
                .reOpen(Mockito.any());
    }


    @Test
    public void shouldUpdateOneReplicateToRevealTimeout() {
        Date twoMinutesAgo = addMinutesToDate(new Date(), -2);

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.CONSENSUS_REACHED);
        task.setRevealDeadline(twoMinutesAgo);

        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);


        when(taskService.findByCurrentStatus(TaskStatus.CONSENSUS_REACHED)).thenReturn(Collections.singletonList(task));
        when(replicatesService.getReplicates(task.getChainTaskId())).thenReturn(Arrays.asList(replicate1, replicate2));

        revealDetector.detect();

        Mockito.verify(replicatesService, Mockito.times(1))
                .setRevealTimeoutStatusIfNeeded(CHAIN_TASK_ID, replicate1);

        Mockito.verify(replicatesService, Mockito.times(1))
                .setRevealTimeoutStatusIfNeeded(CHAIN_TASK_ID, replicate2);
    }

    @Test
    public void shouldNotUpdateSinceTaskIsNotTimedout() {
        Date twoMinutesInFuture = addMinutesToDate(new Date(), 2);

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.CONSENSUS_REACHED);
        task.setRevealDeadline(twoMinutesInFuture);

        Replicate replicate1 = new Replicate(WALLET_WORKER_1, CHAIN_TASK_ID);
        replicate1.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);

        Replicate replicate2 = new Replicate(WALLET_WORKER_2, CHAIN_TASK_ID);
        replicate2.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);


        when(taskService.findByCurrentStatus(TaskStatus.CONSENSUS_REACHED)).thenReturn(Collections.singletonList(task));
        when(replicatesService.getReplicates(task.getChainTaskId())).thenReturn(Arrays.asList(replicate1, replicate2));

        revealDetector.detect();

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));
    }
}
