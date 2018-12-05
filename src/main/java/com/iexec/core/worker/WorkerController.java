package com.iexec.core.worker;


import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerConfigurationModel;
import com.iexec.common.security.Authorization;
import com.iexec.common.utils.BytesUtils;
import com.iexec.core.chain.ChainConfig;
import com.iexec.core.chain.CredentialsService;
import com.iexec.core.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;

import java.math.BigInteger;
import java.util.Date;
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

    public WorkerController(WorkerService workerService,
                            ChainConfig chainConfig,
                            CredentialsService credentialsService,
                            JwtTokenProvider jwtTokenProvider) {
        this.workerService = workerService;
        this.chainConfig = chainConfig;
        this.credentialsService = credentialsService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @RequestMapping(method = RequestMethod.POST, path = "/workers/ping")
    public ResponseEntity ping(@RequestParam(name = "walletAddress") String walletAddress) {
        Optional<Worker> optional = workerService.updateLastAlive(walletAddress);
        return optional.
                <ResponseEntity>map(ResponseEntity::ok)
                .orElseGet(() -> status(HttpStatus.NO_CONTENT).build());
    }

    @RequestMapping(method = RequestMethod.POST, path = "/workers/login")
    public ResponseEntity getToken(@RequestParam(name = "walletAddress") String walletAddress,
                                   @RequestBody Authorization authorization) {

        String token = "";
        byte[] hashTocheck = Hash.sha3(BytesUtils.stringToBytes(walletAddress));

        // check that the public address of the signer can be found
        for (int i = 0; i < 4; i++) {
            BigInteger publicKey = Sign.recoverFromSignature((byte) i,
                    new ECDSASignature(
                            new BigInteger(1, authorization.getSignR()),
                            new BigInteger(1, authorization.getSignS())),
                    hashTocheck);

            if (publicKey != null) {
                String addressRecovered = "0x" + Keys.getAddress(publicKey);

                if (addressRecovered.equals(walletAddress)) {
                    token = jwtTokenProvider.createToken(walletAddress);
                }
            }
        }

        return token.isEmpty() ? new ResponseEntity(HttpStatus.UNAUTHORIZED): ok(token);
    }

    @RequestMapping(method = RequestMethod.POST, path = "/workers/register")
    public ResponseEntity registerWorker(@RequestBody WorkerConfigurationModel model) {

        Worker worker = Worker.builder()
                .name(model.getName())
                .walletAddress(model.getWalletAddress())
                .os(model.getOs())
                .cpu(model.getCpu())
                .cpuNb(model.getCpuNb())
                .lastAliveDate(new Date())
                .build();

        Worker savedWorker = workerService.addWorker(worker);
        log.info("Worker has been registered [worker:{}]", savedWorker);
        return ok(savedWorker);
    }

    @RequestMapping(method = RequestMethod.GET, path = "/workers/config")
    public ResponseEntity getPublicConfiguration() {
        PublicConfiguration config = PublicConfiguration.builder()
                .blockchainURL(chainConfig.getPublicChainAddress())
                .iexecHubAddress(chainConfig.getHubAddress())
                .workerPoolAddress(chainConfig.getPoolAddress())
                .schedulerPublicAddress(credentialsService.getCredentials().getAddress())
                .build();

        return ok(config);
    }


}
