/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ReplicateTests {

    private static final String WALLET_WORKER = "worker";
    private static final String CHAIN_TASK_ID = "chainTaskId";


    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldSerializeAndDeserializeReplicate() throws JsonProcessingException {
        Replicate replicate = new Replicate("walletAddress", "chainTaskId");
        assertThat(replicate.getStatusUpdateList()).hasSize(1);
        String jsonString = mapper.writeValueAsString(replicate);
        long date = replicate.getStatusUpdateList().get(0).getDate().getTime();
        String expectedString = "{"
                + "\"statusUpdateList\":[{\"status\":\"CREATED\",\"modifier\":\"POOL_MANAGER\","
                + "\"date\":" + date + ",\"details\":null,\"success\":true}],"
                + "\"walletAddress\":\"walletAddress\","
                + "\"resultLink\":null,"
                + "\"chainCallbackData\":null,"
                + "\"chainTaskId\":\"chainTaskId\","
                + "\"contributionHash\":\"\","
                + "\"workerWeight\":0,"
                + "\"appComputeLogsPresent\":false"
                + "}";
        ObjectNode actualJsonNode = (ObjectNode) mapper.readTree(jsonString);
        ObjectNode expectedJsonNode = (ObjectNode) mapper.readTree(expectedString);
        assertThat(actualJsonNode).isEqualTo(expectedJsonNode);
        Replicate deserializedReplicate = mapper.readValue(jsonString, Replicate.class);
        assertThat(deserializedReplicate).usingRecursiveComparison().isEqualTo(replicate);
    }

    @Test
    void shouldInitializeStatusProperly() {
        Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        assertThat(replicate.getStatusUpdateList()).hasSize(1);

        ReplicateStatusUpdate statusChange = replicate.getStatusUpdateList().get(0);
        assertThat(statusChange.getStatus()).isEqualTo(ReplicateStatus.CREATED);

        Date now = new Date();
        long duration = now.getTime() - statusChange.getDate().getTime();
        long diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
        assertThat(diffInSeconds).isNotPositive();
    }

    @Test
    void shouldUpdateReplicateStatus() {
        Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        assertThat(replicate.getStatusUpdateList()).hasSize(1);

        // only pool manager sets date of the update
        replicate.updateStatus(STARTING, ReplicateStatusModifier.POOL_MANAGER);
        assertThat(replicate.getStatusUpdateList()).hasSize(2);

        ReplicateStatusUpdate initialStatus = replicate.getStatusUpdateList().get(0);
        assertThat(initialStatus.getStatus()).isEqualTo(ReplicateStatus.CREATED);

        ReplicateStatusUpdate updatedStatus = replicate.getStatusUpdateList().get(1);
        assertThat(updatedStatus.getStatus()).isEqualTo(STARTING);

        Date now = new Date();
        long duration = now.getTime() - updatedStatus.getDate().getTime();
        long diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
        assertThat(diffInSeconds).isNotPositive();
    }

    @Test
    void shouldGetProperLatestStatus() {
        Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        assertThat(replicate.getStatusUpdateList()).hasSize(1);
        assertThat(replicate.getCurrentStatus()).isEqualTo(ReplicateStatus.CREATED);

        replicate.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        assertThat(replicate.getStatusUpdateList()).hasSize(2);
        assertThat(replicate.getCurrentStatus()).isEqualTo(STARTING);
    }


    @Test
    void shouldReturnTrueWhenContributed() {
        Replicate replicate = new Replicate("0x1", CHAIN_TASK_ID);
        replicate.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        assertThat(replicate.containsContributedStatus()).isTrue();
    }

    @Test
    void shouldReturnFalseWhenContributedMissing() {
        Replicate replicate = new Replicate("0x1", CHAIN_TASK_ID);
        replicate.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.CONTRIBUTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.REVEALING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(ReplicateStatus.REVEALED, ReplicateStatusModifier.WORKER);

        assertThat(replicate.containsContributedStatus()).isFalse();
    }

    @Test
    void shouldBeCreatedLongAgo() {
        final long maxExecutionTime = 60000;
        Date now = new Date();
        Replicate replicate = new Replicate("0x1", CHAIN_TASK_ID);
        ReplicateStatusUpdate oldCreationDate = replicate.getStatusUpdateList().get(0);
        oldCreationDate.setDate(new Date(now.getTime() - 3 * maxExecutionTime));
        replicate.setStatusUpdateList(Collections.singletonList(oldCreationDate));

        assertThat(replicate.isCreatedMoreThanNPeriodsAgo(2, maxExecutionTime)).isTrue();
    }

    @Test
    void shouldNotBeCreatedLongAgo() {
        final long maxExecutionTime = 60000;
        Date now = new Date();
        Replicate replicate = new Replicate("0x1", CHAIN_TASK_ID);
        ReplicateStatusUpdate oldCreationDate = replicate.getStatusUpdateList().get(0);
        oldCreationDate.setDate(new Date(now.getTime() - maxExecutionTime));
        replicate.setStatusUpdateList(Collections.singletonList(oldCreationDate));

        assertThat(replicate.isCreatedMoreThanNPeriodsAgo(2, maxExecutionTime)).isFalse();
    }

    // region getLastRelevantStatus
    @Test
    void shouldGetLastRelevantStatusWhenOnlyRelevantStatus() {
        final Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        replicate.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(STARTED, ReplicateStatusModifier.WORKER);

        assertThat(replicate.getLastRelevantStatus()).isEqualTo(STARTED);
    }

    @Test
    void shouldGetLastRelevantStatusWhenRelevantAndIrrelevantStatus() {
        final Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        replicate.updateStatus(STARTING, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(STARTED, ReplicateStatusModifier.WORKER);
        replicate.updateStatus(WORKER_LOST, ReplicateStatusModifier.WORKER);

        assertThat(replicate.getLastRelevantStatus()).isEqualTo(STARTED);
    }

    @Test
    void shouldNotGetLastRelevantStatusWhenNoStatusAtAll() {
        final Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        ReflectionTestUtils.setField(replicate, "statusUpdateList", List.of());

        assertThatExceptionOfType(NoReplicateStatusException.class)
                .isThrownBy(replicate::getLastRelevantStatus)
                .extracting(NoReplicateStatusException::getChainTaskId)
                .isEqualTo(CHAIN_TASK_ID);
    }

    @Test
    void shouldNotGetLastRelevantStatusWhenOnlyWorkerLost() {
        final Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        ReflectionTestUtils.setField(replicate, "statusUpdateList", List.of(ReplicateStatusUpdate.poolManagerRequest(WORKER_LOST)));

        assertThatExceptionOfType(NoReplicateStatusException.class)
                .isThrownBy(replicate::getLastRelevantStatus)
                .extracting(NoReplicateStatusException::getChainTaskId)
                .isEqualTo(CHAIN_TASK_ID);
    }

    @Test
    void shouldNotGetLastRelevantStatusWhenOnlyRecovering() {
        final Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        ReflectionTestUtils.setField(replicate, "statusUpdateList", List.of(ReplicateStatusUpdate.poolManagerRequest(RECOVERING)));

        assertThatExceptionOfType(NoReplicateStatusException.class)
                .isThrownBy(replicate::getLastRelevantStatus)
                .extracting(NoReplicateStatusException::getChainTaskId)
                .isEqualTo(CHAIN_TASK_ID);
    }

    @Test
    void shouldNotGetLastRelevantStatusWhenWorkerLostAndyRecovering() {
        final Replicate replicate = new Replicate(WALLET_WORKER, CHAIN_TASK_ID);
        ReflectionTestUtils.setField(replicate, "statusUpdateList", List.of(
                ReplicateStatusUpdate.poolManagerRequest(WORKER_LOST),
                ReplicateStatusUpdate.poolManagerRequest(RECOVERING)
        ));

        assertThatExceptionOfType(NoReplicateStatusException.class)
                .isThrownBy(replicate::getLastRelevantStatus)
                .extracting(NoReplicateStatusException::getChainTaskId)
                .isEqualTo(CHAIN_TASK_ID);
    }
    // endregion
}
