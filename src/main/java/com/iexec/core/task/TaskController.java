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

import com.iexec.common.replicate.ComputeLogs;
import com.iexec.common.security.SignedChallenge;
import com.iexec.commons.poco.eip712.entity.EIP712Challenge;
import com.iexec.commons.poco.security.Signature;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.utils.SignatureUtils;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.logs.TaskLogs;
import com.iexec.core.logs.TaskLogsModel;
import com.iexec.core.logs.TaskLogsService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicateModel;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.security.EIP712ChallengeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.web3j.utils.Numeric;

import java.util.stream.Collectors;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import static org.springframework.http.ResponseEntity.notFound;
import static org.springframework.http.ResponseEntity.ok;

@RestController
public class TaskController {

    private final EIP712ChallengeService eip712ChallengeService;
    private final IexecHubService iexecHubService;
    private final ReplicatesService replicatesService;
    private final TaskLogsService taskLogsService;
    private final TaskService taskService;

    public TaskController(EIP712ChallengeService eip712ChallengeService,
                          IexecHubService iexecHubService,
                          ReplicatesService replicatesService,
                          TaskLogsService taskLogsService,
                          TaskService taskService) {
        this.eip712ChallengeService = eip712ChallengeService;
        this.iexecHubService = iexecHubService;
        this.replicatesService = replicatesService;
        this.taskLogsService = taskLogsService;
        this.taskService = taskService;
    }

    @GetMapping("/tasks/logs/challenge")
    public ResponseEntity<EIP712Challenge> getChallenge(@RequestParam("address") String address) {
        return ok(eip712ChallengeService.getChallenge(address));
    }

    @GetMapping("/tasks/{chainTaskId}")
    public ResponseEntity<TaskModel> getTask(@PathVariable("chainTaskId") String chainTaskId) {
        return taskService.getTaskByChainTaskId(chainTaskId).map(task -> {
            TaskModel taskModel = task.generateModel();
            if (replicatesService.hasReplicatesList(chainTaskId)) {
                taskModel.setReplicates(replicatesService.getReplicates(chainTaskId)
                        .stream()
                        .map(this::buildReplicateModel)
                        .collect(Collectors.toList()));
            }
            return ok(taskModel);
        }).orElse(notFound().build());
    }

    @GetMapping("/tasks/{chainTaskId}/replicates/{walletAddress}")
    public ResponseEntity<ReplicateModel> getTaskReplicate(@PathVariable("chainTaskId") String chainTaskId,
                                                           @PathVariable("walletAddress") String walletAddress) {
        return replicatesService.getReplicate(chainTaskId, walletAddress)
                .map(replicate -> ok(buildReplicateModel(replicate)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Converts a replicate to a replicate model with links.
     * <p>
     * Note: Currently using links as string to avoid spring hateoas
     * dependencies in client.
     *
     * @param replicate replicate entity
     * @return replicate model
     */
    ReplicateModel buildReplicateModel(Replicate replicate) {
        ReplicateModel replicateModel = replicate.generateModel();
        if (replicate.isAppComputeLogsPresent()) {
            String logs = linkTo(methodOn(TaskController.class)
                    .getComputeLogs(
                            replicate.getChainTaskId(),
                            replicate.getWalletAddress(),
                            ""))
                    .withRel("logs")//useless, but helps understandability
                    .getHref();
            replicateModel.setAppLogs(logs);
        }
        String self = linkTo(methodOn(TaskController.class)
                .getTaskReplicate(
                        replicate.getChainTaskId(),
                        replicate.getWalletAddress()))
                .withSelfRel().getHref();
        replicateModel.setSelf(self);
        return replicateModel;
    }

    /**
     * @deprecated Use {@code /tasks/{chainTaskId}/logs} instead
     */
    @Deprecated(forRemoval = true)
    @GetMapping("/tasks/{chainTaskId}/stdout")
    public ResponseEntity<TaskLogsModel> getTaskLogsLegacy(
            @PathVariable("chainTaskId") String chainTaskId,
            @RequestHeader("Authorization") String authorization) {
        return getTaskLogs(chainTaskId, authorization);
    }

    @GetMapping("/tasks/{chainTaskId}/logs")
    public ResponseEntity<TaskLogsModel> getTaskLogs(
            @PathVariable("chainTaskId") String chainTaskId,
            @RequestHeader("Authorization") String authorization) {
        SignedChallenge signedChallenge = SignedChallenge.createFromString(authorization);
        if (signedChallenge == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String taskLogsRequester = signedChallenge.getWalletAddress();
        if (!isTaskRequester(taskLogsRequester, chainTaskId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Signature signature = new Signature(Numeric.cleanHexPrefix(signedChallenge.getChallengeSignature()));
        EIP712Challenge eip712Challenge = eip712ChallengeService.getChallenge(taskLogsRequester);
        if (!SignatureUtils.doesSignatureMatchesAddress(
                signature.getR(), signature.getS(), eip712Challenge.getHash(), taskLogsRequester)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return taskLogsService.getTaskLogs(chainTaskId)
                .map(TaskLogs::generateModel)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * @deprecated Use {@code /tasks/{chainTaskId}/replicates/{walletAddress}/logs} instead
     */
    @Deprecated(forRemoval = true)
    @GetMapping("/tasks/{chainTaskId}/replicates/{walletAddress}/stdout")
    public ResponseEntity<ComputeLogs> getComputeLogsLegacy(
            @PathVariable("chainTaskId") String chainTaskId,
            @PathVariable("walletAddress") String walletAddress,
            @RequestHeader("Authorization") String authorization) {
        return getComputeLogs(chainTaskId, walletAddress, authorization);
    }

    @GetMapping("/tasks/{chainTaskId}/replicates/{walletAddress}/logs")
    public ResponseEntity<ComputeLogs> getComputeLogs(
            @PathVariable("chainTaskId") String chainTaskId,
            @PathVariable("walletAddress") String walletAddress,
            @RequestHeader("Authorization") String authorization) {
        SignedChallenge signedChallenge = SignedChallenge.createFromString(authorization);
        if (signedChallenge == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String computeLogsRequester = signedChallenge.getWalletAddress();
        if (!isTaskRequester(computeLogsRequester, chainTaskId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Signature signature = new Signature(Numeric.cleanHexPrefix(signedChallenge.getChallengeSignature()));
        EIP712Challenge eip712Challenge = eip712ChallengeService.getChallenge(computeLogsRequester);
        if (!SignatureUtils.doesSignatureMatchesAddress(
                signature.getR(), signature.getS(), eip712Challenge.getHash(), computeLogsRequester)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return taskLogsService.getComputeLogs(chainTaskId, walletAddress)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Checks if requester address from bearer token is the same as the address used to buy the task execution.
     *
     * @param logsRequester Wallet address of requester asking task or compute logs
     * @param chainTaskId   Task for which outputs are requested
     * @return true if the user requesting computation outputs was the one to buy the task, false otherwise
     */
    private boolean isTaskRequester(String logsRequester, String chainTaskId) {
        TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        return taskDescription != null && logsRequester.equalsIgnoreCase(taskDescription.getRequester());
    }

}
