package com.iexec.core.utils;

import java.util.concurrent.ThreadPoolExecutor.DiscardPolicy;

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

public class TaskExecutorUtils {

    public static ThreadPoolTaskExecutor newThreadPoolTaskExecutor(
        String threadNamePrefix
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Runtime.getRuntime().availableProcessors());
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }

    public static ThreadPoolTaskExecutor newThreadPoolTaskExecutor(
        String threadNamePrefix,
        int poolSize
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }

    // TODO remove this
    public static ThreadPoolTaskExecutor singleThreadWithFixedSizeQueue(
        int queueSize,
        String threadNamePrefix
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setKeepAliveSeconds(0);
        executor.setQueueCapacity(queueSize);
        executor.setThreadNamePrefix(threadNamePrefix);
        // Discard silently when we add a task
        //  to the already-full queue.
        executor.setRejectedExecutionHandler(new DiscardPolicy());
        executor.initialize();
        return executor;
    }
}
