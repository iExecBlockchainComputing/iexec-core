package com.iexec.core.stdout;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.iexec.core.task.TaskService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class StdoutCronServiceTests {

    @Mock
    private StdoutService stdoutService;

    @Mock
    private TaskService taskService;

    @InjectMocks
    private StdoutCronService stdoutCronService;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldCleanStdout() {
        List<String> ids = List.of("id1", "id2");
        when(taskService.getChainTaskIdsOfTasksExpiredBefore(any()))
                .thenReturn(ids);
        stdoutCronService.purgeStdout();
        verify(stdoutService).delete(ids);
    }
}
