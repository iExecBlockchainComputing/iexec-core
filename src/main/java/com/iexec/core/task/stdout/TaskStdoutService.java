package com.iexec.core.task.stdout;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.iexec.core.task.TaskService;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TaskStdoutService {

    @Value("${cron.stdoutAvailabilityDays}")
    private int stdoutAvailabilityDays;
    private TaskStdoutRepository taskStdoutRepository;
    private TaskService taskService;

    public TaskStdoutService(TaskStdoutRepository taskStdoutRepository,
                             TaskService taskService) {
        this.taskStdoutRepository = taskStdoutRepository;
        this.taskService = taskService;
    }

    public Optional<TaskStdout> getTaskStdout(String chainTaskId) {
        return taskStdoutRepository.findOneByChainTaskId(chainTaskId);
    }

    public Optional<ReplicateStdout> getReplicateStdout(String chainTaskId, String walletAddress) {
        return taskStdoutRepository.findByChainTaskIdAndWalletAddress(chainTaskId, walletAddress)
                .map(taskStdout -> taskStdout.getReplicateStdoutList().get(0));
    }

    public void addReplicateStdout(String chainTaskId, String walletAddress, String stdout) {
        ReplicateStdout newReplicateStdout = new ReplicateStdout(walletAddress, stdout);
        TaskStdout taskStdout = getTaskStdout(chainTaskId).orElse(new TaskStdout(chainTaskId));
        if (taskStdout.containsWalletAddress(walletAddress)) {
            return;
        }
        taskStdout.getReplicateStdoutList().add(newReplicateStdout);
        taskStdoutRepository.save(taskStdout);
    }

    @Scheduled(fixedRate = DateUtils.MILLIS_PER_DAY)
    public void cleanStdout() {
        Date someDaysAgo = DateUtils.addDays(new Date(), -stdoutAvailabilityDays);
        List<String> chainTaskIds = taskService.getChainTaskIdsOfTasksExpiredBefore(someDaysAgo);
        taskStdoutRepository.deleteByChainTaskIdIn(chainTaskIds);
    }
}
