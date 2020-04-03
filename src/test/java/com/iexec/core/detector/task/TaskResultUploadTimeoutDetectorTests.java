package com.iexec.core.detector.task;

import com.iexec.core.task.Task;
import com.iexec.core.task.TaskExecutorEngine;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.common.utils.DateTimeUtils;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class TaskResultUploadTimeoutDetectorTests {

    @Mock
    private TaskService taskService;

    @Mock
    private TaskExecutorEngine taskExecutorEngine;

    @InjectMocks
    private TaskResultUploadTimeoutDetector taskResultUploadTimeoutDetector;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }


    @Test
    public void shouldDetectResultUploadTimeout() {
        String chainTaskId = "chainTaskId";
        Date oneMinuteBeforeNow = DateTimeUtils.addMinutesToDate(new Date(), -1);

        Task task = Task.builder()
                .chainTaskId(chainTaskId)
                .finalDeadline(oneMinuteBeforeNow)
                .build();

        List<TaskStatus> statuses = Arrays.asList(TaskStatus.RESULT_UPLOAD_REQUESTED,
                TaskStatus.RESULT_UPLOADING);

        when(taskService.findByCurrentStatus(statuses))
                .thenReturn(Collections.singletonList(task));

        taskResultUploadTimeoutDetector.detect();

        Mockito.verify(taskExecutorEngine, Mockito.times(1)).updateTask(chainTaskId); 
    }

    @Test
    public void shouldNotDetectResultUploadTimeoutSinceStillBeforeDeadline() {
        String chainTaskId = "chainTaskId";
        Date oneMinuteBeforeNow = DateTimeUtils.addMinutesToDate(new Date(), 1);

        Task task = Task.builder()
                .chainTaskId(chainTaskId)
                .finalDeadline(oneMinuteBeforeNow)
                .build();

        List<TaskStatus> statuses = Arrays.asList(TaskStatus.RESULT_UPLOAD_REQUESTED,
                TaskStatus.RESULT_UPLOADING);

        when(taskService.findByCurrentStatus(statuses))
                .thenReturn(Collections.singletonList(task));

        taskResultUploadTimeoutDetector.detect();

        Mockito.verify(taskExecutorEngine, Mockito.times(0)).updateTask(chainTaskId); 
    }

}