/*
 * Copyright 2021 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.core.utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Provide a way to avoid parallel runs of an action.
 * Locks are based on a key, so that two actions with the same key can't be run at the same time
 * but two actions with different keys can be run at the same time.
 * <br>
 * In order to avoid memory leaks, {@link TargetedLock#clearReleasedLocks()} should be regularly called.
 *
 * @param <K> Type of the key.
 */
public class TargetedLock<K> {
    private final ConcurrentHashMap<K, Lock> locks = new ConcurrentHashMap<>();

    public <R> R runWithLock(K key, Supplier<R> action) {
        // `ConcurrentHashMap::computeIfAbsent` is atomic, so there can't be any race condition there.
        synchronized (locks.computeIfAbsent(key, id -> new Lock())) {
            locks.get(key).lock();
            final R result = action.get();
            locks.get(key).release();
            return result;
        }
    }

    public void runWithLock(K key, Runnable action) {
        runWithLock(key, () -> {
            action.run();
            return null;
        });
    }

    public <R> R runWithLock(K key, Function<K, R> action) {
        return runWithLock(key, () -> action.apply(key));
    }

    public CompletableFuture<Void> runAsyncWithLock(K key, Runnable action) {
        return CompletableFuture.runAsync(() -> runWithLock(key, action));
    }

    public <R> CompletableFuture<R> runAsyncWithLock(K key, Supplier<R> action) {
        return CompletableFuture.supplyAsync(() -> runWithLock(key, action));
    }

    public <R> CompletableFuture<R> runAsyncWithLock(K key, Function<K, R> action) {
        return CompletableFuture.supplyAsync(() -> runWithLock(key, action));
    }

    public boolean hasCurrentLocks() {
        return !locks.isEmpty();
    }

    /**
     * Clear all released locks.
     * This method should be called regularly in order to avoid memory leaks.
     */
    public void clearReleasedLocks() {
        locks.keySet()
                .stream()
                .filter(key -> !locks.get(key).isLocked())
                .forEach(locks::remove);
    }

    /**
     * Simple lock object.
     * Synchronize its internal state so that it can't be locked or unlocked by two process at the same time.
     */
    private static final class Lock {
        private Boolean isLocked;
        private final Object internalLock = new Object();

        public boolean isLocked() {
            return isLocked;
        }

        public void release() {
            synchronized (internalLock) {
                isLocked = false;
            }
        }

        public void lock() {
            synchronized (internalLock) {
                isLocked = true;
            }
        }
    }
}
