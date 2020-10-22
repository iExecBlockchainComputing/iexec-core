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

package com.iexec.core.config;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

import com.iexec.core.utils.TaskExecutorUtils;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    /**
     * This executor is used for Async tasks.
     * 
     * @return
     */
    @Override
    public Executor getAsyncExecutor() {
        return TaskExecutorUtils.newThreadPoolTaskExecutor("Async-");
    }

    /**
     * Handle uncaught exceptions raised by Async tasks.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new Handler();
    }

    private class Handler implements AsyncUncaughtExceptionHandler {

        @Override
        public void handleUncaughtException(
            Throwable ex,
            Method method,
            Object... params
        ) {
            log.error("Exception in async task [method:{}, params:{}]",
                    method, params, ex);
        }
    }
}