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

package com.iexec.core.sms;

import com.iexec.common.tee.TeeEnclaveProvider;
import com.iexec.common.tee.TeeUtils;
import com.iexec.common.utils.BytesUtils;
import com.iexec.core.registry.PlatformRegistryConfiguration;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientProvider;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Slf4j
@Service
public class SmsService {
    private final PlatformRegistryConfiguration registryConfiguration;
    private final SmsClientProvider smsClientProvider;

    public SmsService(PlatformRegistryConfiguration registryConfiguration,
        SmsClientProvider smsClientProvider) {
        this.registryConfiguration = registryConfiguration;
        this.smsClientProvider = smsClientProvider;
    }

    /**
     * Checks the following conditions:
     * <ul>
     *     <li>Given deal exists on-chain;</li>
     *     <li>The {@link SmsClient} can be created, based on the on-chain deal definition;</li>
     *     <li>The targeted SMS is configured to run with the task's TEE enclave provider.</li>
     * </ul>
     * <p>
     * If any of these conditions is wrong, then the {@link SmsClient} is considered to be not-ready.
     *
     * @param chainTaskId ID of the on-chain task.
     * @param tag Tag of the deal.
     * @return SMS url if TEE types of tag & SMS match.
     */
    public Optional<String> getVerifiedSmsUrl(String chainTaskId, String tag) {
        final TeeEnclaveProvider teeEnclaveProviderForDeal = TeeUtils.getTeeEnclaveProvider(tag);
        if(teeEnclaveProviderForDeal == null){
            log.error("Can't get verified SMS url with invalid TEE enclave " + 
                "provider from tag [chainTaskId:{}]", chainTaskId);
            return Optional.empty();
        }
        Optional<String> smsUrl = retrieveSmsUrl(teeEnclaveProviderForDeal);
        if(smsUrl.isEmpty()){
            log.error("Can't get verified SMS url since type of tag is not " + 
                "supported [chainTaskId:{},teeEnclaveProvider:{}]", 
                    chainTaskId, teeEnclaveProviderForDeal);
            return Optional.empty();
        }
        final SmsClient smsClient = smsClientProvider.getSmsClient(smsUrl.get());
        if(!checkSmsTeeEnclaveProvider(smsClient, teeEnclaveProviderForDeal, chainTaskId)){
            log.error("Can't get verified SMS url since tag TEE type " + 
                "does not match SMS TEE type [chainTaskId:{},teeProviderForTask:{}]", 
                    chainTaskId, teeEnclaveProviderForDeal);
            return Optional.empty();
        }
        return smsUrl;
    }

    private Optional<String> retrieveSmsUrl(TeeEnclaveProvider teeEnclaveProvider) {
        Optional<String> smsUrl = Optional.empty();
        if(TeeEnclaveProvider.SCONE.equals(teeEnclaveProvider)){
            smsUrl = Optional.of(registryConfiguration.getSconeSms());
        } else if(TeeEnclaveProvider.GRAMINE.equals(teeEnclaveProvider)){
            smsUrl = Optional.of(registryConfiguration.getGramineSms());
        }
        return smsUrl;
    }

    private boolean checkSmsTeeEnclaveProvider(SmsClient smsClient,
                                               TeeEnclaveProvider teeEnclaveProviderForDeal,
                                               String chainTaskId) {
        final TeeEnclaveProvider smsTeeEnclaveProvider;
        try {
            smsTeeEnclaveProvider = smsClient.getTeeEnclaveProvider();
        } catch (FeignException e) {
            log.error("Can't retrieve SMS TEE enclave provider [chainTaskId:{}]",
                    chainTaskId, e);
            return false;
        }

        if (smsTeeEnclaveProvider != teeEnclaveProviderForDeal) {
            log.error("SMS is configured for another TEE enclave provider " +
                            "[chainTaskId:{}, teeEnclaveProviderForDeal:{}, smsTeeEnclaveProvider:{}]",
                    chainTaskId, teeEnclaveProviderForDeal, smsTeeEnclaveProvider);
            return false;
        }
        return true;
    }

    public Optional<String> getEnclaveChallenge(String chainTaskId, String smsUrl) {
        return StringUtils.isEmpty(smsUrl)
                ? Optional.of(BytesUtils.EMPTY_ADDRESS)
                : generateEnclaveChallenge(chainTaskId, smsUrl);
    }

    @Retryable(value = FeignException.class)
    Optional<String> generateEnclaveChallenge(String chainTaskId, String smsUrl) {
        // SMS client should already have been created once before.
        // If it couldn't be created, then the task would have been aborted.
        // So the following won't throw an exception.
        final SmsClient smsClient = smsClientProvider.getSmsClient(smsUrl);

        final String teeChallengePublicKey = smsClient.generateTeeChallenge(chainTaskId);

        if (StringUtils.isEmpty(teeChallengePublicKey)) {
            log.error("An error occurred while getting teeChallengePublicKey " 
                + "[chainTaskId:{}, smsUrl:{}]", chainTaskId, smsUrl);
            return Optional.empty();
        }

        return Optional.of(teeChallengePublicKey);
    }

    @Recover
    Optional<String> generateEnclaveChallenge(FeignException e, String chainTaskId, String smsUrl) {
        log.error("Failed to get enclaveChallenge from SMS even after retrying [chainTaskId:{}, attempts:3]", chainTaskId, e);
        return Optional.empty();
    }
}
