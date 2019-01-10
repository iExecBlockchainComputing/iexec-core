package com.iexec.core.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iexec.common.dapp.DappType;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.iexec.core.task.TaskStatus.CONSENSUS_REACHED;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    private String id;

    @Version
    private Long version;

    private String chainDealId;
    private int taskIndex;
    private Date timeRef;
    private String chainTaskId;
    private DappType dappType;
    private String dappName;
    private String commandLine;
    private TaskStatus currentStatus;
    private List<TaskStatusChange> dateStatusList;
    private int trust;
    private int numWorkersNeeded;
    private String uploadingWorkerWalletAddress;
    private String consensus;
    private Date contributionDeadline;
    private Date revealDeadline;
    private Date finalDeadline;

    public Task(String dappName, String commandLine, int trust) {
        this.dappType = DappType.DOCKER;
        this.dappName = dappName;
        this.commandLine = commandLine;
        this.trust = trust;
        this.dateStatusList = new ArrayList<>();
        this.dateStatusList.add(new TaskStatusChange(TaskStatus.RECEIVED));
        this.currentStatus = TaskStatus.RECEIVED;

        // the number of workers needed should satisfy is:
        // 2**n > trust - 1
        // a 20% additional number of workers is taken for safety
        // a max(1, value) is used to cover hedge cases (low values to have at least one worker)
        this.numWorkersNeeded = Math.max(1, (int) Math.ceil((Math.log(trust - 1d) / Math.log(2) * 1.20) / 1.0));
    }

    public Task(String dappName, String commandLine, int trust, String chainTaskId) {
        this(dappName, commandLine, trust);
        this.chainTaskId = chainTaskId;
    }

    public Task(String chainDealId, int taskIndex, String dappName, String commandLine, int trust, Date timeRef) {
        this(dappName, commandLine, trust);
        this.chainDealId = chainDealId;
        this.taskIndex = taskIndex;
        this.timeRef = timeRef;
        this.chainTaskId = "";
    }

    public void changeStatus(TaskStatus status) {
        setCurrentStatus(status);
        this.getDateStatusList().add(new TaskStatusChange(status));
    }

    @JsonIgnore
    public TaskStatusChange getLatestStatusChange() {
        return this.getDateStatusList().get(this.getDateStatusList().size() - 1);
    }

    public boolean isConsensusReachedSinceMultiplePeriods(int nbOfPeriods) {
        Optional<Date> consensusReachedDate = this.getDateOfStatus(CONSENSUS_REACHED);
        if (!consensusReachedDate.isPresent()){
            return false;
        }
        Date onePeriodAfterConsensusReachedDate = new Date(consensusReachedDate.get().getTime() + nbOfPeriods * this.timeRef.getTime());
        Date now = new Date();

        return now.after(onePeriodAfterConsensusReachedDate);
    }

    public Optional<Date> getDateOfStatus(TaskStatus taskStatus) {
        for (TaskStatusChange taskStatusChange : this.dateStatusList) {
            if (taskStatusChange.getStatus().equals(taskStatus)) {
                return Optional.of(taskStatusChange.getDate());
            }
        }
        return Optional.empty();
    }
}
