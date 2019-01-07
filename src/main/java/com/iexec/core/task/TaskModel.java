package com.iexec.core.task;

import com.iexec.common.dapp.DappType;
import com.iexec.core.replicate.Replicate;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

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
}
