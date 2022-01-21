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

import com.iexec.common.utils.ContextualLockRunner;
import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpiringMap;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ContextualLock<K> {
    private final ContextualLockRunner<K> contextualLockRunner =
            new ContextualLockRunner<>(1, TimeUnit.MINUTES);

    private final Map<K, Boolean> locks;

    public ContextualLock(Duration lockDuration) {
        locks = ExpiringMap.builder()
                .expiration(lockDuration.getSeconds(), TimeUnit.SECONDS)
                .build();
    }

    /**
     * Gets a lock on a particular key if no lock currently exists on that key.
     * <p>
     * This method is synchronized on the key,
     * so we're sure there shouldn't be any race condition.
     *
     * @param key Key to lock on.
     * @return {@literal true} if the lock has been acquired,
     * {@literal false} otherwise.
     */
    public boolean lockIfPossible(K key) {
        // Reading and writing should be synchronized,
        // otherwise 2 different threads could get a `no lock` reply
        // and then try to lock on the same key.
        //
        // This may be solved with a simple `synchronized`
        // but that would mean all threads would wait
        // if a single one is trying to lock.
        return contextualLockRunner.getWithLock(key, () -> {
            if (Boolean.TRUE.equals(locks.getOrDefault(key, false))) {
                return false;
            }

            // If `setLock` returns `true`,
            // that means another thread had already locked on that key
            // which shouldn't be possible.
            if (setLock(key, true)) {
                log.warn("Can't set lock when it is already set. " +
                                "This should not happen. " +
                                "Please check there's no race condition.",
                        new Exception());
                return false;
            }
            return true;
        });
    }

    public void unlock(K key) {
        setLock(key, false);
    }

    /**
     * Sets the lock value for a given key.
     *
     * @param key        Key to set/unset the lock on.
     * @param shouldLock Whether to set or unset the lock.
     * @return {@literal true} if the lock was previously set,
     * {@literal false} otherwise.
     */
    private boolean setLock(K key, boolean shouldLock) {
        return Boolean.TRUE.equals(locks.put(key, shouldLock));
    }
}
