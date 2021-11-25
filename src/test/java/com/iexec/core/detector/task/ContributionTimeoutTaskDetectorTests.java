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

package com.iexec.core.detector.task;

import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.common.utils.DateTimeUtils;
import com.iexec.core.task.TaskUpdateManager;
import com.iexec.core.worker.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class ContributionTimeoutTaskDetectorTests {

    private final static String CHAIN_TASK_ID = "chainTaskId";

    @Mock
    private TaskService taskService;

    @Mock
    private ReplicatesService replicatesService;

    @Mock
    private WorkerService workerService;

    @Mock
    private TaskUpdateManager taskUpdateManager;

    @Spy
    @InjectMocks
    private ContributionTimeoutTaskDetector contributionDetector;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldNotDetectAnyContributionTimeout() {
        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.emptyList());
        contributionDetector.detect();

        Mockito.verify(workerService, Mockito.times(0))
                .removeChainTaskIdFromWorker(any(), any());

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));

        Mockito.verify(taskUpdateManager, Mockito.times(0))
                .publishUpdateTaskRequest(any());
    }

    @Test
    public void shouldNotUpdateTaskIfBeforeTimeout() {
        Date now = new Date();
        Date oneMinuteAfterNow = DateTimeUtils.addMinutesToDate(now, 1);

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.RUNNING);
        task.setContributionDeadline(oneMinuteAfterNow);

        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));
        contributionDetector.detect();

        Mockito.verify(workerService, Mockito.times(0))
                .removeChainTaskIdFromWorker(any(), any());

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(any(), any(), any(), any(ReplicateStatusDetails.class));

        Mockito.verify(taskUpdateManager, Mockito.times(0))
                .publishUpdateTaskRequest(any());
    }


    @Test
    public void shouldUpdateIfIsTimeout() {
        Date now = new Date();
        Date oneMinuteBeforeNow = DateTimeUtils.addMinutesToDate(now, -1);

        Task task = new Task("dappName", "commandLine", 2, CHAIN_TASK_ID);
        task.changeStatus(TaskStatus.RUNNING);
        task.setContributionDeadline(oneMinuteBeforeNow);

        when(taskService.findByCurrentStatus(Arrays.asList(TaskStatus.INITIALIZED, TaskStatus.RUNNING))).thenReturn(Collections.singletonList(task));

        contributionDetector.detect();

        Mockito.verify(taskUpdateManager, Mockito.times(1))
                .publishUpdateTaskRequest(any());
    }
}
