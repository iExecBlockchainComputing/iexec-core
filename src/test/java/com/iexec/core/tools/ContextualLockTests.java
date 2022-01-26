package com.iexec.core.tools;

import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

class ContextualLockTests {
    private ContextualLock<Integer> contextualLock;

    @BeforeEach
    void init() {
        contextualLock = new ContextualLock<>(Duration.of(1, ChronoUnit.MINUTES));
    }

    @Test
    void shouldAcquireLockIfNoOtherLock() {
        Assertions.assertThat(contextualLock.tryLock(1)).isTrue();
    }

    @Test
    void shouldNotAcquireLockIfAlreadyLocked() {
        Assertions.assertThat(contextualLock.tryLock(1)).isTrue();
        CompletableFuture.runAsync(() -> Assertions.assertThat(contextualLock.tryLock(1)).isFalse()).join();
    }

    @Test
    void shouldAcquireLockIfAlreadyLockedFromSameThread() {
        Assertions.assertThat(contextualLock.tryLock(1)).isTrue();
        Assertions.assertThat(contextualLock.tryLock(1)).isTrue();
    }

    @Test
    void shouldAcquireAndReleaseLock() {
        Assertions.assertThat(contextualLock.tryLock(1)).isTrue();
        contextualLock.unlock(1);
        Assertions.assertThat(contextualLock.tryLock(1)).isTrue();
    }

    /**
     * Executes 2 threads:
     * <ul>
     *     <li>First thread will acquire lock
     *     and wait for second one to be started before releasing the lock;</li>
     *     <li>Second thread will try to acquire lock and returns once it's done.</li>
     * </ul>
     * This test will fail if there's a deadlock
     * and one or both threads can't complete.
     */
    @Test
    void shouldAcquireLockOnceUnlocked() {
        // Will be set to true once locking thread has obtained the contextual lock.
        BooleanWrapper gotFirstLock = new BooleanWrapper(false);
        // Will be set to true once the second thread has started trying to obtain the lock.
        BooleanWrapper hasTriedToLock = new BooleanWrapper(false);

        Runnable tryingToLockRunnable = () -> {
            // Just wait for other thread to be started
            // in order to be sure this thread won't acquire the lock
            Awaitility
                    .await()
                    .until(gotFirstLock::isTrue);
            while (!contextualLock.tryLock(1)) {
                hasTriedToLock.value = true;
            }
            Assertions.assertThat(contextualLock.tryLock(1)).isTrue();
        };
        Runnable lockingRunnable = () -> {
            contextualLock.tryLock(1);
            gotFirstLock.value = true;
            // Wait to be sure the other thread has started requiring the lock.
            Awaitility
                    .await()
                    .until(hasTriedToLock::isTrue);
            contextualLock.unlock(1);
        };

        final CompletableFuture<Void> lockingThread = CompletableFuture.runAsync(lockingRunnable);
        final CompletableFuture<Void> tryingToLockThread = CompletableFuture.runAsync(tryingToLockRunnable);

        Awaitility
                .await()
                .timeout(1, TimeUnit.SECONDS)
                .until(() -> lockingThread.isDone() && tryingToLockThread.isDone());
    }

    private static class BooleanWrapper {
        boolean value;

        public BooleanWrapper(boolean value) {
            this.value = value;
        }

        boolean isTrue() {
            return value;
        }
    }
}
