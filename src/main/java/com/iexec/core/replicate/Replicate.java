package com.iexec.core.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusChange;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class Replicate {

    private List<ReplicateStatusChange> statusChangeList;
    private String workerName;
    private String resultSha;
    private String resultUri;
    private String taskId;

    public Replicate(String workerName, String taskId) {
        this.taskId = taskId;
        this.statusChangeList = new ArrayList<>();
        this.statusChangeList.add(new ReplicateStatusChange(ReplicateStatus.CREATED));
        this.workerName = workerName;
    }

    public ReplicateStatus getCurrentStatus(){
        return this.getLatestStatusChange().getStatus();
    }

    public ReplicateStatusChange getLatestStatusChange(){
        return this.getStatusChangeList().get(this.getStatusChangeList().size() - 1);
    }

    public boolean updateStatus(ReplicateStatus status){
        return statusChangeList.add(new ReplicateStatusChange(status));
    }
}
