/*
 * Copyright 2022 IEXEC BLOCKCHAIN TECH
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

import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicateModel;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.logs.ComputeLogsService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TaskControllerTests {

    public static final String TASK_ID = "0xtask";
    public static final String WORKER_ADDRESS = "0xworker";

    @Mock
    private TaskService taskService;
    @Mock
    private ReplicatesService replicatesService;
    @Mock
    private ComputeLogsService computeLogsService;
    @InjectMocks
    private TaskController taskController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldGetTaskModel() {
        Task taskEntity = mock(Task.class);
        when(taskEntity.getChainTaskId()).thenReturn(TASK_ID);
        when(taskService.getTaskByChainTaskId(TASK_ID))
                .thenReturn(Optional.of(taskEntity));

        ResponseEntity<TaskModel> taskResponse = taskController.getTask(TASK_ID);
        Assertions.assertTrue(taskResponse.getStatusCode().is2xxSuccessful());
        TaskModel task = taskResponse.getBody();
        Assertions.assertNotNull(task);
        Assertions.assertEquals(TASK_ID, task.getChainTaskId());
        Assertions.assertNull(task.getReplicates());
    }

    @Test
    void shouldGetTaskModelWithReplicates() {
        Task taskEntity = mock(Task.class);
        when(taskEntity.getChainTaskId()).thenReturn(TASK_ID);
        when(taskService.getTaskByChainTaskId(TASK_ID))
                .thenReturn(Optional.of(taskEntity));
        when(replicatesService.hasReplicatesList(TASK_ID))
                .thenReturn(true);
        Replicate replicateEntity = new Replicate(WORKER_ADDRESS, TASK_ID);
        when(replicatesService.getReplicates(TASK_ID))
                .thenReturn(List.of(replicateEntity));

        ResponseEntity<TaskModel> taskResponse = taskController.getTask(TASK_ID);
        Assertions.assertTrue(taskResponse.getStatusCode().is2xxSuccessful());
        TaskModel task = taskResponse.getBody();
        Assertions.assertNotNull(task);
        Assertions.assertEquals(TASK_ID, task.getChainTaskId());
        Assertions.assertEquals(TASK_ID,
                task.getReplicates().get(0).getChainTaskId());
        Assertions.assertEquals(WORKER_ADDRESS,
                task.getReplicates().get(0).getWalletAddress());
    }

    @Test
    void shouldGetReplicate() {
        Replicate replicateEntity = new Replicate(WORKER_ADDRESS, TASK_ID);
        when(replicatesService.getReplicate(TASK_ID, WORKER_ADDRESS))
                .thenReturn(Optional.of(replicateEntity));

        ResponseEntity<ReplicateModel> replicateResponse =
                taskController.getTaskReplicate(TASK_ID, WORKER_ADDRESS);
        Assertions.assertTrue(replicateResponse.getStatusCode().is2xxSuccessful());
        ReplicateModel replicate = replicateResponse.getBody();
        Assertions.assertNotNull(replicate);
        Assertions.assertEquals(TASK_ID, replicate.getChainTaskId());
        Assertions.assertEquals(WORKER_ADDRESS, replicate.getWalletAddress());
    }

    @Test
    void shouldNotGetReplicate() {
        when(replicatesService.getReplicate(TASK_ID, WORKER_ADDRESS))
                .thenReturn(Optional.empty());

        ResponseEntity<ReplicateModel> replicateResponse =
                taskController.getTaskReplicate(TASK_ID, WORKER_ADDRESS);
        Assertions.assertTrue(replicateResponse.getStatusCode().is4xxClientError());
    }

    @Test
    void shouldBuildReplicateModel() {
        Replicate entity = mock(Replicate.class);
        when(entity.getChainTaskId()).thenReturn(TASK_ID);
        when(entity.getWalletAddress()).thenReturn(WORKER_ADDRESS);
        when(entity.isAppComputeLogsPresent()).thenReturn(false);

        ReplicateModel dto = taskController.buildReplicateModel(entity);
        Assertions.assertEquals(TASK_ID, dto.getChainTaskId());
        Assertions.assertTrue(dto.getSelf().endsWith("/tasks/0xtask/replicates/0xworker"));
        Assertions.assertNull(dto.getAppLogs());
    }

    @Test
    void shouldBuildReplicateModelWithStdout() {
        Replicate entity = mock(Replicate.class);
        when(entity.getChainTaskId()).thenReturn(TASK_ID);
        when(entity.getWalletAddress()).thenReturn(WORKER_ADDRESS);
        when(entity.isAppComputeLogsPresent()).thenReturn(true);

        ReplicateModel dto = taskController.buildReplicateModel(entity);
        Assertions.assertEquals(TASK_ID, dto.getChainTaskId());
        Assertions.assertTrue(dto.getSelf().endsWith("/tasks/0xtask/replicates/0xworker"));
        Assertions.assertTrue(dto.getAppLogs().endsWith("/tasks/0xtask/replicates/0xworker/stdout"));
    }

}