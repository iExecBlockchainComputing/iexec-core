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

package com.iexec.core.logs;

import static com.iexec.common.utils.TestUtils.CHAIN_TASK_ID;
import static com.iexec.common.utils.TestUtils.WORKER_ADDRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.iexec.common.replicate.ComputeLogs;
import com.iexec.core.task.TaskService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class TaskComputeLogsServiceTests {

    private static final String STDOUT = "This is an stdout string";
    private static final String STDERR = "This is an stderr string";

    @Mock
    private ComputeLogsRepository computeLogsRepository;
    @Mock
    private TaskService taskService;
    @InjectMocks
    private ComputeLogsService computeLogsService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldAddComputeLogs() {
        final ComputeLogs computeLogs = new ComputeLogs(WORKER_ADDRESS, STDOUT, STDERR);

        ArgumentCaptor<TaskLogs> argumentCaptor = ArgumentCaptor.forClass(TaskLogs.class);
        computeLogsService.addComputeLogs(CHAIN_TASK_ID, computeLogs);
        verify(computeLogsRepository, times(1)).save(argumentCaptor.capture());
        TaskLogs capturedEvent = argumentCaptor.getAllValues().get(0);
        assertThat(capturedEvent.getComputeLogsList().get(0).getStdout()).isEqualTo(STDOUT);
        assertThat(capturedEvent.getComputeLogsList().get(0).getStderr()).isEqualTo(STDERR);
        assertThat(capturedEvent.getComputeLogsList().get(0).getWalletAddress()).isEqualTo(WORKER_ADDRESS);
    }

    @Test
    void shouldGetComputeLogs() {
        ComputeLogs computeLogs = new ComputeLogs(WORKER_ADDRESS, STDOUT, STDERR);
        TaskLogs taskLogs = TaskLogs.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .computeLogsList(List.of(computeLogs))
                .build();
        when(computeLogsRepository.findByChainTaskIdAndWalletAddress(CHAIN_TASK_ID, WORKER_ADDRESS))
                .thenReturn(Optional.of(taskLogs));
        Optional<ComputeLogs> optional = computeLogsService.getComputeLogs(CHAIN_TASK_ID, WORKER_ADDRESS);
        assertThat(optional).isPresent();
        final ComputeLogs actualLogs = optional.get();
        assertThat(actualLogs.getStdout()).isEqualTo(STDOUT);
        assertThat(actualLogs.getStderr()).isEqualTo(STDERR);
    }
}