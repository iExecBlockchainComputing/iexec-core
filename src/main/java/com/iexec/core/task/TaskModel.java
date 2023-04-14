/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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

import com.iexec.commons.poco.dapp.DappType;
import com.iexec.core.replicate.ReplicateModel;
import lombok.*;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskModel {

    private String chainTaskId;
    private List<ReplicateModel> replicates;
    private long maxExecutionTime;
    private String tag;
    private DappType dappType;
    private String dappName;
    private String commandLine;
    private long initializationBlockNumber;
    private TaskStatus currentStatus;
    private int trust;
    private String uploadingWorkerWalletAddress;
    private String consensus;
    private Date contributionDeadline;
    private Date revealDeadline;
    private Date finalDeadline;
    private String resultLink;
    private String chainCallbackData;
    private List<TaskStatusChange> dateStatusList;

    public static TaskModel fromEntity(Task entity) {
        return TaskModel.builder()
                .chainTaskId(entity.getChainTaskId())
                .maxExecutionTime(entity.getMaxExecutionTime())
                .tag(entity.getTag())
                .dappType(entity.getDappType())
                .dappName(entity.getDappName())
                .commandLine(entity.getCommandLine())
                .initializationBlockNumber(entity.getInitializationBlockNumber())
                .currentStatus(entity.getCurrentStatus())
                .trust(entity.getTrust())
                .uploadingWorkerWalletAddress(entity.getUploadingWorkerWalletAddress())
                .consensus(entity.getConsensus())
                .contributionDeadline(entity.getContributionDeadline())
                .revealDeadline(entity.getRevealDeadline())
                .finalDeadline(entity.getFinalDeadline())
                .resultLink(entity.getResultLink())
                .chainCallbackData(entity.getChainCallbackData())
                .dateStatusList(entity.getDateStatusList())
                .build();
    }
}
