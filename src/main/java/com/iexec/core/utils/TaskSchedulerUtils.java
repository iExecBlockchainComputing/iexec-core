package com.iexec.core.utils;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

public class TaskSchedulerUtils {

    public static ThreadPoolTaskScheduler newThreadPoolTaskScheduler(
        String threadNamePrefix
    ) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(Runtime.getRuntime().availableProcessors());
        scheduler.setThreadNamePrefix(threadNamePrefix);
        scheduler.initialize();
        return scheduler;
    }
}
