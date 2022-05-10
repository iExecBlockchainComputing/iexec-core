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

class ReplicateLogsCronServiceTests {

    @Mock
    private ReplicateLogsService replicateLogsService;

    @Mock
    private TaskService taskService;

    @InjectMocks
    private ReplicateLogsCronService replicateLogsCronService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldCleanStdout() {
        List<String> ids = List.of("id1", "id2");
        when(taskService.getChainTaskIdsOfTasksExpiredBefore(any()))
                .thenReturn(ids);
        replicateLogsCronService.purgeLogs();
        verify(replicateLogsService).delete(ids);
    }
}
