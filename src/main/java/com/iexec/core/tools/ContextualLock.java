/*
 * Copyright 2022 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.tools;

import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpiringMap;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a map of locks associated to keys.
 *
 * @param <K> Type of the key.
 */
@Slf4j
public class ContextualLock<K> {
    private final Map<K, Lock> locks;

    public ContextualLock(Duration lockDuration) {
        locks = ExpiringMap.builder()
                .expiration(lockDuration.getSeconds(), TimeUnit.SECONDS)
                .build();
    }

    /**
     * Gets a lock on a particular key
     * if no lock from another thread currently exists on that key.
     *
     * @param key Key to lock on.
     * @return {@literal true} if the lock has been acquired,
     * {@literal false} otherwise.
     */
    public boolean tryLock(K key) {
        final Lock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        return lock.tryLock();
    }

    /**
     * Unlocks the lock on the given key.
     *
     * @param key Key whose lock is associated to.
     */
    public void unlock(K key) {
        final Lock lock = locks.get(key);
        if (lock != null) {
            lock.unlock();
        }
    }
}
