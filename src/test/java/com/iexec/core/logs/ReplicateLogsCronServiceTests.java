package com.iexec.core.logs;

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

class ComputeLogsCronServiceTests {

    @Mock
    private TaskLogsService taskLogsService;

    @Mock
    private TaskService taskService;

    @InjectMocks
    private ComputeLogsCronService computeLogsCronService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldCleanStdout() {
        List<String> ids = List.of("id1", "id2");
        when(taskService.getChainTaskIdsOfTasksExpiredBefore(any()))
                .thenReturn(ids);
        computeLogsCronService.purgeLogs();
        verify(taskLogsService).delete(ids);
    }
}
