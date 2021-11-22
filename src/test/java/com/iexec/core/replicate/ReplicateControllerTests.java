package com.iexec.core.replicate;

import com.iexec.common.chain.WorkerpoolAuthorization;
import com.iexec.common.notification.TaskNotification;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.core.security.JwtTokenProvider;
import com.iexec.core.worker.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class ReplicateControllerTests {

    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final String WALLET_ADDRESS = "walletAddress";
    private static final String TOKEN = "token";
    private static final int BLOCK_NUMBER = 1;
    private static final WorkerpoolAuthorization AUTH = WorkerpoolAuthorization.builder()
            .chainTaskId(CHAIN_TASK_ID)
            .workerWallet(WALLET_ADDRESS)
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

    @InjectMocks
    private ReplicatesController replicatesController;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    // available replicate

    @Test
    public void shouldGetAvailableReplicate() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET_ADDRESS);
        when(workerService.isWorkerAllowedToAskReplicate(WALLET_ADDRESS))
                .thenReturn(true);
        when(replicateSupplyService
                .getAuthOfAvailableReplicate(BLOCK_NUMBER, WALLET_ADDRESS))
                .thenReturn(Optional.of(AUTH));

        ResponseEntity<WorkerpoolAuthorization> response =
                replicatesController.getAvailableReplicate(BLOCK_NUMBER, TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        WorkerpoolAuthorization auth = response.getBody();
        assertThat(auth.getChainTaskId()).isEqualTo(CHAIN_TASK_ID);
    }

    @Test
    public void shouldNotGetAvailableReplicateSinceNotAuthorizedToken() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn("");

        ResponseEntity<WorkerpoolAuthorization> response =
                replicatesController.getAvailableReplicate(BLOCK_NUMBER, TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void shouldNotGetAvailableReplicateSinceNotAllowed() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET_ADDRESS);
        when(workerService.isWorkerAllowedToAskReplicate(WALLET_ADDRESS))
                .thenReturn(false);

        ResponseEntity<WorkerpoolAuthorization> response =
                replicatesController.getAvailableReplicate(BLOCK_NUMBER, TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    public void shouldNotGetAvailableReplicateSinceNoReplicateAvailable() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET_ADDRESS);
        when(workerService.isWorkerAllowedToAskReplicate(WALLET_ADDRESS))
                .thenReturn(true);
        when(replicateSupplyService
                .getAuthOfAvailableReplicate(BLOCK_NUMBER, WALLET_ADDRESS))
                .thenReturn(Optional.empty());

        ResponseEntity<WorkerpoolAuthorization> response =
                replicatesController.getAvailableReplicate(BLOCK_NUMBER, TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // interrupted replicate

    @Test
    public void shouldGetMissedNotifications() {
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
    public void shouldNotGetMissedNotificationsSinceUnauthorized() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn("");

        ResponseEntity<List<TaskNotification>> response =
                replicatesController.getMissedTaskNotifications(BLOCK_NUMBER, TOKEN);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void shouldGetEmptyMissedNotifications() {
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

    // update replicate

    @Test
    public void shouldUpdateReplicate() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET_ADDRESS);
        when(replicatesService.computeUpdateReplicateStatusArgs(CHAIN_TASK_ID, WALLET_ADDRESS, UPDATE))
                .thenReturn(UPDATE_ARGS);
        when(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_ADDRESS, UPDATE, UPDATE_ARGS))
                .thenReturn(ReplicateStatusUpdateError.NO_ERROR);
        when(replicatesService.updateReplicateStatus(CHAIN_TASK_ID, WALLET_ADDRESS, UPDATE, UPDATE_ARGS))
                .thenReturn(Optional.of(TaskNotificationType.PLEASE_DOWNLOAD_APP));
        
        ResponseEntity<TaskNotificationType> response =
                replicatesController.updateReplicateStatus(TOKEN, CHAIN_TASK_ID, UPDATE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .isEqualTo(TaskNotificationType.PLEASE_DOWNLOAD_APP);
        assertThat(UPDATE.getModifier()).isEqualTo(ReplicateStatusModifier.WORKER);
    }

    @Test
    public void shouldNotUpdateReplicateSinceUnauthorized() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn("");
        
        ResponseEntity<TaskNotificationType> response =
                replicatesController.updateReplicateStatus(TOKEN, CHAIN_TASK_ID, UPDATE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void shouldNotUpdateReplicateSinceForbidden() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET_ADDRESS);
        when(replicatesService.computeUpdateReplicateStatusArgs(CHAIN_TASK_ID, WALLET_ADDRESS, UPDATE))
                .thenReturn(UPDATE_ARGS);
        when(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_ADDRESS, UPDATE, UPDATE_ARGS))
                .thenReturn(ReplicateStatusUpdateError.GENERIC_CANT_UPDATE);
        
        ResponseEntity<TaskNotificationType> response =
                replicatesController.updateReplicateStatus(TOKEN, CHAIN_TASK_ID, UPDATE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void shouldReply208AlreadyReported() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET_ADDRESS);
        when(replicatesService.computeUpdateReplicateStatusArgs(CHAIN_TASK_ID, WALLET_ADDRESS, UPDATE))
                .thenReturn(UPDATE_ARGS);
        when(replicatesService.canUpdateReplicateStatus(CHAIN_TASK_ID, WALLET_ADDRESS, UPDATE, UPDATE_ARGS))
                .thenReturn(ReplicateStatusUpdateError.ALREADY_REPORTED);

        ResponseEntity<TaskNotificationType> response =
                replicatesController.updateReplicateStatus(TOKEN, CHAIN_TASK_ID, UPDATE);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ALREADY_REPORTED);
        assertThat(response.getBody())
                .isEqualTo(TaskNotificationType.PLEASE_WAIT);
    }
}
