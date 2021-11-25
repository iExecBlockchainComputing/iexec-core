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

import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskUpdateManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static com.iexec.core.task.TaskStatus.*;
import static org.mockito.Mockito.when;

public class UnstartedTxDetectorTests {

    @Mock
    private TaskService taskService;

    @Mock
    private TaskUpdateManager taskUpdateManager;

    @InjectMocks
    private UnstartedTxDetector unstartedTxDetector;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldTryUpdateTaskFromReceived() {
        Task task = Task.builder()
                .chainDealId("0x1")
                .taskIndex(0)
                .build();
        when(taskService.getInitializableTasks()).thenReturn(Collections.singletonList(task));

        unstartedTxDetector.detect();

        Mockito.verify(taskUpdateManager, Mockito.times(1))
                .publishUpdateTaskRequest(task.getChainTaskId());
    }

    @Test
    public void shouldNotTryUpdateTaskFromReceived() {
        Task task = Task.builder()
                .chainDealId("0x1")
                .taskIndex(0)
                .build();
        when(taskService.findByCurrentStatus(INITIALIZING)).thenReturn(Collections.singletonList(task));

        unstartedTxDetector.detect();

        Mockito.verify(taskUpdateManager, Mockito.times(0))
                .publishUpdateTaskRequest(task.getChainTaskId());
    }

    @Test
    public void shouldTryUpdateTaskFromResultUploaded() {
        Task task = Task.builder()
                .chainDealId("0x1")
                .taskIndex(0)
                .build();
        when(taskService.findByCurrentStatus(RESULT_UPLOADED)).thenReturn(Collections.singletonList(task));

        unstartedTxDetector.detect();

        Mockito.verify(taskUpdateManager, Mockito.times(1))
                .publishUpdateTaskRequest(task.getChainTaskId());
    }

    @Test
    public void shouldNotTryUpdateTaskFromResultUploaded() {
        Task task = Task.builder()
                .chainDealId("0x1")
                .taskIndex(0)
                .build();
        when(taskService.findByCurrentStatus(FINALIZING)).thenReturn(Collections.singletonList(task));

        unstartedTxDetector.detect();

        Mockito.verify(taskUpdateManager, Mockito.times(0))
                .publishUpdateTaskRequest(task.getChainTaskId());
    }


}
