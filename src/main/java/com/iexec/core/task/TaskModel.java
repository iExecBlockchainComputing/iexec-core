package com.iexec.core.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iexec.common.dapp.DappType;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.replicate.Replicate;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import java.util.List;
import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskModel {

    @Id
    private String id;

    @Version
    private Long version;

    private String chainTaskId;
    private DappType dappType;
    private String dappName;
    private String commandLine;
    private TaskStatus currentStatus;
    private List<TaskStatusChange> dateStatusList;
    private List<Replicate> replicates;
    private int trust;
    private int numWorkersNeeded;
    private String uploadingWorkerWalletAddress;
    private String consensus;

    public void changeStatus(TaskStatus status) {
        setCurrentStatus(status);
        this.getDateStatusList().add(new TaskStatusChange(status));
    }

    public boolean createNewReplicate(String walletAddress) {
        return replicates.add(new Replicate(walletAddress, chainTaskId));
    }

    public Optional<Replicate> getReplicate(String walletAddress) {
        for (Replicate replicate : replicates) {
            if (replicate.getWalletAddress().equals(walletAddress)) {
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
        return nbValidReplicates < trust;
    }

    public boolean hasWorkerAlreadyContributed(String walletAddress) {
        for (Replicate replicate : replicates) {
            if (replicate.getWalletAddress().equals(walletAddress)) {
                return true;
            }
        }
        return false;
    }


    @JsonIgnore
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

    public void setConsensus(String consensus) {
        this.consensus = consensus;
    }
}
