package com.iexec.core.task;

import com.iexec.core.utils.ThreadPoolExecutorUtils;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * This class is used to perform updates on a task one by one. It also ensures that no extra update is
 * performed for no reason (in the case of multiple replicate updates in a short time, the task update will only be called
 * once)
 */
@Service
public class TaskExecutorEngine {

    private TaskService taskService;
    private Map<String, ThreadPoolExecutor> executorMap;

    public TaskExecutorEngine(TaskService taskService) {
        this.taskService = taskService;
        executorMap = new ConcurrentHashMap<>();
    }

    void updateTask(Task task) {
        String chainTaskId = task.getChainTaskId();

        executorMap.putIfAbsent(chainTaskId, ThreadPoolExecutorUtils.singleThreadExecutorWithFixedSizeQueue(1));

        Executor executor = executorMap.get(chainTaskId);
        executor.execute(() -> taskService.tryToMoveTaskToNextStatus(task));
    }

    void removeTaskExecutor(Task task){
        String chainTaskId = task.getChainTaskId();

        if (task.getCurrentStatus().equals(TaskStatus.COMPLETED)) {
            executorMap.get(chainTaskId).shutdown();
            executorMap.remove(chainTaskId);
        }
    }
}
