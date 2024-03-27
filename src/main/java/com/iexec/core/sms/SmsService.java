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

package com.iexec.core.sms;

import com.iexec.commons.poco.tee.TeeFramework;
import com.iexec.commons.poco.tee.TeeUtils;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.core.chain.SignatureService;
import com.iexec.core.registry.PlatformRegistryConfiguration;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientProvider;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class SmsService {
    private final PlatformRegistryConfiguration registryConfiguration;
    private final SignatureService signatureService;
    private final SmsClientProvider smsClientProvider;

    public SmsService(PlatformRegistryConfiguration registryConfiguration,
                      SignatureService signatureService,
                      SmsClientProvider smsClientProvider) {
        this.registryConfiguration = registryConfiguration;
        this.signatureService = signatureService;
        this.smsClientProvider = smsClientProvider;
    }

    /**
     * Checks the following conditions:
     * <ul>
     *     <li>Given deal exists on-chain;</li>
     *     <li>The {@link SmsClient} can be created, based on the on-chain deal definition;</li>
     *     <li>The targeted SMS is configured to run with the task's TEE framework.</li>
     * </ul>
     * <p>
     * If any of these conditions is wrong, then the {@link SmsClient} is considered to be not-ready.
     *
     * @param chainTaskId ID of the on-chain task.
     * @param tag         Tag of the deal.
     * @return SMS url if TEE types of tag &amp; SMS match.
     */
    public Optional<String> getVerifiedSmsUrl(String chainTaskId, String tag) {
        final TeeFramework teeFrameworkForDeal = TeeUtils.getTeeFramework(tag);
        if (teeFrameworkForDeal == null) {
            log.error("Can't get verified SMS url with invalid TEE framework " +
                    "from tag [chainTaskId:{}]", chainTaskId);
            return Optional.empty();
        }
        Optional<String> smsUrl = retrieveSmsUrl(teeFrameworkForDeal);
        if (smsUrl.isEmpty()) {
            log.error("Can't get verified SMS url since type of tag is not " +
                            "supported [chainTaskId:{},teeFrameworkForDeal:{}]",
                    chainTaskId, teeFrameworkForDeal);
            return Optional.empty();
        }
        final SmsClient smsClient = smsClientProvider.getSmsClient(smsUrl.get());
        if (!checkSmsTeeFramework(smsClient, teeFrameworkForDeal, chainTaskId)) {
            log.error("Can't get verified SMS url since tag TEE type " +
                            "does not match SMS TEE type [chainTaskId:{},teeFrameworkForDeal:{}]",
                    chainTaskId, teeFrameworkForDeal);
            return Optional.empty();
        }
        return smsUrl;
    }

    private Optional<String> retrieveSmsUrl(TeeFramework teeFramework) {
        Optional<String> smsUrl = Optional.empty();
        if (teeFramework == TeeFramework.SCONE) {
            smsUrl = Optional.of(registryConfiguration.getSconeSms());
        } else if (teeFramework == TeeFramework.GRAMINE) {
            smsUrl = Optional.of(registryConfiguration.getGramineSms());
        }
        return smsUrl;
    }

    private boolean checkSmsTeeFramework(SmsClient smsClient,
                                         TeeFramework teeFrameworkForDeal,
                                         String chainTaskId) {
        final TeeFramework smsTeeFramework;
        try {
            smsTeeFramework = smsClient.getTeeFramework();
        } catch (FeignException e) {
            log.error("Can't retrieve SMS TEE framework [chainTaskId:{}]",
                    chainTaskId, e);
            return false;
        }

        if (smsTeeFramework != teeFrameworkForDeal) {
            log.error("SMS is configured for another TEE framework " +
                            "[chainTaskId:{}, teeFrameworkForDeal:{}, smsTeeFramework:{}]",
                    chainTaskId, teeFrameworkForDeal, smsTeeFramework);
            return false;
        }
        return true;
    }

    public Optional<String> getEnclaveChallenge(String chainTaskId, String smsUrl) {
        return StringUtils.isEmpty(smsUrl)
                ? Optional.of(BytesUtils.EMPTY_ADDRESS)
                : generateEnclaveChallenge(chainTaskId, smsUrl);
    }

    Optional<String> generateEnclaveChallenge(String chainTaskId, String smsUrl) {
        // SMS client should already have been created once before.
        // If it couldn't be created, then the task would have been aborted.
        // So the following won't throw an exception.
        final SmsClient smsClient = smsClientProvider.getSmsClient(smsUrl);

        try {
            final String teeChallengePublicKey = smsClient.generateTeeChallenge(
                    signatureService.createAuthorization("", chainTaskId, "").getSignature().getValue(),
                    chainTaskId);

            if (StringUtils.isEmpty(teeChallengePublicKey)) {
                log.error("An error occurred while getting teeChallengePublicKey [chainTaskId:{}, smsUrl:{}]",
                        chainTaskId, smsUrl);
                return Optional.empty();
            }

            return Optional.of(teeChallengePublicKey);
        } catch (FeignException e) {
            log.error("Failed to get enclaveChallenge from SMS: unexpected return code [chainTaskId:{}, smsUrl:{}, statusCode:{}]",
                    chainTaskId, smsUrl, e.status(), e);
        } catch (RuntimeException e) {
            log.error("Failed to get enclaveChallenge from SMS: unexpected exception [chainTaskId:{}, smsUrl:{}]",
                    chainTaskId, smsUrl, e);
        }
        return Optional.empty();
    }
}
