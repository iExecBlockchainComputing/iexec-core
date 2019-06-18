package com.iexec.core.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.ChainUtils;
import com.iexec.common.dapp.DappType;
import com.iexec.common.tee.TeeUtils;

import lombok.*;
import org.apache.commons.lang.time.DateUtils;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.iexec.core.task.TaskStatus.CONSENSUS_REACHED;
import static com.iexec.core.utils.DateTimeUtils.now;

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
    private long maxExecutionTime;
    private String tag;
    private String chainTaskId;
    private DappType dappType;
    private String dappName;
    private String commandLine;
    private long initializationBlockNumber;
    private TaskStatus currentStatus;
    private int trust;
    private int numWorkersNeeded;//TODO: Remove this field
    private String uploadingWorkerWalletAddress;
    private String consensus;
    private long consensusReachedBlockNumber;
    private Date contributionDeadline;
    private Date revealDeadline;
    private Date finalDeadline;
    private String resultLink;
    private String chainCallbackData;
    private List<TaskStatusChange> dateStatusList;

    public Task(String dappName, String commandLine, int trust) {
        this.dappType = DappType.DOCKER;
        this.dappName = dappName;
        this.commandLine = commandLine;
        this.trust = trust;
        this.dateStatusList = new ArrayList<>();
        this.dateStatusList.add(new TaskStatusChange(TaskStatus.RECEIVED));
        this.currentStatus = TaskStatus.RECEIVED;
    }

    public Task(String dappName, String commandLine, int trust, String chainTaskId) {
        this(dappName, commandLine, trust);
        this.chainTaskId = chainTaskId;
    }

    public Task(String chainDealId, int taskIndex, String dappName, String commandLine, int trust, long maxExecutionTime, String tag) {
        this(dappName, commandLine, trust);
        this.chainDealId = chainDealId;
        this.taskIndex = taskIndex;
        this.chainTaskId = ChainUtils.generateChainTaskId(chainDealId, BigInteger.valueOf(taskIndex));
        this.maxExecutionTime = maxExecutionTime;
        this.tag = tag;
    }

    public void changeStatus(TaskStatus status) {
        changeStatus(status, null);
    }

    public void changeStatus(TaskStatus status, ChainReceipt chainReceipt) {
        setCurrentStatus(status);
        this.getDateStatusList().add(new TaskStatusChange(status, chainReceipt));
    }

    @JsonIgnore
    public TaskStatusChange getLatestStatusChange() {
        return this.getDateStatusList().get(this.getDateStatusList().size() - 1);
    }

    @JsonIgnore
    public TaskStatus getLastButOneStatus() {
        return this.getDateStatusList().get(this.getDateStatusList().size() - 2).getStatus();
    }

    public boolean isConsensusReachedSinceMultiplePeriods(int nbOfPeriods) {
        Optional<Date> consensusReachedDate = this.getDateOfStatus(CONSENSUS_REACHED);
        if (!consensusReachedDate.isPresent()){
            return false;
        }
        Date onePeriodAfterConsensusReachedDate = new Date(consensusReachedDate.get().getTime() + nbOfPeriods * this.maxExecutionTime);
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

    public boolean isContributionDeadlineReached(){
        return new Date().after(contributionDeadline);
    }

	public boolean inContributionPhase() {
		return TaskStatus.isInContributionPhase(getCurrentStatus());
	}

	public boolean inRevealPhase() {
		return TaskStatus.isInRevealPhase(getCurrentStatus());
	}

	public boolean inResultUploadPhase() {
		return TaskStatus.isInResultUploadPhase(getCurrentStatus());
	}

    public boolean inCompletionPhase() {
		return TaskStatus.isInCompletionPhase(getCurrentStatus());
    }

    public boolean isTeeNeeded() {
        return TeeUtils.isTrustedExecutionTag(getTag());
    }
}
