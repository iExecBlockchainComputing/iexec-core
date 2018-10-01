package com.iexec.core.tasks;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
public class Task {

    @Id
    private String id;
    private String commandLine;
    private TaskStatus currentStatus;
    private List<TaskStatusChange> dateStatusList;
    private List<Replicate> replicates;
    private int nbContributionNeeded;

    public Task(String commandLine, int nbContributionNeeded) {
        this.commandLine = commandLine;
        this.nbContributionNeeded = nbContributionNeeded;
        this.dateStatusList = new ArrayList<>();
        this.dateStatusList.add(new TaskStatusChange(TaskStatus.CREATED));
        this.currentStatus = TaskStatus.CREATED;
        this.replicates = new ArrayList<>();
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setDateStatusList(List<TaskStatusChange> dateStatusList) {
        this.dateStatusList = dateStatusList;
    }

    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }

    public void setReplicates(List<Replicate> replicates) {
        this.replicates = replicates;
    }

    public void setNbContributionNeeded(int nbContributionNeeded) {
        this.nbContributionNeeded = nbContributionNeeded;
    }

    public void setCurrentStatus(TaskStatus status) {
        this.currentStatus = status;
        this.getDateStatusList().add(new TaskStatusChange(status));
    }
}
