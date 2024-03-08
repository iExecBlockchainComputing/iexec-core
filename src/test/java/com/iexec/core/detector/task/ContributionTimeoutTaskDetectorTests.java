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

package com.iexec.core.detector.task;

import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import com.iexec.core.worker.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ContributionTimeoutTaskDetectorTests {

    private final static String CHAIN_TASK_ID = "chainTaskId";

    @Mock
    private TaskService taskService;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private WorkerService workerService;

    @Mock
    private TaskUpdateRequestManager taskUpdateRequestManager;

    @Spy
    @InjectMocks
    private ContributionTimeoutTaskDetector contributionDetector;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldNotDetectAnyContributionTimeout() {
        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.emptyList());
        contributionDetector.detect();

        Mockito.verify(workerService, Mockito.times(0))
                .removeChainTaskIdFromWorker(any(), any());

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(ReplicateStatusUpdate.class));

        Mockito.verify(taskUpdateRequestManager, Mockito.times(0))
                .publishRequest(any());
    }

    @Test
    void shouldNotUpdateTaskIfBeforeTimeout() {
        Date oneMinuteAfterNow = Date.from(Instant.now().plus(1L, ChronoUnit.MINUTES));

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.RUNNING);
        task.setContributionDeadline(oneMinuteAfterNow);

        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));
        contributionDetector.detect();

        Mockito.verify(workerService, Mockito.times(0))
                .removeChainTaskIdFromWorker(any(), any());

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(ReplicateStatusUpdate.class));

        Mockito.verify(taskUpdateRequestManager, Mockito.times(0))
                .publishRequest(any());
    }


    @Test
    void shouldUpdateIfIsTimeout() {
        Date oneMinuteBeforeNow = Date.from(Instant.now().minus(1L, ChronoUnit.MINUTES));

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.RUNNING);
        task.setContributionDeadline(oneMinuteBeforeNow);

        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));

        contributionDetector.detect();

        Mockito.verify(taskUpdateRequestManager, Mockito.times(1))
                .publishRequest(any());
    }
}
