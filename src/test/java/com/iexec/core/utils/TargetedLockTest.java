package com.iexec.core.utils;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TargetedLockTest {
    /**
     * Run an action {@code numberOfRunsPerGroup * numberOfActions} times.
     * <br>
     * There are {@code numberOfActions} groups of actions, every action in a group having the same input.
     * Every group is run {@code numberOfRunsPerGroup} times.
     *
     * @param <K> Type of the lock key.
     */
    private <K> void runWithLock(int numberOfRunsPerGroup,
                                 int numberOfActions,
                                 Function<Integer, K> keyProvider) throws InterruptedException, ExecutionException {
        final TargetedLock<K> locks = new TargetedLock<>();

        final ConcurrentLinkedQueue<Integer> actionsOrder = new ConcurrentLinkedQueue<>();
        // Run the given action a bunch of times.
        final Consumer<Integer> runActionAndGetOrder = (i) -> {
            for (int j = 0; j < numberOfRunsPerGroup; j++) {
                actionsOrder.add(i);
            }
        };

        // Run the action a bunch of times with different input and potentially different key each time.
        // Wait for the completion of all actions.
        final List<CompletableFuture<Void>> asyncTasks = IntStream.range(0, numberOfActions)
                .parallel()
                .mapToObj(i -> locks.runAsyncWithLock(keyProvider.apply(i), () -> runActionAndGetOrder.accept(i)))
                .collect(Collectors.toList());

        for (CompletableFuture<?> asyncTask : asyncTasks) {
            asyncTask.get();
        }

        // We loop through calls order and see if all calls for a given action have finished
        // before another action with the same key starts for this task.
        // Two action with different keys should be able to run at the same time.
        final Map<K, Map<Integer, Integer>> callsOrder = new HashMap<>();

        for (int index : actionsOrder) {
            final Map<Integer, Integer> foundOutputsForKeyGroup = callsOrder.computeIfAbsent(keyProvider.apply(index), (key) -> new HashMap<>());
            for (int alreadyFound : foundOutputsForKeyGroup.keySet()) {
                if (!Objects.equals(alreadyFound, index) && foundOutputsForKeyGroup.get(alreadyFound) < numberOfRunsPerGroup) {
                    Assertions.fail("Synchronization has failed: %s has only %s out of %s occurrences while %s has been inserted.",
                            alreadyFound, foundOutputsForKeyGroup.get(alreadyFound), numberOfRunsPerGroup, index);
                }
            }

            foundOutputsForKeyGroup.merge(index, 1, (currentValue, defaultValue) -> currentValue + 1);
        }

        // At the end of the execution, no lock should remain.
        // Otherwise, that could be a memory leak.
        locks.clearReleasedLocks();
        Assertions.assertThat(locks.hasCurrentLocks()).isFalse();
    }

    /**
     * A constant value is used to lock all operations:
     * final order should be a list within which each value should not interfere with others.
     * E.g. [2, 2, 2, 1, 1, 1, 0, 0, 0, 3, 3, 3] would be an expected result,
     * however [2, <b>1</b>, 2, 2, 1, 1, 0, 0, 0, 3, 3, 3] would not be an expected result as 1 interferes with 2.
     */
    @Test
    public void runWithLockOnConstantValue() throws ExecutionException, InterruptedException {
        runWithLock(100, 100, i -> true);
    }

    /**
     * Parity is used to lock all operations:
     * final order should be a list within which each value should not interfere with others of same parity.
     * E.g. [2, 1, 2, 2, 0, 1, 1, 3, 0, 0, 0, 3, 3] would be an expected result,
     * however [2, 1, 2, 2, 1, <b>3</b>, 1, 0, 0, 0, 3, 3] would not be an expected result as 3 interferes with 1.
     */
    @Test
    public void runWithLockOnParity() throws ExecutionException, InterruptedException {
        runWithLock(100, 100, i -> i % 2 == 0);
    }

    /**
     * Operation's value are used to lock operations:
     * any order is then acceptable as we don't care about interferences between distinct operations.
     */
    @Test
    public void runWithLockPerValue() throws ExecutionException, InterruptedException {
        runWithLock(100, 100, Function.identity());
    }

    /**
     * Simple test to verify that we get a result if we try to run a function.
     */
    @Test
    public void getResultOfAsyncFunctionRunWithLock() throws ExecutionException, InterruptedException {
        final String key = "key";

        final TargetedLock<String> locks = new TargetedLock<>();
        final String result = locks.runAsyncWithLock(key, s -> s + s).get();
        Assertions.assertThat(result).isEqualTo(key + key);
    }

    /**
     * Simple test to verify that we get a result if we try to run a supplier.
     */
    @Test
    public void getResultOfAsyncSupplierRunWithLock() throws ExecutionException, InterruptedException {
        final String key = "key";
        final String expectedResult = "result";

        final TargetedLock<String> locks = new TargetedLock<>();
        final String actualResult = locks.runAsyncWithLock(key, () -> expectedResult).get();
        Assertions.assertThat(actualResult).isEqualTo(expectedResult);
    }
}