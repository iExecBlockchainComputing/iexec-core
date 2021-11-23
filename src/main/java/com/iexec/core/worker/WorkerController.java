/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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


import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerModel;
import com.iexec.common.security.Signature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.core.chain.ChainConfig;
import com.iexec.core.chain.CredentialsService;
import com.iexec.core.chain.adapter.BlockchainAdapterClientConfig;
import com.iexec.core.configuration.*;
import com.iexec.core.security.ChallengeService;
import com.iexec.core.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.web3j.crypto.Hash;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

@Slf4j
@RestController
public class WorkerController {

    private final WorkerService workerService;
    private final ChainConfig chainConfig;
    private final CredentialsService credentialsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final ChallengeService challengeService;
    private final WorkerConfiguration workerConfiguration;
    private final ResultRepositoryConfiguration resultRepoConfig;
    private final SmsConfiguration smsConfiguration;
    private final BlockchainAdapterClientConfig blockchainAdapterClientConfig;

    public WorkerController(WorkerService workerService,
                            ChainConfig chainConfig,
                            CredentialsService credentialsService,
                            JwtTokenProvider jwtTokenProvider,
                            ChallengeService challengeService,
                            WorkerConfiguration workerConfiguration,
                            ResultRepositoryConfiguration resultRepoConfig,
                            SmsConfiguration smsConfiguration,
                            BlockchainAdapterClientConfig blockchainAdapterClientConfig) {
        this.workerService = workerService;
        this.chainConfig = chainConfig;
        this.credentialsService = credentialsService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.challengeService = challengeService;
        this.workerConfiguration = workerConfiguration;
        this.resultRepoConfig = resultRepoConfig;
        this.smsConfiguration = smsConfiguration;
        this.blockchainAdapterClientConfig = blockchainAdapterClientConfig;
    }

    @PostMapping(path = "/workers/ping")
    public ResponseEntity<String> ping(@RequestHeader("Authorization") String bearerToken) {
        String workerWalletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        if (workerWalletAddress.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }
        return workerService.updateLastAlive(workerWalletAddress)
                .<ResponseEntity<String>>map(worker -> ok(SessionService.getSessionId()))
                .orElseGet(() -> status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping(path = "/workers/challenge")
    public ResponseEntity<String> getChallenge(@RequestParam(name = "walletAddress") String walletAddress) {
        if (!workerService.isAllowedToJoin(walletAddress)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }
        return ok(challengeService.getChallenge(walletAddress));
    }

    @PostMapping(path = "/workers/login")
    public ResponseEntity<String> getToken(@RequestParam(name = "walletAddress") String walletAddress,
                                   @RequestBody Signature signature) {

        if (!workerService.isAllowedToJoin(walletAddress)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String challenge = challengeService.getChallenge(walletAddress);
        byte[] hashTocheck = Hash.sha3(BytesUtils.stringToBytes(challenge));

        if (SignatureUtils.doesSignatureMatchesAddress(signature.getR(), signature.getS(),
                BytesUtils.bytesToString(hashTocheck), walletAddress)) {
            String token = jwtTokenProvider.createToken(walletAddress);
            return ok(token);
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping(path = "/workers/register")
    public ResponseEntity<Worker> registerWorker(@RequestHeader("Authorization") String bearerToken,
                                         @RequestBody WorkerModel model) {
        String workerWalletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);

        if (workerWalletAddress.isEmpty()){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }

        // if it is a GPU worker, it can process only 1 task at a time, otherwise it can process cpuNb
        int maxNbTasks = model.isGpuEnabled() ? 1 : model.getCpuNb();

        Worker worker = Worker.builder()
                .name(model.getName())
                .walletAddress(workerWalletAddress)
                .os(model.getOs())
                .cpu(model.getCpu())
                .cpuNb(model.getCpuNb())
                .maxNbTasks(maxNbTasks)
                .memorySize(model.getMemorySize())
                .teeEnabled(model.isTeeEnabled())
                .gpuEnabled(model.isGpuEnabled())
                .lastAliveDate(new Date())
                .participatingChainTaskIds(new ArrayList<>())
                .computingChainTaskIds(new ArrayList<>())
                .build();

        Worker savedWorker = workerService.addWorker(worker);
        log.info("Worker ready [worker:{}]", savedWorker);
        return ok(savedWorker);
    }

    @GetMapping(path = "/workers/config")
    public ResponseEntity<PublicConfiguration> getPublicConfiguration() {
        PublicConfiguration config = PublicConfiguration.builder()
                .workerPoolAddress(chainConfig.getPoolAddress())
                .blockchainAdapterUrl(blockchainAdapterClientConfig.getUrl())
                .schedulerPublicAddress(credentialsService.getCredentials().getAddress())
                .resultRepositoryURL(resultRepoConfig.getResultRepositoryURL())
                .smsURL(smsConfiguration.getSmsURL())
                .askForReplicatePeriod(workerConfiguration.getAskForReplicatePeriod())
                .requiredWorkerVersion(workerConfiguration.getRequiredWorkerVersion())
                .build();

        return ok(config);
    }


    @GetMapping(path = "/workers/computing")
    public ResponseEntity<List<String>> getComputingTasks(@RequestHeader("Authorization") String bearerToken) {
        String workerWalletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        if (workerWalletAddress.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }
        return ok(workerService.getComputingTaskIds(workerWalletAddress));
    }

}
