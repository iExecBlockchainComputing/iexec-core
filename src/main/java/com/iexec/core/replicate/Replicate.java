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

    private List<ReplicateStatusChange> statusList;
    private String workerName;
    private String resultSha;
    private String resultUri;
    private String taskId;

    public Replicate(String workerName, String taskId) {
        this.taskId = taskId;
        this.statusList = new ArrayList<>();
        this.statusList.add(new ReplicateStatusChange(ReplicateStatus.CREATED));
        this.workerName = workerName;
    }

    public ReplicateStatus getLatestStatus(){
        return this.getStatusList().get(this.getStatusList().size() - 1).getStatus();
    }

    public boolean updateStatus(ReplicateStatus status){
        return statusList.add(new ReplicateStatusChange(status));
    }
}
