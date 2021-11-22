package com.iexec.core.task.update;

import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;

public class TaskUpdateRequestManagerTests {


    public static final String CHAIN_TASK_ID = "chainTaskId";
    @InjectMocks
    private TaskUpdateRequestManager taskUpdateRequestManager;

    @BeforeEach
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

    @Test
    public void shouldNotUpdateAtTheSameTime() throws NoSuchFieldException, IllegalAccessException {
        final ConcurrentLinkedQueue<Integer> callsOrder = new ConcurrentLinkedQueue<>();
        final ConcurrentHashMap<Integer, String> taskForUpdateId = new ConcurrentHashMap<>();
        final int callsPerUpdate = 10;
        final List<String> updates = List.of("1", "1", "2", "2", "1");
        final Random random = new Random();

        // Consuming a task update should only log the call a few times, while sleeping between each log
        // so that another task could be updated at the same time if authorized by lock.
        final TaskUpdateRequestConsumer consumer = chainTaskId -> {
            final int updateId = (int)System.nanoTime() % Integer.MAX_VALUE;
            taskForUpdateId.put(updateId, chainTaskId);
            for (int i = 0; i < callsPerUpdate; i++) {
                try {
                    TimeUnit.MILLISECONDS.sleep(random.nextInt(10));
                } catch (InterruptedException ignored) {}
                callsOrder.add(updateId);
            }
        };
        taskUpdateRequestManager.setRequestConsumer(consumer);

        // Ugly way to put some task updates in waiting queue.
        final Field queueField = TaskUpdateRequestManager.class
                .getDeclaredField("queue");
        queueField.setAccessible(true);
        //noinspection unchecked
        final BlockingQueue<String> queue = (BlockingQueue<String>) queueField.get(taskUpdateRequestManager);
        queue.addAll(updates);

        // We need to run this method as a new thread, so we can interrupt it after all tasks have run.
        final CompletableFuture<Void> asyncRun = CompletableFuture.runAsync(taskUpdateRequestManager::consumeAndNotify);

        Awaitility
                .await()
                .timeout(30, TimeUnit.SECONDS)
                .until(() -> callsOrder.size() == callsPerUpdate * updates.size());

        asyncRun.cancel(true);

        Assertions.assertThat(callsOrder.size()).isEqualTo(callsPerUpdate * updates.size());

        // We loop through calls order and see if all calls for a given update have finished
        // before another update starts for this task.
        // Two updates for different tasks can run at the same time.
        Map<String, Map<Integer, Integer>> foundTaskUpdates = new HashMap<>();

        for (int updateId : callsOrder) {
            System.out.println("[taskId:" + taskForUpdateId.get(updateId) + ", updateId:" + updateId + "]");
            final Map<Integer, Integer> foundOutputsForKeyGroup = foundTaskUpdates.computeIfAbsent(taskForUpdateId.get(updateId), (key) -> new HashMap<>());
            for (int alreadyFound : foundOutputsForKeyGroup.keySet()) {
                if (!Objects.equals(alreadyFound, updateId) && foundOutputsForKeyGroup.get(alreadyFound) < callsPerUpdate) {
                    Assertions.fail("Synchronization has failed: %s has only %s out of %s occurrences while %s has been inserted.",
                            alreadyFound, foundOutputsForKeyGroup.get(alreadyFound), callsPerUpdate, updateId);
                }
            }

            foundOutputsForKeyGroup.merge(updateId, 1, (currentValue, defaultValue) -> currentValue + 1);
        }
    }
}
