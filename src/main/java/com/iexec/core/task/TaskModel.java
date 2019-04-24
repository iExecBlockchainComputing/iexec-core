package com.iexec.core.task;

import com.iexec.common.dapp.DappType;
import com.iexec.core.replicate.Replicate;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import java.util.Date;
import java.util.List;

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
    private List<Replicate> replicates;
    private long maxExecutionTime;
    private String tag;
    private DappType dappType;
    private String dappName;
    private String commandLine;
    private long initializationBlockNumber;
    private TaskStatus currentStatus;
    private int trust;
    private int numWorkersNeeded;
    private String uploadingWorkerWalletAddress;
    private String consensus;
    private Date contributionDeadline;
    private Date revealDeadline;
    private Date finalDeadline;
    private String resultLink;
    private String chainCallbackData;
    private List<TaskStatusChange> dateStatusList;

    public TaskModel(Task task, List<Replicate> replicates) {
        this.id = task.getId();
        this.replicates = replicates;
        this.version = task.getVersion();
        this.chainTaskId = task.getChainTaskId();
        this.maxExecutionTime = task.getMaxExecutionTime();
        this.tag = task.getTag();
        this.dappType = task.getDappType();
        this.dappName = task.getDappName();
        this.commandLine = task.getCommandLine();
        this.initializationBlockNumber = task.getInitializationBlockNumber();
        this.currentStatus = task.getCurrentStatus();
        this.trust = task.getTrust();
        this.numWorkersNeeded = task.getNumWorkersNeeded();
        this.uploadingWorkerWalletAddress = task.getUploadingWorkerWalletAddress();
        this.consensus = task.getConsensus();
        this.contributionDeadline = task.getContributionDeadline();
        this.revealDeadline = task.getRevealDeadline();
        this.finalDeadline = task.getFinalDeadline();
        this.resultLink = task.getResultLink();
        this.chainCallbackData = task.getChainCallbackData();
        this.dateStatusList = task.getDateStatusList();
    }
}
