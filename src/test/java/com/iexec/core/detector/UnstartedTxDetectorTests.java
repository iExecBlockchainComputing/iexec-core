package com.iexec.core.detector;

import com.iexec.core.detector.task.UnstartedTxDetector;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskExecutorEngine;
import com.iexec.core.task.TaskService;
import org.junit.Before;
import org.junit.Test;
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
    private TaskExecutorEngine taskExecutorEngine;

    @InjectMocks
    private UnstartedTxDetector unstartedTxDetector;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldTryUpdateTaskFromReceived() {
        Task task = Task.builder()
                .chainDealId("0x1")
                .taskIndex(0)
                .build();
        when(taskService.findByCurrentStatus(RECEIVED)).thenReturn(Collections.singletonList(task));

        unstartedTxDetector.detect();

        Mockito.verify(taskExecutorEngine, Mockito.times(1))
                .updateTask(task.getChainTaskId());
    }

    @Test
    public void shouldNotTryUpdateTaskFromReceived() {
        Task task = Task.builder()
                .chainDealId("0x1")
                .taskIndex(0)
                .build();
        when(taskService.findByCurrentStatus(INITIALIZING)).thenReturn(Collections.singletonList(task));

        unstartedTxDetector.detect();

        Mockito.verify(taskExecutorEngine, Mockito.times(0))
                .updateTask(task.getChainTaskId());
    }

    @Test
    public void shouldTryUpdateTaskFromResultUploaded() {
        Task task = Task.builder()
                .chainDealId("0x1")
                .taskIndex(0)
                .build();
        when(taskService.findByCurrentStatus(RESULT_UPLOADED)).thenReturn(Collections.singletonList(task));

        unstartedTxDetector.detect();

        Mockito.verify(taskExecutorEngine, Mockito.times(1))
                .updateTask(task.getChainTaskId());
    }

    @Test
    public void shouldNotTryUpdateTaskFromResultUploaded() {
        Task task = Task.builder()
                .chainDealId("0x1")
                .taskIndex(0)
                .build();
        when(taskService.findByCurrentStatus(FINALIZING)).thenReturn(Collections.singletonList(task));

        unstartedTxDetector.detect();

        Mockito.verify(taskExecutorEngine, Mockito.times(0))
                .updateTask(task.getChainTaskId());
    }


}
