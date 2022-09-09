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

import com.iexec.common.chain.ChainDeal;
import com.iexec.common.chain.IexecHubAbstractService;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.tee.TeeEnclaveProvider;
import com.iexec.common.tee.TeeUtils;
import com.iexec.common.utils.BytesUtils;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientCreationException;
import com.iexec.sms.api.SmsClientProvider;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Slf4j
@Service
public class SmsService {
    private final SmsClientProvider smsClientProvider;
    private final IexecHubAbstractService iexecHubService;

    public SmsService(SmsClientProvider smsClientProvider, IexecHubAbstractService iexecHubService) {
        this.smsClientProvider = smsClientProvider;
        this.iexecHubService = iexecHubService;
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
     * @param chainDealId ID of the on-chain deal related to the task to execute.
     * @param chainTaskId ID of the on-chain task.
     * @return {@literal true} if previous conditions are met, {@literal false} otherwise.
     */
    public boolean isSmsClientReady(String chainDealId, String chainTaskId) {
        try {
            final Optional<ChainDeal> chainDeal = iexecHubService.getChainDeal(chainDealId);
            if (chainDeal.isEmpty()) {
                log.error("No chain deal for given ID [chainDealId: {}]", chainDealId);
                return false;
            }
            final SmsClient smsClient = smsClientProvider.getOrCreateSmsClientForUninitializedTask(chainDeal.get(), chainTaskId);
            final TeeEnclaveProvider teeEnclaveProviderForDeal = TeeUtils.getTeeEnclaveProvider(chainDeal.get().getTag());
            return checkSmsTeeEnclaveProvider(smsClient, teeEnclaveProviderForDeal, chainTaskId);
        } catch (SmsClientCreationException e) {
            log.error("SmsClient is not ready [chainTaskId: {}]", chainTaskId, e);
            return false;
        }
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

    public Optional<String> getEnclaveChallenge(String chainTaskId, boolean isTeeEnabled) {
        return isTeeEnabled
                ? generateEnclaveChallenge(chainTaskId)
                : Optional.of(BytesUtils.EMPTY_ADDRESS);
    }

    @Retryable(value = FeignException.class)
    Optional<String> generateEnclaveChallenge(String chainTaskId) {
        final TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);

        // SMS client should already have been created once before.
        // If it couldn't be created, then the task would have been aborted.
        // So the following won't throw an exception.
        final SmsClient smsClient = smsClientProvider.getOrCreateSmsClientForTask(taskDescription);

        final String teeChallengePublicKey = smsClient.generateTeeChallenge(chainTaskId);

        if (teeChallengePublicKey == null || teeChallengePublicKey.isEmpty()) {
            log.error("An error occurred while getting teeChallengePublicKey [chainTaskId:{}]", chainTaskId);
            return Optional.empty();
        }

        return Optional.of(teeChallengePublicKey);
    }

    @Recover
    Optional<String> generateEnclaveChallenge(FeignException e, String chainTaskId) {
        log.error("Failed to get enclaveChallenge from SMS even after retrying [chainTaskId:{}, attempts:3]", chainTaskId, e);
        return Optional.empty();
    }
}
