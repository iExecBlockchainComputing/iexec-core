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

import com.iexec.common.security.Signature;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.CredentialsUtils;
import com.iexec.common.utils.HashUtils;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicateModel;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.security.ChallengeService;
import com.iexec.core.security.JwtTokenProvider;
import com.iexec.core.stdout.ReplicateStdout;
import com.iexec.core.stdout.StdoutService;
import com.iexec.core.stdout.TaskStdout;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.List;
import java.util.Optional;

import static com.iexec.common.utils.SignatureUtils.signMessageHashAndGetSignature;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Slf4j
class TaskControllerTests {

    public static final String TASK_ID = "0xtask";
    public static final String WORKER_ADDRESS = "0xworker";

    @Mock private ChallengeService challengeService;
    @Mock private IexecHubService iexecHubService;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private ReplicatesService replicatesService;
    @Mock private StdoutService stdoutService;
    @Mock private TaskService taskService;
    @InjectMocks private TaskController taskController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
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
        String privateKey = generateKey();
        String requesterAddress = CredentialsUtils.getAddress(privateKey);
        String challenge =  RandomStringUtils.randomAlphabetic(10);
        when(challengeService.getChallenge(requesterAddress)).thenReturn(challenge);
        when(jwtTokenProvider.createToken(requesterAddress)).thenReturn("token");
        ResponseEntity<String> response = taskController.getChallenge(requesterAddress);
        assertEquals(challenge, response.getBody());
        assertNotNull(challenge);
        String challengeHash = Hash.sha3String(challenge);
        Signature signature = signMessageHashAndGetSignature(challengeHash, privateKey);
        ResponseEntity<String> loginResponse = taskController.login(requesterAddress, signature);
        assertTrue(loginResponse.getStatusCode().is2xxSuccessful());
        verify(challengeService, times(2)).getChallenge(requesterAddress);
        verify(jwtTokenProvider).createToken(anyString());
    }

    @Test
    void shouldNotLogWithInvalidChallenge() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String privateKey = generateKey();
        String requesterAddress = CredentialsUtils.getAddress(privateKey);
        String challenge =  RandomStringUtils.randomAlphabetic(10);
        when(challengeService.getChallenge(requesterAddress)).thenReturn(challenge);
        String challengeHash = Hash.sha3String("bad-challenge");
        Signature signature = signMessageHashAndGetSignature(challengeHash, privateKey);
        ResponseEntity<String> loginResponse = taskController.login(requesterAddress, signature);
        assertEquals(HttpStatus.UNAUTHORIZED, loginResponse.getStatusCode());
        assertNull(loginResponse.getBody());
        verify(challengeService).getChallenge(requesterAddress);
        verifyNoInteractions(jwtTokenProvider);
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
        when(entity.isAppComputeStdoutPresent()).thenReturn(false);

        ReplicateModel dto = taskController.buildReplicateModel(entity);
        assertEquals(TASK_ID, dto.getChainTaskId());
        Assertions.assertTrue(dto.getSelf().endsWith("/tasks/0xtask/replicates/0xworker"));
        Assertions.assertNull(dto.getAppStdout());
    }

    @Test
    void shouldBuildReplicateModelWithStdout() {
        Replicate entity = mock(Replicate.class);
        when(entity.getChainTaskId()).thenReturn(TASK_ID);
        when(entity.getWalletAddress()).thenReturn(WORKER_ADDRESS);
        when(entity.isAppComputeStdoutPresent()).thenReturn(true);

        ReplicateModel dto = taskController.buildReplicateModel(entity);
        assertEquals(TASK_ID, dto.getChainTaskId());
        Assertions.assertTrue(dto.getSelf().endsWith("/tasks/0xtask/replicates/0xworker"));
        Assertions.assertTrue(dto.getAppStdout().endsWith("/tasks/0xtask/replicates/0xworker/stdout"));
    }
    //endregion

    //region getTaskStdout
    @Test
    void shouldGetTaskStdoutWhenAuthenticated()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String privateKey = generateKey();
        String requesterAddress = CredentialsUtils.getAddress(privateKey);
        TaskDescription description = TaskDescription.builder()
                .requester(requesterAddress)
                .build();
        TaskStdout taskStdout = TaskStdout.builder().build();
        String challenge = RandomStringUtils.randomAlphabetic(10);
        String signedChallenge = signMessageHashAndGetSignature(challenge, privateKey).getValue();
        when(jwtTokenProvider.getWalletAddress(signedChallenge)).thenReturn(requesterAddress);
        when(iexecHubService.getTaskDescription(TASK_ID)).thenReturn(description);
        when(stdoutService.getTaskStdout(TASK_ID)).thenReturn(Optional.of(taskStdout));
        ResponseEntity<TaskStdout> response = taskController.getTaskStdout(TASK_ID, signedChallenge);
        Assertions.assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    void shouldFailToGetTaskStdoutWithNotFoundStatus()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String privateKey = generateKey();
        String requesterAddress = CredentialsUtils.getAddress(privateKey);
        TaskDescription description = TaskDescription.builder()
                .requester(requesterAddress)
                .build();
        String challenge = RandomStringUtils.randomAlphabetic(10);
        String signedChallenge = signMessageHashAndGetSignature(challenge, privateKey).getValue();
        when(jwtTokenProvider.getWalletAddress(signedChallenge)).thenReturn(requesterAddress);
        when(iexecHubService.getTaskDescription(TASK_ID)).thenReturn(description);
        ResponseEntity<TaskStdout> response = taskController.getTaskStdout(TASK_ID, signedChallenge);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldFailToGetTaskStdoutWhenAddressMissingInToken() {
        when(jwtTokenProvider.getWalletAddress("")).thenReturn("");
        ResponseEntity<TaskStdout> response = taskController.getTaskStdout(TASK_ID, "");
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void shouldFailToGetTaskStdoutWhenNotTaskRequester()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String privateKey = generateKey();
        String requesterAddress = CredentialsUtils.getAddress(privateKey);
        TaskDescription description = TaskDescription.builder()
                .requester(CredentialsUtils.getAddress(generateKey()))
                .build();
        String challenge = RandomStringUtils.randomAlphabetic(10);
        String signedChallenge = signMessageHashAndGetSignature(challenge, requesterAddress).getValue();
        when(jwtTokenProvider.getWalletAddress(signedChallenge)).thenReturn(requesterAddress);
        when(iexecHubService.getTaskDescription(TASK_ID)).thenReturn(description);
        ResponseEntity<TaskStdout> response = taskController.getTaskStdout(TASK_ID, signedChallenge);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
    //endregion

    //region getReplicateStdout
    @Test
    void shouldGetReplicateStdoutWhenAuthenticated()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String privateKey = generateKey();
        String requesterAddress = CredentialsUtils.getAddress(privateKey);
        TaskDescription description = TaskDescription.builder()
                .requester(requesterAddress)
                .build();
        ReplicateStdout replicateStdout = ReplicateStdout.builder().build();
        String challenge = RandomStringUtils.randomAlphabetic(10);
        String signedChallenge = signMessageHashAndGetSignature(challenge, privateKey).getValue();
        when(jwtTokenProvider.getWalletAddress(signedChallenge)).thenReturn(requesterAddress);
        when(iexecHubService.getTaskDescription(TASK_ID)).thenReturn(description);
        when(stdoutService.getReplicateStdout(TASK_ID, WORKER_ADDRESS)).thenReturn(Optional.of(replicateStdout));
        ResponseEntity<ReplicateStdout> response = taskController.getReplicateStdout(TASK_ID, WORKER_ADDRESS, signedChallenge);
        Assertions.assertTrue(response.getStatusCode().is2xxSuccessful());
    }

    @Test
    void shouldFailToGetReplicateStdoutWithNotFoundStatus()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        String privateKey = generateKey();
        String requesterAddress = CredentialsUtils.getAddress(privateKey);
        TaskDescription description = TaskDescription.builder()
                .requester(requesterAddress)
                .build();
        String challenge = RandomStringUtils.randomAlphabetic(10);
        String signedChallenge = signMessageHashAndGetSignature(challenge, privateKey).getValue();
        when(jwtTokenProvider.getWalletAddress(signedChallenge)).thenReturn(requesterAddress);
        when(iexecHubService.getTaskDescription(TASK_ID)).thenReturn(description);
        ResponseEntity<ReplicateStdout> response = taskController.getReplicateStdout(TASK_ID, WORKER_ADDRESS, signedChallenge);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void shouldFailToGetReplicateStdoutWhenAddressMissingInToken() {
        when(jwtTokenProvider.getWalletAddress("")).thenReturn("");
        ResponseEntity<ReplicateStdout> response = taskController.getReplicateStdout(TASK_ID, WORKER_ADDRESS, "");
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void shouldFailToGetReplicateStdoutWhenNotTaskRequester()
            throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        TaskDescription description = TaskDescription.builder()
                .requester(CredentialsUtils.getAddress(generateKey()))
                .build();
        String privateKey = generateKey();
        String requesterAddress = CredentialsUtils.getAddress(privateKey);
        String challenge = RandomStringUtils.randomAlphabetic(10);
        String signedChallenge = signMessageHashAndGetSignature(challenge, requesterAddress).getValue();
        when(jwtTokenProvider.getWalletAddress(signedChallenge)).thenReturn(requesterAddress);
        when(iexecHubService.getTaskDescription(TASK_ID)).thenReturn(description);
        ResponseEntity<ReplicateStdout> response = taskController.getReplicateStdout(TASK_ID, WORKER_ADDRESS, signedChallenge);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }
    //endregion

}
