package com.iexec.core.tasks;

import org.springframework.data.annotation.Id;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Replicate {

    @Id
    private String id;
    private List<ReplicateStatusChange> statusList;
    private String workerName;
    private String resultSha;
    private String resultUri;

    // TODO: this should be removed from the Replicate
    private String taskId;

    public Replicate() {}

    public Replicate(String workerName, String taskId) {
        this.taskId = taskId;
        this.statusList = new ArrayList<>();
        this.statusList.add(new ReplicateStatusChange(new Date(), ReplicateStatus.CREATED));
        this.workerName = workerName;
    }

    public String getWorkerName() {
        return workerName;
    }

    public void setWorkerName(String workerName) {
        this.workerName = workerName;
    }

    public List<ReplicateStatusChange> getStatusList() {
        return statusList;
    }

    public void setStatusList(List<ReplicateStatusChange> statusList) {
        this.statusList = statusList;
    }

    public String getResultSha() {
        return resultSha;
    }

    public void setResultSha(String resultSha) {
        this.resultSha = resultSha;
    }

    public String getResultUri() {
        return resultUri;
    }

    public void setResultUri(String resultUri) {
        this.resultUri = resultUri;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
}
