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

package com.iexec.core.configuration;

import com.iexec.common.config.PublicConfiguration;
import com.iexec.core.chain.ChainConfig;
import com.iexec.core.chain.CredentialsService;
import com.iexec.core.chain.adapter.BlockchainAdapterClientConfig;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;

import javax.annotation.PostConstruct;

/**
 * This simple service will generate a random session id when the scheduler is started, it will be send to workers when
 * they ping the scheduler. If they see that the session id has changed, it means that the scheduler has restarted.
 */
@Service
public class PublicConfigurationService {
    private final ChainConfig chainConfig;
    private final CredentialsService credentialsService;
    private final WorkerConfiguration workerConfiguration;
    private final ResultRepositoryConfiguration resultRepoConfig;
    private final SmsConfiguration smsConfiguration;
    private final BlockchainAdapterClientConfig blockchainAdapterClientConfig;

    private PublicConfiguration publicConfiguration = null;
    /**
     * {@literal publicConfigurationHash} is a Base64-encoded hash
     * of all {@link PublicConfiguration} fields,
     * concatenated with {@literal \n}.
     */
    private String publicConfigurationHash = null;

    public PublicConfigurationService(ChainConfig chainConfig,
                                      CredentialsService credentialsService,
                                      WorkerConfiguration workerConfiguration,
                                      ResultRepositoryConfiguration resultRepoConfig,
                                      SmsConfiguration smsConfiguration,
                                      BlockchainAdapterClientConfig blockchainAdapterClientConfig) {
        this.chainConfig = chainConfig;
        this.credentialsService = credentialsService;
        this.workerConfiguration = workerConfiguration;
        this.resultRepoConfig = resultRepoConfig;
        this.smsConfiguration = smsConfiguration;
        this.blockchainAdapterClientConfig = blockchainAdapterClientConfig;
    }

    @PostConstruct
    void buildPublicConfiguration() throws NoSuchAlgorithmException {
        this.publicConfiguration = PublicConfiguration.builder()
                .workerPoolAddress(chainConfig.getPoolAddress())
                .blockchainAdapterUrl(blockchainAdapterClientConfig.getUrl())
                .schedulerPublicAddress(credentialsService.getCredentials().getAddress())
                .resultRepositoryURL(resultRepoConfig.getResultRepositoryURL())
                .smsURL(smsConfiguration.getSmsURL())
                .askForReplicatePeriod(workerConfiguration.getAskForReplicatePeriod())
                .requiredWorkerVersion(workerConfiguration.getRequiredWorkerVersion())
                .build();

        // TODO: would be great to put this in Common
        // (a simple `@ToString` would be sufficient)
        final String publicConfigurationAsString = String.join("\n",
                publicConfiguration.getWorkerPoolAddress(),
                publicConfiguration.getBlockchainAdapterUrl(),
                publicConfiguration.getSchedulerPublicAddress(),
                publicConfiguration.getResultRepositoryURL(),
                publicConfiguration.getSmsURL(),
                publicConfiguration.getAskForReplicatePeriod() + "",
                publicConfiguration.getRequiredWorkerVersion()
        );

        this.publicConfigurationHash = Hash.sha3String(publicConfigurationAsString);
    }

    public String getPublicConfigurationHash() {
        if (publicConfigurationHash == null) {
            throw new IllegalArgumentException("Public configuration accessed before initialization.");
        }
        return publicConfigurationHash;
    }

    public PublicConfiguration getPublicConfiguration() {
        if (publicConfiguration == null) {
            throw new IllegalArgumentException("Public configuration accessed before initialization.");
        }
        return publicConfiguration;
    }
}
