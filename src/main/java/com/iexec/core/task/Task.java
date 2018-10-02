package com.iexec.core.task;

import com.iexec.common.dapp.DappType;
import com.iexec.core.replicate.Replicate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    private String id;
    private DappType dappType;
    private String dappName;
    private String commandLine;
    private TaskStatus currentStatus;
    private List<TaskStatusChange> dateStatusList;
    private List<Replicate> replicates;
    private int nbContributionNeeded;

    public Task(String dappName, String commandLine, int nbContributionNeeded) {
        this.dappType=DappType.DOCKER;
        this.dappName = dappName;
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
