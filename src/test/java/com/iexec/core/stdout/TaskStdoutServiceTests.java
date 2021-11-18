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

package com.iexec.core.stdout;

import static com.iexec.common.utils.TestUtils.CHAIN_TASK_ID;
import static com.iexec.common.utils.TestUtils.WORKER_ADDRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.iexec.core.task.TaskService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TaskStdoutServiceTests {

    private static final String STDOUT = "This is an stdout string";

    @Mock
    private StdoutRepository stdoutRepository;
    @Mock
    private TaskService taskService;
    @InjectMocks
    private StdoutService stdoutService;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldAddReplicateStdout() {
        ArgumentCaptor<TaskStdout> argumentCaptor = ArgumentCaptor.forClass(TaskStdout.class);
        stdoutService.addReplicateStdout(CHAIN_TASK_ID, WORKER_ADDRESS, STDOUT);
        verify(stdoutRepository, times(1)).save(argumentCaptor.capture());
        TaskStdout capturedEvent = argumentCaptor.getAllValues().get(0);
        assertThat(capturedEvent.getReplicateStdoutList().get(0).getStdout()).isEqualTo(STDOUT);
        assertThat(capturedEvent.getReplicateStdoutList().get(0).getWalletAddress()).isEqualTo(WORKER_ADDRESS);
    }

    @Test
    public void shouldGetReplicateStdout() {
        ReplicateStdout replicateStdout = new ReplicateStdout(WORKER_ADDRESS, STDOUT);
        TaskStdout taskStdout = TaskStdout.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .replicateStdoutList(List.of(replicateStdout))
                .build();
        when(stdoutRepository.findByChainTaskIdAndWalletAddress(CHAIN_TASK_ID, WORKER_ADDRESS))
                .thenReturn(Optional.of(taskStdout));
        Optional<ReplicateStdout> optional = stdoutService.getReplicateStdout(CHAIN_TASK_ID, WORKER_ADDRESS);
        assertThat(optional.get().getStdout()).isEqualTo(STDOUT);
    }
}