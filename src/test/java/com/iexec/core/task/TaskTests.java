package com.iexec.core.task;


import org.junit.Test;

import java.util.Date;

import static com.iexec.core.utils.DateTimeUtils.addMinutesToDate;
import static org.assertj.core.api.Assertions.assertThat;

public class TaskTests {

    @Test
    public void shouldInitializeProperly(){
        Task task = new Task("dappName", "cmdLine", 2);

        assertThat(task.getDateStatusList().size()).isEqualTo(1);
        assertThat(task.getDateStatusList().get(0).getStatus()).isEqualTo(TaskStatus.RECEIVED);
    }

    @Test
    public void shouldSetCurrentStatus() {
        Task task = new Task("dappName", "cmdLine", 2);
        assertThat(task.getDateStatusList().size()).isEqualTo(1);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RECEIVED);

        task.changeStatus(TaskStatus.TRANSACTION_INITIALIZE_COMPLETED);
        assertThat(task.getDateStatusList().size()).isEqualTo(2);
        assertThat(task.getDateStatusList().get(0).getStatus()).isEqualTo(TaskStatus.RECEIVED);
        assertThat(task.getDateStatusList().get(1).getStatus()).isEqualTo(TaskStatus.TRANSACTION_INITIALIZE_COMPLETED);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.TRANSACTION_INITIALIZE_COMPLETED);

        task.changeStatus(TaskStatus.COMPUTED);
        assertThat(task.getDateStatusList().size()).isEqualTo(3);
        assertThat(task.getDateStatusList().get(2).getStatus()).isEqualTo(TaskStatus.COMPUTED);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.COMPUTED);
    }

    @Test
    public void shouldGetCorrectLastStatusChange(){
        Task task = new Task("dappName", "cmdLine", 2);
        Date oneMinuteAgo = addMinutesToDate(new Date(), -1);

        TaskStatusChange latestChange = task.getLatestStatusChange();
        assertThat(latestChange.getStatus()).isEqualTo(TaskStatus.RECEIVED);

        task.changeStatus(TaskStatus.TRANSACTION_INITIALIZE_COMPLETED);
        latestChange = task.getLatestStatusChange();
        assertThat(latestChange.getDate().after(oneMinuteAgo)).isTrue();
        assertThat(latestChange.getStatus()).isEqualTo(TaskStatus.TRANSACTION_INITIALIZE_COMPLETED);

        task.changeStatus(TaskStatus.COMPUTED);
        latestChange = task.getLatestStatusChange();
        assertThat(latestChange.getDate().after(oneMinuteAgo)).isTrue();
        assertThat(latestChange.getStatus()).isEqualTo(TaskStatus.COMPUTED);
    }

}
