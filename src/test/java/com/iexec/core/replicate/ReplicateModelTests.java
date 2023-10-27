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
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.core.exception.MultipleOccurrencesOfFieldNotAllowed;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

class ReplicateModelTests {

    public static final String CHAIN_TASK_ID = "task";
    public static final String WALLET_ADDRESS = "wallet";
    public static final ReplicateStatus CURRENT_STATUS = ReplicateStatus.COMPLETED;
    public static final ReplicateStatusDetails STATUS_UPDATE_DETAILS = ReplicateStatusDetails.builder()
            .exitCode(0)
            .teeSessionGenerationError("error")
            .build();
    public static final ReplicateStatusUpdate STATUS_UPDATE = ReplicateStatusUpdate.builder()
            .details(STATUS_UPDATE_DETAILS)
            .status(CURRENT_STATUS)
            .build();
    public static final List<ReplicateStatusUpdate> STATUS_UPDATE_LIST = Collections.singletonList(STATUS_UPDATE);
    public static final String RESULT_LINK = "link";
    public static final String CHAIN_CALLBACK_DATA = "data";
    public static final String CONTRIBUTION_HASH = "hash";

    @Test
    void shouldConvertFromEntityToDto() {
        Replicate entity = createReplicate();
        entity.setStatusUpdateList(STATUS_UPDATE_LIST);

        ReplicateModel dto = entity.generateModel();
        Assertions.assertEquals(entity.getChainTaskId(), dto.getChainTaskId());
        Assertions.assertEquals(entity.getWalletAddress(), dto.getWalletAddress());
        Assertions.assertEquals(entity.getCurrentStatus(), dto.getCurrentStatus());
        Assertions.assertEquals(entity.getResultLink(), dto.getResultLink());
        Assertions.assertEquals(entity.getChainCallbackData(), dto.getChainCallbackData());
        Assertions.assertEquals(entity.getContributionHash(), dto.getContributionHash());

        Assertions.assertEquals(entity.getStatusUpdateList().get(0).getStatus(), dto.getStatusUpdateList().get(0).getStatus());
        Assertions.assertEquals(entity.getStatusUpdateList().get(0).getDate(), dto.getStatusUpdateList().get(0).getDate());
        Assertions.assertEquals(entity.getStatusUpdateList().get(0).getDetails().getCause(), dto.getStatusUpdateList().get(0).getCause());

        Assertions.assertEquals(entity.getStatusUpdateList().get(0).getDetails().getExitCode(), dto.getAppExitCode());
        Assertions.assertEquals(entity.getStatusUpdateList().get(0).getDetails().getTeeSessionGenerationError(), dto.getTeeSessionGenerationError());
    }

    @Test
    void shouldNotConvertFromEntityToDtoSinceMultipleAppExitCode() {
        final ReplicateStatusDetails statusUpdateDetails = ReplicateStatusDetails.builder()
                .exitCode(0)
                .build();

        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .details(statusUpdateDetails)
                .status(CURRENT_STATUS)
                .build();

        final List<ReplicateStatusUpdate> statusUpdateList = List.of(statusUpdate, statusUpdate);

        Replicate entity = createReplicate();
        entity.setStatusUpdateList(statusUpdateList);

        final MultipleOccurrencesOfFieldNotAllowed exception = Assertions
                .assertThrows(MultipleOccurrencesOfFieldNotAllowed.class, entity::generateModel);
        Assertions.assertEquals("exitCode", exception.getFieldName());
    }

    @Test
    void shouldNotConvertFromEntityToDtoSinceMultipleTeeError() {
        final ReplicateStatusDetails statusUpdateDetails = ReplicateStatusDetails.builder()
                .teeSessionGenerationError("error")
                .build();

        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .details(statusUpdateDetails)
                .status(CURRENT_STATUS)
                .build();

        final List<ReplicateStatusUpdate> statusUpdateList = List.of(statusUpdate, statusUpdate);

        Replicate entity = createReplicate();
        entity.setStatusUpdateList(statusUpdateList);

        final MultipleOccurrencesOfFieldNotAllowed exception = Assertions
                .assertThrows(MultipleOccurrencesOfFieldNotAllowed.class, entity::generateModel);
        Assertions.assertEquals("teeSessionGenerationError", exception.getFieldName());
    }

    private Replicate createReplicate() {
        Replicate replicate = new Replicate();
        replicate.setChainTaskId(CHAIN_TASK_ID);
        replicate.setWalletAddress(WALLET_ADDRESS);
        replicate.setResultLink(RESULT_LINK);
        replicate.setChainCallbackData(CHAIN_CALLBACK_DATA);
        replicate.setContributionHash(CONTRIBUTION_HASH);
        return replicate;
    }
}
