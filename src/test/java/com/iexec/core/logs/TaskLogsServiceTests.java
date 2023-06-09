/*
 * Copyright 2022-2023 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.replicate.ComputeLogs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.iexec.commons.poco.utils.TestUtils.CHAIN_TASK_ID;
import static com.iexec.commons.poco.utils.TestUtils.WORKER_ADDRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TaskLogsServiceTests {

    private static final String STDOUT = "This is an stdout string";
    private static final String STDERR = "This is an stderr string";

    @Mock
    private TaskLogsRepository taskLogsRepository;
    @InjectMocks
    private TaskLogsService taskLogsService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    //region addComputeLogs
    @Test
    void shouldAddComputeLogs() {
        final ComputeLogs computeLogs = new ComputeLogs(WORKER_ADDRESS, STDOUT, STDERR);

        ArgumentCaptor<TaskLogs> argumentCaptor = ArgumentCaptor.forClass(TaskLogs.class);
        taskLogsService.addComputeLogs(CHAIN_TASK_ID, computeLogs);
        verify(taskLogsRepository, times(1)).save(argumentCaptor.capture());
        TaskLogs capturedEvent = argumentCaptor.getAllValues().get(0);
        assertThat(capturedEvent.getComputeLogsList().get(0).getStdout()).isEqualTo(STDOUT);
        assertThat(capturedEvent.getComputeLogsList().get(0).getStderr()).isEqualTo(STDERR);
        assertThat(capturedEvent.getComputeLogsList().get(0).getWalletAddress()).isEqualTo(WORKER_ADDRESS);
    }

    @Test
    void shouldNotAddComputeLogsSinceNull() {
        taskLogsService.addComputeLogs(CHAIN_TASK_ID, null);
        verifyNoInteractions(taskLogsRepository);
    }

    @Test
    void shouldNotAddComputeLogsSinceLogsAlreadyKnown() {
        final ComputeLogs computeLogs = new ComputeLogs(WORKER_ADDRESS, STDOUT, STDERR);
        final TaskLogs taskLogs = TaskLogs.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .computeLogsList(Collections.singletonList(computeLogs))
                .build();

        when(taskLogsService.getTaskLogs(CHAIN_TASK_ID))
                .thenReturn(Optional.of(taskLogs));

        taskLogsService.addComputeLogs(CHAIN_TASK_ID, computeLogs);

        verify(taskLogsRepository).findOneByChainTaskId(CHAIN_TASK_ID);
        verify(taskLogsRepository, times(0)).save(any());
    }
    //endregion

    //region getComputeLogs
    @Test
    void shouldGetComputeLogs() {
        ComputeLogs computeLogs = new ComputeLogs(WORKER_ADDRESS, STDOUT, STDERR);
        TaskLogs taskLogs = TaskLogs.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .computeLogsList(List.of(computeLogs))
                .build();
        when(taskLogsRepository.findByChainTaskIdAndWalletAddress(CHAIN_TASK_ID, WORKER_ADDRESS))
                .thenReturn(Optional.of(taskLogs));
        Optional<ComputeLogs> optional = taskLogsService.getComputeLogs(CHAIN_TASK_ID, WORKER_ADDRESS);
        assertThat(optional).isPresent();
        final ComputeLogs actualLogs = optional.get();
        assertThat(actualLogs.getStdout()).isEqualTo(STDOUT);
        assertThat(actualLogs.getStderr()).isEqualTo(STDERR);
    }
    //endregion
}