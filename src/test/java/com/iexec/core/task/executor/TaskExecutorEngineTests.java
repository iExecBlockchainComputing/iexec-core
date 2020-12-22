package com.iexec.core.task.executor;

import com.iexec.core.task.Task;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.utils.TaskExecutorUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TaskExecutorEngineTests {

    @Mock
    private TaskExecutorFactory taskExecutorFactory;

    @InjectMocks
    private TaskExecutorEngine taskExecutorEngine;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldStartRunnable() throws Exception {
        String chainTaskId = "chainTaskId";
        Task task = Task.builder()
                .chainTaskId(chainTaskId)
                .dateStatusList(new ArrayList<>())
                .build();
        task.changeStatus(TaskStatus.INITIALIZING);
        long expiration = 5;
        ThreadPoolTaskExecutor executor =
                TaskExecutorUtils.singleThreadWithFixedSizeQueue(1, "threadNamePrefix");
        Runnable runnable = () -> task.changeStatus(TaskStatus.INITIALIZED);
        when(taskExecutorFactory.getOrCreate(chainTaskId, expiration))
                .thenReturn(Optional.of(executor));

        CompletableFuture<Void> f =
                taskExecutorEngine.run(chainTaskId, expiration, runnable);
        TimeUnit.MILLISECONDS.sleep(10);
        f.get();
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.INITIALIZED);
    }

    @Test
    public void shouldNotStartRunnableSinceNoExecutor() throws Exception {
        String chainTaskId = "chainTaskId";
        Task task = Task.builder()
                .chainTaskId(chainTaskId)
                .dateStatusList(new ArrayList<>())
                .build();
        task.changeStatus(TaskStatus.INITIALIZING);
        long expiration = 5;
        Runnable runnable = () -> task.changeStatus(TaskStatus.INITIALIZED);
        when(taskExecutorFactory.getOrCreate(chainTaskId, expiration))
                .thenReturn(Optional.empty());

        CompletableFuture<Void> f =
                taskExecutorEngine.run(chainTaskId, expiration, runnable);
        f.get();
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.INITIALIZING);
    }

    @Test
    public void shouldRemoveExecutor() {
        String chainTaskId = "chainTaskId";
        taskExecutorEngine.removeExecutor(chainTaskId);
        verify(taskExecutorFactory).remove(chainTaskId);
    }
}
