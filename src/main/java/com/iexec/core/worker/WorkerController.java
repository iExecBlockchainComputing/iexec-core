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

package com.iexec.core.worker;

import com.iexec.commons.poco.security.Signature;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.commons.poco.utils.SignatureUtils;
import com.iexec.core.config.PublicConfiguration;
import com.iexec.core.config.WorkerModel;
import com.iexec.core.configuration.PublicConfigurationService;
import com.iexec.core.security.ChallengeService;
import com.iexec.core.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.web3j.crypto.Hash;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.ResponseEntity.ok;

@Slf4j
@RestController
public class WorkerController {

    private final WorkerService workerService;
    private final JwtTokenProvider jwtTokenProvider;
    private final ChallengeService challengeService;
    private final PublicConfigurationService publicConfigurationService;

    public WorkerController(WorkerService workerService,
                            JwtTokenProvider jwtTokenProvider,
                            ChallengeService challengeService,
                            PublicConfigurationService publicConfigurationService) {
        this.workerService = workerService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.challengeService = challengeService;
        this.publicConfigurationService = publicConfigurationService;
    }

    @PostMapping(path = "/workers/ping")
    public ResponseEntity<String> ping(@RequestHeader("Authorization") String bearerToken) {
        String workerWalletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        if (workerWalletAddress.isEmpty()) {
            WorkerUtils.emitWarnOnUnAuthorizedAccess("");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.debug("Worker keepalive ping [workerAddress:{}]", workerWalletAddress);
        final String publicConfigurationHash = publicConfigurationService.getPublicConfigurationHash();
        workerService.updateLastAlive(workerWalletAddress);
        return ok(publicConfigurationHash);
    }

    @GetMapping(path = "/workers/challenge")
    public ResponseEntity<String> getChallenge(@RequestParam(name = "walletAddress") String walletAddress) {
        if (!workerService.isAllowedToJoin(walletAddress)) {
            WorkerUtils.emitWarnOnUnAuthorizedAccess(walletAddress);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.debug("Worker challenge request [workerAddress:{}]", walletAddress);
        return ok(challengeService.getChallenge(walletAddress));
    }

    @PostMapping(path = "/workers/login")
    public ResponseEntity<String> getToken(@RequestParam(name = "walletAddress") String walletAddress,
                                           @RequestBody Signature signature) {
        if (!workerService.isAllowedToJoin(walletAddress)) {
            WorkerUtils.emitWarnOnUnAuthorizedAccess(walletAddress);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.debug("Worker login attempt [workerAddress:{}]", walletAddress);
        String challenge = challengeService.getChallenge(walletAddress);
        byte[] hashToCheck = Hash.sha3(BytesUtils.stringToBytes(challenge));

        if (SignatureUtils.doesSignatureMatchesAddress(signature.getR(), signature.getS(),
                BytesUtils.bytesToString(hashToCheck), walletAddress)) {
            challengeService.removeChallenge(walletAddress, challenge);
            String token = jwtTokenProvider.getOrCreateToken(walletAddress);
            log.debug("Worker has successfully logged on [workerAddress:{}]", walletAddress);
            return ok(token);
        }

        log.debug("Worker has failed to log in [workerAddress:{}]", walletAddress);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping(path = "/workers/register")
    public ResponseEntity<Worker> registerWorker(@RequestHeader("Authorization") final String bearerToken,
                                                 @RequestBody final WorkerModel model) {
        final String workerWalletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        if (workerWalletAddress.isEmpty()) {
            WorkerUtils.emitWarnOnUnAuthorizedAccess("");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // if it is a GPU worker, it can process only 1 task at a time, otherwise it can process cpuNb
        final int maxNbTasks = model.isGpuEnabled() ? 1 : model.getCpuNb();

        final Worker worker = Worker.builder()
                .name(model.getName())
                .walletAddress(workerWalletAddress)
                .os(model.getOs())
                .cpu(model.getCpu())
                .cpuNb(model.getCpuNb())
                .maxNbTasks(maxNbTasks)
                .memorySize(model.getMemorySize())
                .gpuEnabled(model.isGpuEnabled())
                .teeEnabled(model.isTeeEnabled())
                .tdxEnabled(model.isTdxEnabled())
                .participatingChainTaskIds(new ArrayList<>())
                .computingChainTaskIds(new ArrayList<>())
                .build();

        final Worker savedWorker = workerService.addWorker(worker);
        log.info("Worker ready [worker:{}]", savedWorker);
        return ok(savedWorker);
    }

    @GetMapping(path = "/workers/config")
    public ResponseEntity<PublicConfiguration> getPublicConfiguration() {
        log.debug("Ask for the public configuration");
        final PublicConfiguration config = publicConfigurationService.getPublicConfiguration();
        return ok(config);
    }


    @GetMapping(path = "/workers/computing")
    public ResponseEntity<List<String>> getComputingTasks(@RequestHeader("Authorization") String bearerToken) {
        String workerWalletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        if (workerWalletAddress.isEmpty()) {
            WorkerUtils.emitWarnOnUnAuthorizedAccess("");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        log.debug("Worker requests for computing tasks ids [workerAddress:{}]", workerWalletAddress);
        return ok(workerService.getComputingTaskIds(workerWalletAddress));
    }
}
