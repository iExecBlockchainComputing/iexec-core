package com.iexec.core.task;

import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.replicate.Replicate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    private String id;

    @Version
    private Long version;

    private DappType dappType;
    private String dappName;
    private String commandLine;
    private TaskStatus currentStatus;
    private List<TaskStatusChange> dateStatusList;
    private List<Replicate> replicates;
    private int nbContributionNeeded;
    private String uploadingWorkerName;

    public Task(String dappName, String commandLine, int nbContributionNeeded) {
        this.dappType = DappType.DOCKER;
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

    public void setVersion(Long version) {
        this.version = version;
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

    public void setUploadingWorkerName(String uploadingWorkerName) {
        this.uploadingWorkerName = uploadingWorkerName;
    }

    public void setCurrentStatus(TaskStatus status) {
        this.currentStatus = status;
        this.getDateStatusList().add(new TaskStatusChange(status));
    }

    public boolean createNewReplicate(String workerName) {
        return replicates.add(new Replicate(workerName, id));
    }

    public Optional<Replicate> getReplicate(String workerName) {
        for (Replicate replicate : replicates) {
            if (replicate.getWorkerName().equals(workerName)) {
                return Optional.of(replicate);
            }
        }
        return Optional.empty();
    }

    public boolean needMoreReplicates() {
        int nbValidReplicates = 0;
        for (Replicate replicate : getReplicates()) {
            if (!(replicate.getCurrentStatus().equals(ReplicateStatus.ERROR)
                    || replicate.getCurrentStatus().equals(ReplicateStatus.WORKER_LOST))) {
                nbValidReplicates++;
            }
        }
        return nbValidReplicates < nbContributionNeeded;
    }

    public boolean hasWorkerAlreadyContributed(String workerName) {
        for (Replicate replicate : replicates) {
            if (replicate.getWorkerName().equals(workerName)) {
                return true;
            }
        }
        return false;
    }

    public TaskStatusChange getLatestStatusChange() {
        return this.getDateStatusList().get(this.getDateStatusList().size() - 1);
    }

    public int getNbReplicatesWithStatus(ReplicateStatus status) {
        int nbReplicates = 0;
        for (Replicate replicate : replicates) {
            if (replicate.getCurrentStatus().equals(status)) {
                nbReplicates++;
            }
        }
        return nbReplicates;
    }

    public int getNbReplicatesStatusEqualTo(ReplicateStatus... listStatus) {
        int nbReplicates = 0;
        for (Replicate replicate : replicates) {
            for (ReplicateStatus status : listStatus) {
                if (replicate.getCurrentStatus().equals(status)) {
                    nbReplicates++;
                }
            }
        }
        return nbReplicates;
    }
}
