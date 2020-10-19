package com.iexec.core.utils;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Copied from {@link java.util.concurrent.Executors#defaultThreadFactory()}
 * with minor changes to use a custom thread pool name.
 */
class CustomSingleExecutorThreadFactory implements ThreadFactory {
    private final ThreadGroup group;
    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    CustomSingleExecutorThreadFactory(String threadPoolName) {
        SecurityManager s = System.getSecurityManager();
        group = (s != null) ? s.getThreadGroup() :
                                Thread.currentThread().getThreadGroup();
        namePrefix = threadPoolName + "-th-";
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(group, r,
                                namePrefix + threadNumber.getAndIncrement(),
                                0);
        if (t.isDaemon())
            t.setDaemon(false);
        if (t.getPriority() != Thread.NORM_PRIORITY)
            t.setPriority(Thread.NORM_PRIORITY);
        return t;
    }
}
