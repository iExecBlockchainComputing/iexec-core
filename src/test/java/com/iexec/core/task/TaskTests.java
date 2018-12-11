package com.iexec.core.task;


import org.junit.Test;

import java.util.Date;

import static com.iexec.core.utils.DateTimeUtils.addMinutesToDate;
import static org.assertj.core.api.Assertions.assertThat;

public class TaskTests {

    private final static String DAPP_NAME = "dappName";
    private final static String COMMAND_LINE = "commandLine";

    @Test
    public void shouldInitializeProperly(){
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2);

        assertThat(task.getDateStatusList().size()).isEqualTo(1);
        assertThat(task.getDateStatusList().get(0).getStatus()).isEqualTo(TaskStatus.RECEIVED);
    }

    @Test
    public void shouldComputeCorrectNumberOfWorkersNeeded(){
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 0);
        assertThat(task.getNumWorkersNeeded()).isEqualTo(1);

        task = new Task(DAPP_NAME, COMMAND_LINE, 1);
        assertThat(task.getNumWorkersNeeded()).isEqualTo(1);

        task = new Task(DAPP_NAME, COMMAND_LINE, 2);
        assertThat(task.getNumWorkersNeeded()).isEqualTo(1);

        task = new Task(DAPP_NAME, COMMAND_LINE, 3);
        assertThat(task.getNumWorkersNeeded()).isEqualTo(2);

        task = new Task(DAPP_NAME, COMMAND_LINE, 4);
        assertThat(task.getNumWorkersNeeded()).isEqualTo(2);

        task = new Task(DAPP_NAME, COMMAND_LINE, 5);
        assertThat(task.getNumWorkersNeeded()).isEqualTo(3);

        task = new Task(DAPP_NAME, COMMAND_LINE, 6);
        assertThat(task.getNumWorkersNeeded()).isEqualTo(3);

        task = new Task(DAPP_NAME, COMMAND_LINE, 7);
        assertThat(task.getNumWorkersNeeded()).isEqualTo(4);

        task = new Task(DAPP_NAME, COMMAND_LINE, 8);
        assertThat(task.getNumWorkersNeeded()).isEqualTo(4);

        task = new Task(DAPP_NAME, COMMAND_LINE, 9);
        assertThat(task.getNumWorkersNeeded()).isEqualTo(4);

        task = new Task(DAPP_NAME, COMMAND_LINE, 20);
        assertThat(task.getNumWorkersNeeded()).isEqualTo(6);
    }

    @Test
    public void shouldSetCurrentStatus() {
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2);
        assertThat(task.getDateStatusList().size()).isEqualTo(1);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RECEIVED);

        task.changeStatus(TaskStatus.INITIALIZED);
        assertThat(task.getDateStatusList().size()).isEqualTo(2);
        assertThat(task.getDateStatusList().get(0).getStatus()).isEqualTo(TaskStatus.RECEIVED);
        assertThat(task.getDateStatusList().get(1).getStatus()).isEqualTo(TaskStatus.INITIALIZED);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.INITIALIZED);

        task.changeStatus(TaskStatus.RUNNING);
        assertThat(task.getDateStatusList().size()).isEqualTo(3);
        assertThat(task.getDateStatusList().get(2).getStatus()).isEqualTo(TaskStatus.RUNNING);
        assertThat(task.getCurrentStatus()).isEqualTo(TaskStatus.RUNNING);
    }

    @Test
    public void shouldGetCorrectLastStatusChange(){
        Task task = new Task(DAPP_NAME, COMMAND_LINE, 2);
        Date oneMinuteAgo = addMinutesToDate(new Date(), -1);

        TaskStatusChange latestChange = task.getLatestStatusChange();
        assertThat(latestChange.getStatus()).isEqualTo(TaskStatus.RECEIVED);

        task.changeStatus(TaskStatus.INITIALIZED);
        latestChange = task.getLatestStatusChange();
        assertThat(latestChange.getDate().after(oneMinuteAgo)).isTrue();
        assertThat(latestChange.getStatus()).isEqualTo(TaskStatus.INITIALIZED);

        task.changeStatus(TaskStatus.RUNNING);
        latestChange = task.getLatestStatusChange();
        assertThat(latestChange.getDate().after(oneMinuteAgo)).isTrue();
        assertThat(latestChange.getStatus()).isEqualTo(TaskStatus.RUNNING);
    }

}
