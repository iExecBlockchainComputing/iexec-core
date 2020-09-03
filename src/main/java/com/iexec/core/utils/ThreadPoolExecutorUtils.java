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

package com.iexec.core.utils;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ThreadPoolExecutorUtils {

    private ThreadPoolExecutorUtils(){
        throw new UnsupportedOperationException();
    }

    public static ThreadPoolExecutor singleThreadExecutorWithFixedSizeQueue(int queueSize) {
        int numThreads = 1;
        ThreadPoolExecutor threadPool = new ThreadPoolExecutor(numThreads, numThreads,
                0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueSize));

        // By default (unfortunately) the ThreadPoolExecutor will throw an exception when a job is submitted that fills the queue
        // To avoid this exception, an empty RejectedExecutionHandler (that does nothing) needs to be set
        threadPool.setRejectedExecutionHandler((r, executor) -> {
            // it is kept empty so no exception is thrown
        });

        return threadPool;
    }
}
