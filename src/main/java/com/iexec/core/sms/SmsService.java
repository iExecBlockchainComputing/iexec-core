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
import com.iexec.core.feign.SmsClient;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import net.jodah.expiringmap.ExpiringMap;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.iexec.core.task.Task.LONGEST_TASK_TIMEOUT;


@Slf4j
@Service
public class SmsService {
    /**
     * Memoize enclave challenges as they are computed by the SMS.
     * They are constant over time, so we don't need to compute them everytime.
     */
    private final Map<String, String> enclaveChallenges = ExpiringMap.builder()
            .expiration(LONGEST_TASK_TIMEOUT.getSeconds(), TimeUnit.SECONDS)
            .build();

    private final SmsClient smsClient;

    public SmsService(SmsClient smsClient) {
        this.smsClient = smsClient;
    }

    public String getEnclaveChallenge(String chainTaskId, boolean isTeeEnabled) {
        if (!isTeeEnabled) {
            return BytesUtils.EMPTY_ADDRESS;
        }

        return enclaveChallenges.computeIfAbsent(chainTaskId, this::generateEnclaveChallenge);
    }

    @Retryable(value = FeignException.class)
    String generateEnclaveChallenge(String chainTaskId) {

        String teeChallengePublicKey = smsClient.generateTeeChallenge(chainTaskId);

        if (teeChallengePublicKey == null || teeChallengePublicKey.isEmpty()) {
            log.error("An error occured while getting teeChallengePublicKey [chainTaskId:{}]", chainTaskId);
            return "";
        }

        return teeChallengePublicKey;
    }

    @Recover
    String generateEnclaveChallenge(FeignException e, String chainTaskId) {
        log.error("Failed to get enclaveChallenge from SMS even after retrying [chainTaskId:{}, attempts:3]", chainTaskId);
        e.printStackTrace();
        return "";
    }
}