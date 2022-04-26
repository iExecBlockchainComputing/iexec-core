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

package com.iexec.core.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReplicateModelTest {

    public static final String CHAIN_TASK_ID = "task";
    public static final String WALLET_ADDRESS = "wallet";
    public static final ReplicateStatus CURRENT_STATUS = ReplicateStatus.COMPLETED;
    public static final List<ReplicateStatusUpdate> STATUS_UPDATE_LIST = new ArrayList<>();
    public static final String RESULT_LINK = "link";
    public static final String CHAIN_CALLBACK_DATA = "data";
    public static final String CONTRIBUTION_HASH = "hash";

    @Test
    void shouldConvertFromEntityToDto() {
        Replicate entity = mock(Replicate.class);
        when(entity.getChainTaskId()).thenReturn(CHAIN_TASK_ID);
        when(entity.getWalletAddress()).thenReturn(WALLET_ADDRESS);
        when(entity.getCurrentStatus()).thenReturn(CURRENT_STATUS);
        when(entity.getStatusUpdateList()).thenReturn(STATUS_UPDATE_LIST);
        when(entity.getResultLink()).thenReturn(RESULT_LINK);
        when(entity.getChainCallbackData()).thenReturn(CHAIN_CALLBACK_DATA);
        when(entity.getContributionHash()).thenReturn(CONTRIBUTION_HASH);

        ReplicateModel dto = ReplicateModel.fromEntity(entity);
        Assertions.assertEquals(entity.getChainTaskId(), dto.getChainTaskId());
        Assertions.assertEquals(entity.getWalletAddress(), dto.getWalletAddress());
        Assertions.assertEquals(entity.getCurrentStatus(), dto.getCurrentStatus());
        Assertions.assertEquals(entity.getStatusUpdateList(), dto.getStatusUpdateList());
        Assertions.assertEquals(entity.getResultLink(), dto.getResultLink());
        Assertions.assertEquals(entity.getChainCallbackData(), dto.getChainCallbackData());
        Assertions.assertEquals(entity.getContributionHash(), dto.getContributionHash());
    }
}