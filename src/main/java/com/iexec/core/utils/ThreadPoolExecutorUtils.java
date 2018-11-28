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
