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

import com.iexec.common.chain.eip712.entity.Challenge;
import com.iexec.common.chain.eip712.entity.EIP712Challenge;
import com.iexec.common.chain.eip712.EIP712Domain;
import com.iexec.common.replicate.ComputeLogs;
import com.iexec.common.security.Signature;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.CredentialsUtils;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.logs.TaskLogs;
import com.iexec.core.logs.TaskLogsService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicateModel;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.security.EIP712ChallengeService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Slf4j
class TaskControllerTests {

    private static final String LOGS_REQUESTER_TOKEN = "Bearer logsRequesterToken";
    private static final String TASK_REQUESTER_TOKEN = "Bearer taskRequesterToken";
    private static final String TASK_ID = "0xtask";
    private static final String WORKER_ADDRESS = "0xworker";

    private EIP712Challenge challenge;
    private EIP712Challenge badChallenge;

    @Mock private EIP712ChallengeService challengeService;
    @Mock private IexecHubService iexecHubService;
    @Mock private ReplicatesService replicatesService;
    @Mock private TaskService taskService;
    @Mock private TaskLogsService taskLogsService;
    @InjectMocks private TaskController taskController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        EIP712Domain domain = new EIP712Domain();
        challenge = new EIP712Challenge(domain, Challenge.builder().challenge("challenge").build());
        badChallenge = new EIP712Challenge(domain, Challenge.builder().challenge("bad-challenge").build());
    }

    //region utilities
    String generateKey() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        BigInteger pk = Keys.createEcKeyPair().getPrivateKey();
        return String.format("0x%032x", pk);
    }
    //endregion

    //region login
    @Test
    void shouldLogin() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        ECKeyPair ecKeyPair = Keys.createEcKeyPair();
        String privateKey = String.format("0x%032x", ecKeyPair.getPrivateKey());
        String requesterAddress = CredentialsUtils.getAddress(privateKey);
        when(challengeService.getChallenge(requesterAddress)).thenReturn(challenge);
        when(challengeService.createToken(requesterAddress)).thenReturn("token");
        ResponseEntity<EIP712Challenge> response = taskController.getChallenge(requesterAddress);
        assertEquals(challenge, response.getBody());
        String signatureString = challenge.signMessage(ecKeyPair);
        Signature signature = new Signature(Numeric.cleanHexPrefix(signatureString));
        ResponseEntity<String> loginResponse = taskController.login(requesterAddress, signature);
        assertTrue(loginResponse.getStatusCode().is2xxSuccessful());
        verify(challengeService, times(2)).getChallenge(requesterAddress);
        verify(challengeService).createToken(anyString());
    }

    @Test
    void shouldNotLogWithInvalidChallenge() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        ECKeyPair ecKeyPair = Keys.createEcKeyPair();
        String privateKey = String.format("0x%032x", ecKeyPair.getPrivateKey());
        String requesterAddress = CredentialsUtils.getAddress(privateKey);
        when(challengeService.getChallenge(requesterAddress)).thenReturn(challenge);
        String signatureString = badChallenge.signMessage(ecKeyPair);
        Signature signature = new Signature(Numeric.cleanHexPrefix(signatureString));
        ResponseEntity<String> loginResponse = taskController.login(requesterAddress, signature);
        assertEquals(HttpStatus.UNAUTHORIZED, loginResponse.getStatusCode());
        assertNull(loginResponse.getBody());
        verify(challengeService).getChallenge(requesterAddress);
    }
    //endregion

    //region getTask
    @Test
    void shouldGetTaskModel() {
        Task taskEntity = mock(Task.class);
        when(taskEntity.getChainTaskId()).thenReturn(TASK_ID);
        when(taskService.getTaskByChainTaskId(TASK_ID))
                .thenReturn(Optional.of(taskEntity));

        ResponseEntity<TaskModel> taskResponse = taskController.getTask(TASK_ID);
        Assertions.assertTrue(taskResponse.getStatusCode().is2xxSuccessful());
        TaskModel task = taskResponse.getBody();
        Assertions.assertNotNull(task);
        assertEquals(TASK_ID, task.getChainTaskId());
        Assertions.assertNull(task.getReplicates());
    }

    @Test
    void shouldGetTaskModelWithReplicates() {
        Task taskEntity = mock(Task.class);
        when(taskEntity.getChainTaskId()).thenReturn(TASK_ID);
        when(taskService.getTaskByChainTaskId(TASK_ID))
                .thenReturn(Optional.of(taskEntity));
        when(replicatesService.hasReplicatesList(TASK_ID))
                .thenReturn(true);
        Replicate replicateEntity = new Replicate(WORKER_ADDRESS, TASK_ID);
        when(replicatesService.getReplicates(TASK_ID))
                .thenReturn(List.of(replicateEntity));

        ResponseEntity<TaskModel> taskResponse = taskController.getTask(TASK_ID);
        Assertions.assertTrue(taskResponse.getStatusCode().is2xxSuccessful());
        TaskModel task = taskResponse.getBody();
        Assertions.assertNotNull(task);
        assertEquals(TASK_ID, task.getChainTaskId());
        assertEquals(TASK_ID,
                task.getReplicates().get(0).getChainTaskId());
        assertEquals(WORKER_ADDRESS,
                task.getReplicates().get(0).getWalletAddress());
    }
    //endregion

    //region getTaskReplicate
    @Test
    void shouldGetReplicate() {
        Replicate replicateEntity = new Replicate(WORKER_ADDRESS, TASK_ID);
        when(replicatesService.getReplicate(TASK_ID, WORKER_ADDRESS))
                .thenReturn(Optional.of(replicateEntity));

        ResponseEntity<ReplicateModel> replicateResponse =
                taskController.getTaskReplicate(TASK_ID, WORKER_ADDRESS);
        Assertions.assertTrue(replicateResponse.getStatusCode().is2xxSuccessful());
        ReplicateModel replicate = replicateResponse.getBody();
        Assertions.assertNotNull(replicate);
        assertEquals(TASK_ID, replicate.getChainTaskId());
        assertEquals(WORKER_ADDRESS, replicate.getWalletAddress());
    }

    @Test
    void shouldNotGetReplicate() {
        when(replicatesService.getReplicate(TASK_ID, WORKER_ADDRESS))
                .thenReturn(Optional.empty());

        ResponseEntity<ReplicateModel> replicateResponse =
                taskController.getTaskReplicate(TASK_ID, WORKER_ADDRESS);
        Assertions.assertTrue(replicateResponse.getStatusCode().is4xxClientError());
    }
    //endregion

    //region buildReplicateModel
    @Test
    void shouldBuildReplicateModel() {
        Replicate entity = mock(Replicate.class);
        when(entity.getChainTaskId()).thenReturn(TASK_ID);
        when(entity.getWalletAddress()).thenReturn(WORKER_ADDRESS);
        when(entity.isAppComputeLogsPresent()).thenReturn(false);

        ReplicateModel dto = taskController.buildReplicateModel(entity);
        assertEquals(TASK_ID, dto.getChainTaskId());
        Assertions.assertTrue(dto.getSelf().endsWith("/tasks/0xtask/replicates/0xworker"));
        Assertions.assertNull(dto.getAppLogs());
    }

    @Test
    void shouldBuildReplicateModelWithComputeLogs() {
        Replicate entity = mock(Replicate.class);
        when(entity.getChainTaskId()).thenReturn(TASK_ID);
        when(entity.getWalletAddress()).thenReturn(WORKER_ADDRESS);
        when(entity.isAppComputeLogsPresent()).thenReturn(true);

        ReplicateModel dto = taskController.buildReplicateModel(entity);
        assertEquals(TASK_ID, dto.getChainTaskId());
        Assertions.assertTrue(dto.getSelf().endsWith("/tasks/0xtask/replicates/0xworker"));
        Assertions.assertTrue(dto.getAppLogs().endsWith("/tasks/0xtask/replicates/0xworker/stdout"));
    }
    //endregion

    //region getTaskLogs
    @Test
    void shouldGetTaskLogsWhenAuthenticated()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String privateKey = generateKey();
        String requesterAddress = CredentialsUtils.getAddress(privateKey);
        TaskDescription description = TaskDescription.builder()
                .requester(requesterAddress)
                .build();
        TaskLogs taskStdout = TaskLogs.builder().build();
        when(challengeService.getWalletAddressFromBearerToken(TASK_REQUESTER_TOKEN)).thenReturn(requesterAddress);
        when(challengeService.isValidToken(TASK_REQUESTER_TOKEN)).thenReturn(true);
        when(iexecHubService.getTaskDescription(TASK_ID)).thenReturn(description);
        when(taskLogsService.getTaskLogs(TASK_ID)).thenReturn(Optional.of(taskStdout));
        ResponseEntity<TaskLogs> response = taskController.getTaskLogs(TASK_ID, TASK_REQUESTER_TOKEN);
        Assertions.assertTrue(response.getStatusCode().is2xxSuccessful());
        verify(challengeService).getWalletAddressFromBearerToken(TASK_REQUESTER_TOKEN);
        verify(challengeService).isValidToken(TASK_REQUESTER_TOKEN);
        verify(iexecHubService).getTaskDescription(TASK_ID);
    }

    @Test
    void shouldFailToGetTaskLogsWithNotFoundStatus()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String privateKey = generateKey();
        String requesterAddress = CredentialsUtils.getAddress(privateKey);
        TaskDescription description = TaskDescription.builder()
                .requester(requesterAddress)
                .build();
        when(challengeService.getWalletAddressFromBearerToken(TASK_REQUESTER_TOKEN)).thenReturn(requesterAddress);
        when(challengeService.isValidToken(TASK_REQUESTER_TOKEN)).thenReturn(true);
        when(iexecHubService.getTaskDescription(TASK_ID)).thenReturn(description);
        ResponseEntity<TaskLogs> response = taskController.getTaskLogs(TASK_ID, TASK_REQUESTER_TOKEN);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(challengeService).getWalletAddressFromBearerToken(TASK_REQUESTER_TOKEN);
        verify(challengeService).isValidToken(TASK_REQUESTER_TOKEN);
        verify(iexecHubService).getTaskDescription(TASK_ID);
    }

    @Test
    void shouldFailToGetTaskLogsWhenAddressMissingInToken() {
        when(challengeService.getWalletAddressFromBearerToken("")).thenReturn("");
        when(challengeService.isValidToken("")).thenReturn(false);
        ResponseEntity<TaskLogs> response = taskController.getTaskLogs(TASK_ID, "");
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(challengeService).getWalletAddressFromBearerToken("");
        verify(challengeService).isValidToken("");
        verifyNoInteractions(iexecHubService);
    }

    @Test
    void shouldFailToGetTaskLogsWhenNotTaskRequester()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String privateKey = generateKey();
        String requesterAddress = CredentialsUtils.getAddress(privateKey);
        TaskDescription description = TaskDescription.builder()
                .requester(CredentialsUtils.getAddress(generateKey()))
                .build();
        when(challengeService.getWalletAddressFromBearerToken(LOGS_REQUESTER_TOKEN)).thenReturn(requesterAddress);
        when(challengeService.isValidToken(LOGS_REQUESTER_TOKEN)).thenReturn(true);
        when(iexecHubService.getTaskDescription(TASK_ID)).thenReturn(description);
        ResponseEntity<TaskLogs> response = taskController.getTaskLogs(TASK_ID, LOGS_REQUESTER_TOKEN);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(challengeService).getWalletAddressFromBearerToken(LOGS_REQUESTER_TOKEN);
        verify(challengeService).isValidToken(LOGS_REQUESTER_TOKEN);
        verify(iexecHubService).getTaskDescription(TASK_ID);
    }
    //endregion

    //region getComputeLogs
    @Test
    void shouldGetComputeLogsWhenAuthenticated()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String privateKey = generateKey();
        String requesterAddress = CredentialsUtils.getAddress(privateKey);
        TaskDescription description = TaskDescription.builder()
                .requester(requesterAddress)
                .build();
        ComputeLogs computeLogs = ComputeLogs.builder().build();
        when(challengeService.getWalletAddressFromBearerToken(TASK_REQUESTER_TOKEN)).thenReturn(requesterAddress);
        when(challengeService.isValidToken(TASK_REQUESTER_TOKEN)).thenReturn(true);
        when(iexecHubService.getTaskDescription(TASK_ID)).thenReturn(description);
        when(taskLogsService.getComputeLogs(TASK_ID, WORKER_ADDRESS)).thenReturn(Optional.of(computeLogs));
        ResponseEntity<ComputeLogs> response = taskController.getComputeLogs(TASK_ID, WORKER_ADDRESS, TASK_REQUESTER_TOKEN);
        Assertions.assertTrue(response.getStatusCode().is2xxSuccessful());
        verify(challengeService).getWalletAddressFromBearerToken(TASK_REQUESTER_TOKEN);
        verify(challengeService).isValidToken(TASK_REQUESTER_TOKEN);
        verify(iexecHubService).getTaskDescription(TASK_ID);
    }

    @Test
    void shouldFailToGetComputeLogsWithNotFoundStatus()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String privateKey = generateKey();
        String requesterAddress = CredentialsUtils.getAddress(privateKey);
        TaskDescription description = TaskDescription.builder()
                .requester(requesterAddress)
                .build();
        when(challengeService.getWalletAddressFromBearerToken(TASK_REQUESTER_TOKEN)).thenReturn(requesterAddress);
        when(challengeService.isValidToken(TASK_REQUESTER_TOKEN)).thenReturn(true);
        when(iexecHubService.getTaskDescription(TASK_ID)).thenReturn(description);
        ResponseEntity<ComputeLogs> response = taskController.getComputeLogs(TASK_ID, WORKER_ADDRESS, TASK_REQUESTER_TOKEN);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(challengeService).getWalletAddressFromBearerToken(TASK_REQUESTER_TOKEN);
        verify(challengeService).isValidToken(TASK_REQUESTER_TOKEN);
        verify(iexecHubService).getTaskDescription(TASK_ID);
    }

    @Test
    void shouldFailToGetComputeLogsWhenAddressMissingInToken() {
        when(challengeService.getWalletAddressFromBearerToken("")).thenReturn("");
        when(challengeService.isValidToken("")).thenReturn(false);
        ResponseEntity<ComputeLogs> response = taskController.getComputeLogs(TASK_ID, WORKER_ADDRESS, "");
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(challengeService).getWalletAddressFromBearerToken("");
        verify(challengeService).isValidToken("");
        verifyNoInteractions(iexecHubService);
    }

    @Test
    void shouldFailToGetComputeLogsWhenNotTaskRequester()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String privateKey = generateKey();
        String requesterAddress = CredentialsUtils.getAddress(privateKey);
        TaskDescription description = TaskDescription.builder()
                .requester(CredentialsUtils.getAddress(generateKey()))
                .build();
        when(challengeService.getWalletAddressFromBearerToken(LOGS_REQUESTER_TOKEN)).thenReturn(requesterAddress);
        when(challengeService.isValidToken(LOGS_REQUESTER_TOKEN)).thenReturn(true);
        when(iexecHubService.getTaskDescription(TASK_ID)).thenReturn(description);
        ResponseEntity<ComputeLogs> response = taskController.getComputeLogs(TASK_ID, WORKER_ADDRESS, LOGS_REQUESTER_TOKEN);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verify(challengeService).getWalletAddressFromBearerToken(LOGS_REQUESTER_TOKEN);
        verify(challengeService).isValidToken(LOGS_REQUESTER_TOKEN);
        verify(iexecHubService).getTaskDescription(TASK_ID);
    }
    //endregion

}
