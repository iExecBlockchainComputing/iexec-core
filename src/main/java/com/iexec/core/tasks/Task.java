package com.iexec.core.tasks;

import org.springframework.data.annotation.Id;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Task {

    @Id
    private String id;
    private String commandLine;
    private TaskStatus currentStatus;
    private List<TaskStatusChange> dateStatusList;
    private List<Replicate> replicates;
    private int nbContributionNeeded;

    public Task() {}

    public Task(String commandLine, int nbContributionNeeded) {
        this.commandLine = commandLine;
        this.nbContributionNeeded = nbContributionNeeded;
        this.dateStatusList = new ArrayList<>();
        this.dateStatusList.add(new TaskStatusChange(TaskStatus.CREATED));
        this.currentStatus = TaskStatus.CREATED;
        this.replicates = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<TaskStatusChange> getDateStatusList() {
        return dateStatusList;
    }

    public void setDateStatusList(List<TaskStatusChange> dateStatusList) {
        this.dateStatusList = dateStatusList;
    }

    public String getCommandLine() {
        return commandLine;
    }

    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }

    public List<Replicate> getReplicates() {
        return replicates;
    }

    public void setReplicates(List<Replicate> replicates) {
        this.replicates = replicates;
    }

    public int getNbContributionNeeded() {
        return nbContributionNeeded;
    }

    public void setNbContributionNeeded(int nbContributionNeeded) {
        this.nbContributionNeeded = nbContributionNeeded;
    }

    public TaskStatus getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(TaskStatus status) {
        this.currentStatus = status;
        this.getDateStatusList().add(new TaskStatusChange(status));
    }

}
