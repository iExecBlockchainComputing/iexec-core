/*
 * Copyright 2024-2025 IEXEC BLOCKCHAIN TECH
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

import com.iexec.commons.poco.chain.ChainTask;
import com.iexec.commons.poco.chain.ChainTaskStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.update.TaskUpdateRequestManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;

import static com.iexec.core.TestUtils.CHAIN_TASK_ID;
import static com.iexec.core.TestUtils.getStubTask;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class ConsensusReachedTaskDetectorTests {

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse(System.getProperty("mongo.image")));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.host", mongoDBContainer::getHost);
        registry.add("spring.data.mongodb.port", () -> mongoDBContainer.getMappedPort(27017));
    }

    @Mock
    private IexecHubService iexecHubService;
    @Mock
    private TaskService taskService;
    @Mock
    private TaskUpdateRequestManager taskUpdateRequestManager;

    @InjectMocks
    private ConsensusReachedTaskDetector detector;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        when(taskService.findByCurrentStatus(TaskStatus.RUNNING)).thenReturn(List.of(getStubTask()));
    }

    @Test
    void shouldDetectWhenOnChainTaskRevealing(CapturedOutput output) {
        final ChainTask chainTask = ChainTask.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .status(ChainTaskStatus.REVEALING)
                .build();
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));
        detector.detect();
        assertThat(output.getOut()).contains("Detected confirmed missing update (task)");
        verify(taskUpdateRequestManager).publishRequest(CHAIN_TASK_ID);
    }

    @ParameterizedTest
    @EnumSource(value = ChainTaskStatus.class, names = "REVEALING", mode = EnumSource.Mode.EXCLUDE)
    void shouldNotDetectWhenOnChainTaskNotRevealing(ChainTaskStatus chainTaskStatus, CapturedOutput output) {
        final ChainTask chainTask = ChainTask.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .status(chainTaskStatus)
                .build();
        when(iexecHubService.getChainTask(CHAIN_TASK_ID)).thenReturn(Optional.of(chainTask));
        detector.detect();
        assertThat(output.getOut()).doesNotContain("Detected confirmed missing update (task)");
        verifyNoInteractions(taskUpdateRequestManager);
    }
}
