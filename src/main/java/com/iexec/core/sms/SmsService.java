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

import com.iexec.common.utils.BytesUtils;
import com.iexec.sms.api.SmsClient;
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

    public SmsService(SmsClientProvider smsClientProvider) {
        this.smsClientProvider = smsClientProvider;
    }

    public Optional<String> getEnclaveChallenge(String chainTaskId, boolean isTeeEnabled) {
        return isTeeEnabled
                ? generateEnclaveChallenge(chainTaskId)
                : Optional.of(BytesUtils.EMPTY_ADDRESS);
    }

    @Retryable(value = FeignException.class)
    Optional<String> generateEnclaveChallenge(String chainTaskId) {
        final Optional<SmsClient> oSmsClient = smsClientProvider.getSmsClientForTask(chainTaskId);
        if (oSmsClient.isEmpty()) {
            log.warn("No SMS related to given task [chainTaskId: {}]", chainTaskId);
            return Optional.empty();
        }
        final SmsClient smsClient = oSmsClient.get();

        String teeChallengePublicKey = smsClient.generateTeeChallenge(chainTaskId);

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
