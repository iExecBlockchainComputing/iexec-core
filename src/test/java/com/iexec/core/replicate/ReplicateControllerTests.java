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

package com.iexec.core.replicate;

import com.iexec.common.replicate.*;
import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.notification.TaskNotification;
import com.iexec.commons.poco.notification.TaskNotificationType;
import com.iexec.core.chain.BlockchainConnectionHealthIndicator;
import com.iexec.core.security.JwtTokenProvider;
import com.iexec.core.worker.WorkerService;
import io.vavr.control.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ReplicateControllerTests {

    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final String WALLET_ADDRESS = "walletAddress";
    private static final String SMS_URL = "smsUrl";
    private static final String TOKEN = "token";
    private static final int BLOCK_NUMBER = 1;
    private static final WorkerpoolAuthorization AUTH = WorkerpoolAuthorization.builder()
            .chainTaskId(CHAIN_TASK_ID)
            .workerWallet(WALLET_ADDRESS)
            .build();
    private static final ReplicateTaskSummary REPLICATE_TASK_SUMMARY = ReplicateTaskSummary.builder()
            .workerpoolAuthorization(AUTH)
            .smsUrl(SMS_URL)
            .build();
    private static final ReplicateStatusUpdate UPDATE = ReplicateStatusUpdate.builder()
            .status(ReplicateStatus.STARTED)
            .build();
    private static final UpdateReplicateStatusArgs UPDATE_ARGS = UpdateReplicateStatusArgs.builder()
            .workerWeight(1)
            .build();

    @Mock
    private ReplicatesService replicatesService;
    @Mock
    private ReplicateSupplyService replicateSupplyService;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private WorkerService workerService;
    @Mock
    private BlockchainConnectionHealthIndicator blockchainConnectionHealthIndicator;

    @InjectMocks
    private ReplicatesController replicatesController;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    //region available replicate

    @Test
    void shouldGetAvailableReplicate() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET_ADDRESS);
        when(blockchainConnectionHealthIndicator.isUp())
                .thenReturn(true);
        when(workerService.isWorkerAllowedToAskReplicate(WALLET_ADDRESS))
                .thenReturn(true);
        when(replicateSupplyService
                .getAvailableReplicateTaskSummary(BLOCK_NUMBER, WALLET_ADDRESS))
                .thenReturn(Optional.of(REPLICATE_TASK_SUMMARY));

        ResponseEntity<ReplicateTaskSummary> replicateTaskSummaryResponse =
                replicatesController.getAvailableReplicateTaskSummary(BLOCK_NUMBER, TOKEN);

        assertThat(replicateTaskSummaryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        ReplicateTaskSummary replicateTaskSummary = replicateTaskSummaryResponse.getBody();
        assertThat(replicateTaskSummary.getWorkerpoolAuthorization().getChainTaskId()).isEqualTo(CHAIN_TASK_ID);

        verify(blockchainConnectionHealthIndicator, times(1)).isUp();
    }

    @Test
    void shouldNotGetAvailableReplicateSinceNotAuthorizedToken() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn("");

        ResponseEntity<ReplicateTaskSummary> replicateTaskSummaryResponse =
                replicatesController.getAvailableReplicateTaskSummary(BLOCK_NUMBER, TOKEN);

        assertThat(replicateTaskSummaryResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        verify(blockchainConnectionHealthIndicator, never()).isUp();
    }

    @Test
    void shouldNotGetAvailableReplicateSinceChainIsDown() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET_ADDRESS);
        when(blockchainConnectionHealthIndicator.isUp())
                .thenReturn(false);

        ResponseEntity<ReplicateTaskSummary> replicateTaskSummaryResponse =
                replicatesController.getAvailableReplicateTaskSummary(BLOCK_NUMBER, TOKEN);

        assertThat(replicateTaskSummaryResponse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        verify(blockchainConnectionHealthIndicator, times(1)).isUp();
    }


    @Test
    void shouldNotGetAvailableReplicateSinceNotAllowed() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET_ADDRESS);
        when(blockchainConnectionHealthIndicator.isUp())
                .thenReturn(true);
        when(workerService.isWorkerAllowedToAskReplicate(WALLET_ADDRESS))
                .thenReturn(false);

        ResponseEntity<ReplicateTaskSummary> replicateTaskSummaryResponse =
                replicatesController.getAvailableReplicateTaskSummary(BLOCK_NUMBER, TOKEN);

        assertThat(replicateTaskSummaryResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        verify(blockchainConnectionHealthIndicator, times(1)).isUp();
    }

    @Test
    void shouldNotGetAvailableReplicateSinceNoReplicateAvailable() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET_ADDRESS);
        when(blockchainConnectionHealthIndicator.isUp())
                .thenReturn(true);
        when(workerService.isWorkerAllowedToAskReplicate(WALLET_ADDRESS))
                .thenReturn(true);
        when(replicateSupplyService
                .getAvailableReplicateTaskSummary(BLOCK_NUMBER, WALLET_ADDRESS))
                .thenReturn(Optional.empty());

        ResponseEntity<ReplicateTaskSummary> replicateTaskSummaryResponse =
                replicatesController.getAvailableReplicateTaskSummary(BLOCK_NUMBER, TOKEN);

        assertThat(replicateTaskSummaryResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        verify(blockchainConnectionHealthIndicator, times(1)).isUp();
    }
    //endregion

    //region interrupted replicate
    @Test
    void shouldGetMissedNotifications() {
        TaskNotification notification = TaskNotification.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .build();
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET_ADDRESS);
        when(replicateSupplyService
                .getMissedTaskNotifications(BLOCK_NUMBER, WALLET_ADDRESS))
                .thenReturn(List.of(notification));

        ResponseEntity<List<TaskNotification>> response =
                replicatesController.getMissedTaskNotifications(BLOCK_NUMBER, TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<TaskNotification> notifications = response.getBody();
        assertThat(notifications.get(0).getChainTaskId()).isEqualTo(CHAIN_TASK_ID);
    }

    @Test
    void shouldNotGetMissedNotificationsSinceUnauthorized() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn("");

        ResponseEntity<List<TaskNotification>> response =
                replicatesController.getMissedTaskNotifications(BLOCK_NUMBER, TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldGetEmptyMissedNotifications() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET_ADDRESS);
        when(replicateSupplyService
                .getMissedTaskNotifications(BLOCK_NUMBER, WALLET_ADDRESS))
                .thenReturn(List.of());

        ResponseEntity<List<TaskNotification>> response =
                replicatesController.getMissedTaskNotifications(BLOCK_NUMBER, TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<TaskNotification> notifications = response.getBody();
        assertThat(notifications).isEmpty();
    }
    //endregion

    //region update replicate
    @Test
    void shouldUpdateReplicate() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET_ADDRESS);
        when(blockchainConnectionHealthIndicator.isUp())
                .thenReturn(true);
        when(replicatesService.computeUpdateReplicateStatusArgs(CHAIN_TASK_ID, WALLET_ADDRESS, UPDATE))
                .thenReturn(UPDATE_ARGS);
        when(replicatesService.canUpdateReplicateStatus(new Replicate(CHAIN_TASK_ID, WALLET_ADDRESS), UPDATE, UPDATE_ARGS))
                .thenReturn(ReplicateStatusUpdateError.NO_ERROR);
        when(replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_ADDRESS, UPDATE, UPDATE_ARGS))
                .thenReturn(Either.right(TaskNotificationType.PLEASE_DOWNLOAD_APP));

        ResponseEntity<TaskNotificationType> response =
                replicatesController.updateReplicateStatus(TOKEN, CHAIN_TASK_ID, UPDATE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .isEqualTo(TaskNotificationType.PLEASE_DOWNLOAD_APP);
        assertThat(UPDATE.getModifier()).isEqualTo(ReplicateStatusModifier.WORKER);

        verify(blockchainConnectionHealthIndicator, times(1)).isUp();
    }

    @Test
    void shouldUpdateReplicateAndSetWalletAddress() {
        final ReplicateStatusUpdate updateWithLogs = ReplicateStatusUpdate.builder()
                .status(ReplicateStatus.STARTED)
                .details(ReplicateStatusDetails.builder().computeLogs(ComputeLogs.builder().walletAddress("wrongWalletAddress").build()).build())
                .build();

        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET_ADDRESS);
        when(blockchainConnectionHealthIndicator.isUp())
                .thenReturn(true);
        when(replicatesService.computeUpdateReplicateStatusArgs(CHAIN_TASK_ID, WALLET_ADDRESS, updateWithLogs))
                .thenReturn(UPDATE_ARGS);
        when(replicatesService.canUpdateReplicateStatus(new Replicate(CHAIN_TASK_ID, WALLET_ADDRESS), updateWithLogs, UPDATE_ARGS))
                .thenReturn(ReplicateStatusUpdateError.NO_ERROR);
        when(replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_ADDRESS, updateWithLogs, UPDATE_ARGS))
                .thenReturn(Either.right((TaskNotificationType.PLEASE_DOWNLOAD_APP)));

        ResponseEntity<TaskNotificationType> response =
                replicatesController.updateReplicateStatus(TOKEN, CHAIN_TASK_ID, updateWithLogs);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .isEqualTo(TaskNotificationType.PLEASE_DOWNLOAD_APP);
        assertThat(updateWithLogs.getModifier()).isEqualTo(ReplicateStatusModifier.WORKER);
        assertThat(updateWithLogs.getDetails().getComputeLogs().getWalletAddress()).isEqualTo(WALLET_ADDRESS);

        verify(blockchainConnectionHealthIndicator, times(1)).isUp();
    }

    @Test
    void shouldNotUpdateReplicateSinceUnauthorized() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn("");

        ResponseEntity<TaskNotificationType> response =
                replicatesController.updateReplicateStatus(TOKEN, CHAIN_TASK_ID, UPDATE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNull();

        verify(blockchainConnectionHealthIndicator, never()).isUp();
    }

    @Test
    void shouldReturnServiceUnavailableSinceChainIsDown() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET_ADDRESS);
        when(blockchainConnectionHealthIndicator.isUp())
                .thenReturn(false);

        ResponseEntity<TaskNotificationType> response =
                replicatesController.updateReplicateStatus(TOKEN, CHAIN_TASK_ID, UPDATE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        verify(blockchainConnectionHealthIndicator, times(1)).isUp();
    }


    @ParameterizedTest
    @EnumSource(value = ReplicateStatusUpdateError.class, names = {
            "UNKNOWN_REPLICATE",
            "UNKNOWN_TASK",
            "BAD_WORKFLOW_TRANSITION",
            "GENERIC_CANT_UPDATE"
    })
    void shouldReturnPleaseAbortSinceCantUpdate(ReplicateStatusUpdateError error) {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET_ADDRESS);
        when(blockchainConnectionHealthIndicator.isUp())
                .thenReturn(true);
        when(replicatesService.computeUpdateReplicateStatusArgs(CHAIN_TASK_ID, WALLET_ADDRESS, UPDATE))
                .thenReturn(UPDATE_ARGS);
        when(replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_ADDRESS, UPDATE, UPDATE_ARGS))
                .thenReturn(Either.left(error));

        ResponseEntity<TaskNotificationType> response =
                replicatesController.updateReplicateStatus(TOKEN, CHAIN_TASK_ID, UPDATE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(TaskNotificationType.PLEASE_ABORT);

        verify(blockchainConnectionHealthIndicator, times(1)).isUp();
    }

    @Test
    void shouldReply208AlreadyReported() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET_ADDRESS);
        when(blockchainConnectionHealthIndicator.isUp())
                .thenReturn(true);
        when(replicatesService.computeUpdateReplicateStatusArgs(CHAIN_TASK_ID, WALLET_ADDRESS, UPDATE))
                .thenReturn(UPDATE_ARGS);
        when(replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_ADDRESS, UPDATE, UPDATE_ARGS))
                .thenReturn(Either.left(ReplicateStatusUpdateError.ALREADY_REPORTED));

        ResponseEntity<TaskNotificationType> response =
                replicatesController.updateReplicateStatus(TOKEN, CHAIN_TASK_ID, UPDATE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ALREADY_REPORTED);
        assertThat(response.getBody())
                .isEqualTo(TaskNotificationType.PLEASE_WAIT);

        verify(blockchainConnectionHealthIndicator, times(1)).isUp();
    }

    @Test
    void shouldReply500WhenErrorNotExpected() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET_ADDRESS);
        when(blockchainConnectionHealthIndicator.isUp())
                .thenReturn(true);
        when(replicatesService.computeUpdateReplicateStatusArgs(CHAIN_TASK_ID, WALLET_ADDRESS, UPDATE))
                .thenReturn(UPDATE_ARGS);
        when(replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_ADDRESS, UPDATE, UPDATE_ARGS))
                .thenReturn(Either.left(ReplicateStatusUpdateError.NO_ERROR));

        ResponseEntity<TaskNotificationType> response =
                replicatesController.updateReplicateStatus(TOKEN, CHAIN_TASK_ID, UPDATE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

        verify(blockchainConnectionHealthIndicator, times(1)).isUp();
    }
    //endregion
}
