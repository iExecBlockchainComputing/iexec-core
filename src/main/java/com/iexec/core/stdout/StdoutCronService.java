package com.iexec.core.stdout;

import java.util.Date;
import java.util.List;

import com.iexec.core.task.TaskService;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class StdoutCronService {

    @Value("${cron.stdoutAvailabilityDays}")
    private int stdoutAvailabilityDays;
    private StdoutService stdoutService;
    private TaskService taskService;

    public StdoutCronService(StdoutService stdoutService, TaskService taskService) {
        this.stdoutService = stdoutService;
        this.taskService = taskService;
    }

    @Scheduled(fixedRate = DateUtils.MILLIS_PER_DAY)
    public void cleanStdout() {
        Date someDaysAgo = DateUtils.addDays(new Date(), -stdoutAvailabilityDays);
        List<String> chainTaskIds = taskService.getChainTaskIdsOfTasksExpiredBefore(someDaysAgo);
        stdoutService.delete(chainTaskIds);
    }

}