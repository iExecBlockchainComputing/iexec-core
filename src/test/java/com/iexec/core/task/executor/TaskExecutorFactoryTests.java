package com.iexec.core.task.executor;

import org.junit.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class TaskExecutorFactoryTests {

    private TaskExecutorFactory taskExecutorFactory = new TaskExecutorFactory();

    // getOrCreate

    @Test
    public void shouldCreateNewTaskExecutorForNewTask() {
        assertThat(
                taskExecutorFactory.getOrCreate("chainTaskId", 1000)
        ).isPresent();
    }

    @Test
    public void shouldGetSameTaskExecutorForSameTask() {
        String chainTaskId = "chainTaskId";
        long expiration = 1000; // ms
        Optional<ThreadPoolTaskExecutor> created =
                taskExecutorFactory.getOrCreate(chainTaskId, expiration);

        Optional<ThreadPoolTaskExecutor> expected =
                taskExecutorFactory.getOrCreate(chainTaskId, expiration);

        assertThat(expected.get() == created.get()).isTrue();
    }

    @Test
    public void shouldGetDifferentTaskExecutorsForDifferentTasks() {
        String chainTaskId1 = "chainTaskId1";
        String chainTaskId2 = "chainTaskId2";
        long expiration = 1000; // ms
        Optional<ThreadPoolTaskExecutor> executor1 =
                taskExecutorFactory.getOrCreate(chainTaskId1, expiration);

        Optional<ThreadPoolTaskExecutor> executor2 =
                taskExecutorFactory.getOrCreate(chainTaskId2, expiration);

        assertThat(executor1).isNotEqualTo(executor2);
    }

    @Test
    public void shouldNotCreateTaskExecutorSinceExpirationIsZero() {
        assertThat(
                taskExecutorFactory.getOrCreate("chainTaskId", 0)
        ).isEmpty();
    }

    @Test
    public void shouldRemoveTaskExecutorAfterExpirationDate() throws Exception {
        int oneSecond = 1;
        int twoSeconds = 2;
        Instant start = Instant.now();
        Instant expirationDate = start.plus(oneSecond, ChronoUnit.SECONDS);
        Instant taskEndDate = start.plus(twoSeconds, ChronoUnit.SECONDS);
        long oneSecondExpiration = TimeUnit.SECONDS.toMillis(oneSecond);
        // get an executor
        Optional<ThreadPoolTaskExecutor> executor =
                taskExecutorFactory.getOrCreate("chainTaskId", oneSecondExpiration);
        // run long task in executor
        CompletableFuture
                .runAsync(() -> sleep(twoSeconds), executor.get())
                .get();
        // check that current date is after expiration
        // date and before task's end date
        Instant now = Instant.now();
        assertThat(now).isAfter(expirationDate);
        assertThat(now).isBefore(taskEndDate);
    }

    @Test
    public void shouldRemoveTaskExecutor() throws Exception {
        String chainTaskId = "chainTaskId";
        int twoSeconds = 2;
        Instant start = Instant.now();
        Instant taskEndDate = start.plus(twoSeconds, ChronoUnit.SECONDS);
        // long oneSecondExpiration = TimeUnit.SECONDS.toMillis(twoSeconds);
        // get an executor
        Optional<ThreadPoolTaskExecutor> executor =
                taskExecutorFactory.getOrCreate(chainTaskId, 3000); // 3s
        // run long task in executor
        CompletableFuture<Void> future = CompletableFuture
                .runAsync(() -> sleep(twoSeconds), executor.get());
        // remove the executor
        taskExecutorFactory.remove(chainTaskId);
        // wait for the executor to finish
        // it should finish immediately and
        // not wait for the task to end
        future.get();
        // check that current date is before task's end date
        Instant now = Instant.now();
        assertThat(now).isBefore(taskEndDate);
    }

    private void sleep(int seconds) {
        try {
            System.out.println("Sleeping...");
            TimeUnit.SECONDS.sleep(seconds);
            System.out.println("Waking...");
        } catch (Exception e) {
            System.out.println("Interrupted!");
        }
    }
}
