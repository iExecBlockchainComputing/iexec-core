package com.iexec.core.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
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
