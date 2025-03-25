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

package com.iexec.core.sms;

import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.tee.TeeFramework;
import com.iexec.commons.poco.tee.TeeUtils;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.commons.poco.utils.HashUtils;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.SignatureService;
import com.iexec.core.configuration.ResultRepositoryConfiguration;
import com.iexec.core.registry.PlatformRegistryConfiguration;
import com.iexec.core.task.event.TaskInitializedEvent;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientProvider;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;

import java.util.Optional;

import static com.iexec.sms.secret.ReservedSecretKeyName.IEXEC_RESULT_IEXEC_RESULT_PROXY_URL;

@Slf4j
@Service
public class SmsService {
    private final IexecHubService iexecHubService;
    private final PlatformRegistryConfiguration registryConfiguration;
    private final ResultRepositoryConfiguration resultRepositoryConfiguration;
    private final SignatureService signatureService;
    private final SmsClientProvider smsClientProvider;

    public SmsService(final IexecHubService iexecHubService,
                      final PlatformRegistryConfiguration registryConfiguration,
                      final ResultRepositoryConfiguration resultRepositoryConfiguration,
                      final SignatureService signatureService,
                      final SmsClientProvider smsClientProvider) {
        this.iexecHubService = iexecHubService;
        this.registryConfiguration = registryConfiguration;
        this.resultRepositoryConfiguration = resultRepositoryConfiguration;
        this.signatureService = signatureService;
        this.smsClientProvider = smsClientProvider;
    }

    private Optional<String> getVerifiedSmsUrl(final String chainTaskId) {
        final TaskDescription taskDescription = iexecHubService.getTaskDescription(chainTaskId);
        return getVerifiedSmsUrl(chainTaskId, taskDescription.getTeeFramework());
    }

    public Optional<String> getVerifiedSmsUrl(final String chainTaskId, final String tag) {
        return getVerifiedSmsUrl(chainTaskId, TeeUtils.getTeeFramework(tag));
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
     * @param chainTaskId         ID of the on-chain task.
     * @param teeFrameworkForDeal Tag of the deal.
     * @return SMS url if TEE types of tag &amp; SMS match.
     */
    Optional<String> getVerifiedSmsUrl(final String chainTaskId, final TeeFramework teeFrameworkForDeal) {
        log.debug("getVerifiedSmsUrl [chainTaskId:{}, teeFrameworkForDeal:{}]", chainTaskId, teeFrameworkForDeal);
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

    /**
     * Checks if default Result Proxy URL is present in SMS.
     *
     * @param smsURL The SMS URL to check.
     * @return {@literal true} if the URL is present, {@literal false} otherwise.
     * @throws FeignException runtime exception if {@code isWeb2SecretSet} HTTP return code differs from 2XX or 404.
     */
    private boolean isWorkerpoolResultProxyUrlPresent(final String smsURL) {
        try {
            final SmsClient smsClient = smsClientProvider.getSmsClient(smsURL);
            smsClient.isWeb2SecretSet(signatureService.getAddress(), IEXEC_RESULT_IEXEC_RESULT_PROXY_URL);
            return true;
        } catch (FeignException.NotFound e) {
            log.info("Worker pool default Result Proxy URL does not exist in SMS");
        }
        return false;
    }

    /**
     * Pushes worker pool default Result Proxy URL to SMS.
     *
     * @param event The default result-proxy URL.
     */
    @EventListener
    public void pushWorkerpoolResultProxyUrl(final TaskInitializedEvent event) {
        log.debug("pushWorkerpoolResultProxyUrl [event:{}]", event);
        try {
            final String resultProxyURL = resultRepositoryConfiguration.getUrl();
            final String smsURL = getVerifiedSmsUrl(event.getChainTaskId()).orElseThrow();
            log.debug("Pushing result-proxy default URL to SMS [sms:{}, result-proxy:{}]", smsURL, resultProxyURL);
            final SmsClient smsClient = smsClientProvider.getSmsClient(smsURL);
            final String challenge = HashUtils.concatenateAndHash(
                    Hash.sha3String("IEXEC_SMS_DOMAIN"),
                    signatureService.getAddress(),
                    Hash.sha3String(IEXEC_RESULT_IEXEC_RESULT_PROXY_URL),
                    Hash.sha3String(resultProxyURL));
            final String authorization = signatureService.sign(challenge).getValue();
            if (isWorkerpoolResultProxyUrlPresent(smsURL)) {
                smsClient.updateWeb2Secret(authorization, signatureService.getAddress(), IEXEC_RESULT_IEXEC_RESULT_PROXY_URL, resultProxyURL);
            } else {
                smsClient.setWeb2Secret(authorization, signatureService.getAddress(), IEXEC_RESULT_IEXEC_RESULT_PROXY_URL, resultProxyURL);
            }
        } catch (Exception e) {
            log.error("Failed to push default result-proxy URL to SMS", e);
        }
    }
}
