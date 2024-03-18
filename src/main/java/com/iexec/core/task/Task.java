/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.core.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.iexec.commons.poco.chain.ChainUtils;
import com.iexec.commons.poco.dapp.DappType;
import com.iexec.commons.poco.tee.TeeUtils;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.iexec.core.task.TaskStatus.CONSENSUS_REACHED;
import static com.iexec.core.task.TaskStatus.RECEIVED;

@Document
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
/*
 * We need this index to make sure that we don't
 * add two tasks with the same combination:
 * (chainDealId + taskIndex).
 * This can appear when multiple threads call
 * the method {@link TaskService#addTask()}.
 */
@CompoundIndex(name = "unique_deal_idx",
        def = "{'chainDealId': 1, 'taskIndex': 1}",
        unique = true)
public class Task {

    public static final String CURRENT_STATUS_FIELD_NAME = "currentStatus";
    public static final String CONTRIBUTION_DEADLINE_FIELD_NAME = "contributionDeadline";

    @Id
    private String id;

    @Version
    private Long version;

    @Indexed(unique = true)
    private String chainTaskId;

    private String chainDealId;
    private int taskIndex;
    private long dealBlockNumber;
    private long maxExecutionTime;
    private String tag;
    private DappType dappType;
    private String dappName;
    private String commandLine;
    private long initializationBlockNumber;
    @Field(CURRENT_STATUS_FIELD_NAME)
    private TaskStatus currentStatus;
    private int trust;
    private int numWorkersNeeded;//TODO: Remove this field
    private String uploadingWorkerWalletAddress;
    private String consensus;
    private long consensusReachedBlockNumber;
    @Field(CONTRIBUTION_DEADLINE_FIELD_NAME)
    private Date contributionDeadline;
    private Date revealDeadline;
    private Date finalDeadline;
    private String resultLink;
    private String chainCallbackData;
    private List<TaskStatusChange> dateStatusList;
    private String enclaveChallenge;
    private String smsUrl;

    public Task(String dappName, String commandLine, int trust) {
        this.dappType = DappType.DOCKER;
        this.dappName = dappName;
        this.commandLine = commandLine;
        this.trust = trust;
        this.dateStatusList = new ArrayList<>();
        TaskStatusChange taskStatusChange = TaskStatusChange.builder()
                .status(RECEIVED)
                .build();
        this.dateStatusList.add(taskStatusChange);
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
        this.chainTaskId = ChainUtils.generateChainTaskId(chainDealId, taskIndex);
        this.maxExecutionTime = maxExecutionTime;
        this.tag = tag;
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
        if (consensusReachedDate.isEmpty()) {
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

    public boolean isContributionDeadlineReached() {
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

    public boolean isTeeTask() {
        return TeeUtils.isTeeTag(getTag());
    }

    TaskModel generateModel() {
        return TaskModel.builder()
                .chainTaskId(chainTaskId)
                .maxExecutionTime(maxExecutionTime)
                .tag(tag)
                .dappType(dappType)
                .dappName(dappName)
                .commandLine(commandLine)
                .initializationBlockNumber(initializationBlockNumber)
                .currentStatus(currentStatus)
                .trust(trust)
                .uploadingWorkerWalletAddress(uploadingWorkerWalletAddress)
                .consensus(consensus)
                .contributionDeadline(contributionDeadline)
                .revealDeadline(revealDeadline)
                .finalDeadline(finalDeadline)
                .resultLink(resultLink)
                .chainCallbackData(chainCallbackData)
                .dateStatusList(dateStatusList)
                .build();
    }
}
