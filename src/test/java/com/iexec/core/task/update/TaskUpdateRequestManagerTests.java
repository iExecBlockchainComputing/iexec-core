package com.iexec.core.task.update;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class TaskUpdateRequestManagerTests {


    public static final String CHAIN_TASK_ID = "chainTaskId";
    @InjectMocks
    private TaskUpdateRequestManager taskUpdateRequestManager;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldPublishRequest() throws ExecutionException, InterruptedException {
        CompletableFuture<Boolean> booleanCompletableFuture = taskUpdateRequestManager.publishRequest(CHAIN_TASK_ID);
        booleanCompletableFuture.join();
        Assertions.assertThat(booleanCompletableFuture.get()).isTrue();
    }

    @Test
    public void shouldNotPublishRequestSinceEmptyTaskId() throws ExecutionException, InterruptedException {
        CompletableFuture<Boolean> booleanCompletableFuture = taskUpdateRequestManager.publishRequest("");
        booleanCompletableFuture.join();
        Assertions.assertThat(booleanCompletableFuture.get()).isFalse();
    }

    @Test
    public void shouldNotPublishRequestSinceItemAlreadyAdded() throws ExecutionException, InterruptedException {
        taskUpdateRequestManager.publishRequest(CHAIN_TASK_ID);
        CompletableFuture<Boolean> booleanCompletableFuture = taskUpdateRequestManager.publishRequest(CHAIN_TASK_ID);
        booleanCompletableFuture.join();
        Assertions.assertThat(booleanCompletableFuture.get()).isFalse();
    }

}
