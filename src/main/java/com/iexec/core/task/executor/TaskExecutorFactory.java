/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.task.executor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.Date;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.iexec.core.utils.TaskExecutorUtils;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpirationListener;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;

/**
 * A factory that manages expiring thread executors.
 * Each executor has its own expiration period.
 */
@Component
@Slf4j
class TaskExecutorFactory {

    // this map is thread-safe
    private final ExpiringMap<String, ThreadPoolTaskExecutor> map;

    TaskExecutorFactory() {
        this.map = ExpiringMap.builder()
                .expirationPolicy(ExpirationPolicy.CREATED)
                .variableExpiration()
                // shutdown thread executor when an entry expires
                .expirationListener(
                        new ShutdownExecuterExpirationListener()
                ).build();
    }

    /**
     * Get a task's executor or create a new one if needed.
     * The executor has an expiration period after which it
     * will be shutdown and removed.
     * 
     * @param chainTaskId id associated to executor
     * @param maxTtl max time to live for this executor
     * @return the executor
     */
    Optional<ThreadPoolTaskExecutor> getOrCreate(String chainTaskId, long expiration) {
        if (expiration <= 0) {
            log.error("Cannot create executor with negative expiration " +
                    "[chainTaskId:{}, expiration:{}]", chainTaskId, expiration);
            return Optional.empty();
        }
        if (map.containsKey(chainTaskId)) {
            return Optional.of(map.get(chainTaskId));
        }
        String threadNamePrefix = chainTaskId.substring(0, 9);
        map.put(
                chainTaskId,
                TaskExecutorUtils.singleThreadWithFixedSizeQueue(1, threadNamePrefix)
        );
        map.setExpiration(chainTaskId, expiration, MILLISECONDS);
        Date expirationDate = new Date(new Date().getTime() + expiration);
        log.info("Created new task executor [chainTaskId:{}, expiration:{}]",
                chainTaskId, expirationDate);
        return Optional.of(map.get(chainTaskId));
    }

    /**
     * Remove executor by id.
     * 
     * @param chainTaskId
     */
    void remove(String chainTaskId) {
        // Set the expiration period to 0
        // and the ExpirationListener will do
        // the rest.
        map.setExpiration(chainTaskId, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * An expiration listener that shutdowns the executor
     * when its expiration time is reached.
     */
    private class ShutdownExecuterExpirationListener
            implements ExpirationListener<String, ThreadPoolTaskExecutor> {

        @Override
        public void expired(
            String chainTaskId, ThreadPoolTaskExecutor executor
        ) {
            executor.shutdown();
        }
    }
}
