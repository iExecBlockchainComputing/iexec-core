package com.iexec.core.worker;


import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerModel;
import com.iexec.common.security.Signature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.core.chain.ChainConfig;
import com.iexec.core.chain.CredentialsService;
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
import java.util.Optional;

import static org.springframework.http.ResponseEntity.ok;
import static org.springframework.http.ResponseEntity.status;

@Slf4j
@RestController
public class WorkerController {

    private WorkerService workerService;
    private ChainConfig chainConfig;
    private CredentialsService credentialsService;
    private JwtTokenProvider jwtTokenProvider;
    private ChallengeService challengeService;
    private WorkerConfiguration workerConfiguration;
    private ResultRepositoryConfiguration resultRepoConfig;
    private SmsConfiguration smsConfiguration;
    private SconeCasConfiguration sconeCasConfiguration;

    public WorkerController(WorkerService workerService,
                            ChainConfig chainConfig,
                            CredentialsService credentialsService,
                            JwtTokenProvider jwtTokenProvider,
                            ChallengeService challengeService,
                            WorkerConfiguration workerConfiguration,
                            ResultRepositoryConfiguration resultRepoConfig,
                            SmsConfiguration smsConfiguration,
                            SconeCasConfiguration sconeCasConfiguration) {
        this.workerService = workerService;
        this.chainConfig = chainConfig;
        this.credentialsService = credentialsService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.challengeService = challengeService;
        this.workerConfiguration = workerConfiguration;
        this.resultRepoConfig = resultRepoConfig;
        this.smsConfiguration = smsConfiguration;
        this.sconeCasConfiguration = sconeCasConfiguration;
    }

    @PostMapping(path = "/workers/ping")
    public ResponseEntity ping(@RequestHeader("Authorization") String bearerToken) {
        String workerWalletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        if (workerWalletAddress.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }


        Optional<Worker> optional = workerService.updateLastAlive(workerWalletAddress);

        return optional.
                <ResponseEntity>map(worker -> ok(SessionService.getSessionId()))
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    @GetMapping(path = "/workers/challenge")
    public ResponseEntity getChallenge(@RequestParam(name = "walletAddress") String walletAddress) {
        if (!workerService.isAllowedToJoin(walletAddress)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }
        return ok(challengeService.getChallenge(walletAddress));
    }

    @PostMapping(path = "/workers/login")
    public ResponseEntity getToken(@RequestParam(name = "walletAddress") String walletAddress,
                                   @RequestBody Signature signature) {

        if (!workerService.isAllowedToJoin(walletAddress)) {
            return new ResponseEntity(HttpStatus.UNAUTHORIZED);
        }

        String challenge = challengeService.getChallenge(walletAddress);
        byte[] hashTocheck = Hash.sha3(BytesUtils.stringToBytes(challenge));

        if (SignatureUtils.doesSignatureMatchesAddress(signature.getR(), signature.getS(),
                BytesUtils.bytesToString(hashTocheck), walletAddress)) {
            String token = jwtTokenProvider.createToken(walletAddress);
            return ok(token);
        }

        return new ResponseEntity(HttpStatus.UNAUTHORIZED);
    }

    @PostMapping(path = "/workers/register")
    public ResponseEntity registerWorker(@RequestHeader("Authorization") String bearerToken,
                                         @RequestBody WorkerModel model) {
        String workerWalletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);

        if (workerWalletAddress.isEmpty()){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }

        Worker worker = Worker.builder()
                .name(model.getName())
                .walletAddress(workerWalletAddress)
                .os(model.getOs())
                .cpu(model.getCpu())
                .cpuNb(model.getCpuNb())
                .memorySize(model.getMemorySize())
                .teeEnabled(model.isTeeEnabled())
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
                .chainId(chainConfig.getChainId())
                .blockchainURL(chainConfig.getPublicChainAddress())
                .iexecHubAddress(chainConfig.getHubAddress())
                .workerPoolAddress(chainConfig.getPoolAddress())
                .schedulerPublicAddress(credentialsService.getCredentials().getAddress())
                .resultRepositoryURL(resultRepoConfig.getResultRepositoryURL())
                .smsURL(smsConfiguration.getSmsURL())
                .sconeCasURL(sconeCasConfiguration.getURL())
                .askForReplicatePeriod(workerConfiguration.getAskForReplicatePeriod())
                .requiredWorkerVersion(workerConfiguration.getRequiredWorkerVersion())
                .build();

        return ok(config);
    }


    @GetMapping(path = "/workers/currenttasks")
    public ResponseEntity<List<String>> getTasksInProgress(@RequestHeader("Authorization") String bearerToken) {
        String workerWalletAddress = jwtTokenProvider.getWalletAddressFromBearerToken(bearerToken);
        if (workerWalletAddress.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED.value()).build();
        }
        return ok(workerService.getChainTaskIds(workerWalletAddress));
    }

}
