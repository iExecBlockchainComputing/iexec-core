/*
 * Copyright 2022 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.dapp.DappType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

class TaskModelTests {

    public static final String CHAIN_TASK_ID = "task";
    public static final long MAX_EXECUTION_TIME = 1;
    public static final String TAG = "0xa";
    public static final DappType DAPP_TYPE = DappType.DOCKER;
    public static final String DAPP_NAME = "name";
    public static final String COMMAND_LINE = "line";
    public static final long INITIALIZATION_BLOCK_NUMBER = 2;
    public static final TaskStatus CURRENT_STATUS = TaskStatus.COMPLETED;
    public static final int TRUST = 3;
    public static final String UPLOADING_WORKER_WALLET_ADDRESS = "wallet";
    public static final String CONSENSUS = "consensus";
    public static final Date CONTRIBUTION_DEADLINE = new Date(1);
    public static final Date REVEAL_DEADLINE = new Date(2);
    public static final Date FINAL_DEADLINE = new Date(3);
    public static final String RESULT_LINK = "link";
    public static final String CHAIN_CALLBACK_DATA = "data";
    public static final List<TaskStatusChange> DATE_STATUS_LIST = new ArrayList<>();

    @Test
    void shouldConvertFromEntityToDto() {
        Task entity = Task.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .maxExecutionTime(MAX_EXECUTION_TIME)
                .tag(TAG)
                .dappType(DAPP_TYPE)
                .dappName(DAPP_NAME)
                .commandLine(COMMAND_LINE)
                .initializationBlockNumber(INITIALIZATION_BLOCK_NUMBER)
                .currentStatus(CURRENT_STATUS)
                .trust(TRUST)
                .uploadingWorkerWalletAddress(UPLOADING_WORKER_WALLET_ADDRESS)
                .consensus(CONSENSUS)
                .contributionDeadline(CONTRIBUTION_DEADLINE)
                .revealDeadline(REVEAL_DEADLINE)
                .finalDeadline(FINAL_DEADLINE)
                .resultLink(RESULT_LINK)
                .chainCallbackData(CHAIN_CALLBACK_DATA)
                .dateStatusList(DATE_STATUS_LIST)
                .build();

        TaskModel dto = TaskModel.fromEntity(entity);
        Assertions.assertEquals(entity.getChainTaskId(), dto.getChainTaskId());
        Assertions.assertEquals(entity.getMaxExecutionTime(), dto.getMaxExecutionTime());
        Assertions.assertEquals(entity.getTag(), dto.getTag());
        Assertions.assertEquals(entity.getDappType(), dto.getDappType());
        Assertions.assertEquals(entity.getDappName(), dto.getDappName());
        Assertions.assertEquals(entity.getCommandLine(), dto.getCommandLine());
        Assertions.assertEquals(entity.getInitializationBlockNumber(), dto.getInitializationBlockNumber());
        Assertions.assertEquals(entity.getCurrentStatus(), dto.getCurrentStatus());
        Assertions.assertEquals(entity.getTrust(), dto.getTrust());
        Assertions.assertEquals(entity.getUploadingWorkerWalletAddress(), dto.getUploadingWorkerWalletAddress());
        Assertions.assertEquals(entity.getConsensus(), dto.getConsensus());
        Assertions.assertEquals(entity.getContributionDeadline(), dto.getContributionDeadline());
        Assertions.assertEquals(entity.getRevealDeadline(), dto.getRevealDeadline());
        Assertions.assertEquals(entity.getFinalDeadline(), dto.getFinalDeadline());
        Assertions.assertEquals(entity.getResultLink(), dto.getResultLink());
        Assertions.assertEquals(entity.getChainCallbackData(), dto.getChainCallbackData());
        Assertions.assertEquals(entity.getDateStatusList(), dto.getDateStatusList());
    }

}
