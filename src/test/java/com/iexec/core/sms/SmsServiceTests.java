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

import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.security.Signature;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.commons.poco.tee.TeeFramework;
import com.iexec.commons.poco.tee.TeeUtils;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.SignatureService;
import com.iexec.core.configuration.ResultRepositoryConfiguration;
import com.iexec.core.registry.PlatformRegistryConfiguration;
import com.iexec.core.task.event.TaskInitializedEvent;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientProvider;
import feign.FeignException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.Optional;

import static com.iexec.sms.secret.ReservedSecretKeyName.IEXEC_RESULT_IEXEC_RESULT_PROXY_URL;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ExtendWith(OutputCaptureExtension.class)
class SmsServiceTests {

    private static final String ADDRESS = "address";
    private static final String AUTHORIZATION = "authorization";
    private static final String RESULT_PROXY_URL = "http://result-proxy";
    private static final String GRAMINE_SMS_URL = "http://gramine-sms";
    private static final String SCONE_SMS_URL = "http://scone-sms";
    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final String URL = "url";

    @Mock
    private IexecHubService iexecHubService;
    @Mock
    private PlatformRegistryConfiguration registryConfiguration;
    @Mock
    private ResultRepositoryConfiguration resultRepositoryConfiguration;
    @Mock
    private SignatureService signatureService;
    @Mock
    private SmsClient smsClient;
    @Mock
    private SmsClientProvider smsClientProvider;

    @InjectMocks
    private SmsService smsService;

    @Test
    void shouldGetVerifiedGramineSmsUrl() {
        when(registryConfiguration.getGramineSms()).thenReturn(GRAMINE_SMS_URL);
        when(smsClientProvider.getSmsClient(GRAMINE_SMS_URL)).thenReturn(smsClient);
        when(smsClient.getTeeFramework()).thenReturn(TeeUtils.getTeeFramework(TeeUtils.TEE_GRAMINE_ONLY_TAG));

        Assertions.assertThat(smsService.getVerifiedSmsUrl(CHAIN_TASK_ID, TeeUtils.TEE_GRAMINE_ONLY_TAG))
                .isEqualTo(Optional.of(GRAMINE_SMS_URL));

        verify(smsClientProvider).getSmsClient(GRAMINE_SMS_URL);
        verify(smsClient).getTeeFramework();
    }

    @Test
    void shouldGetVerifiedSconeSmsUrl() {
        when(registryConfiguration.getSconeSms()).thenReturn(SCONE_SMS_URL);
        when(smsClientProvider.getSmsClient(SCONE_SMS_URL)).thenReturn(smsClient);
        when(smsClient.getTeeFramework()).thenReturn(TeeUtils.getTeeFramework(TeeUtils.TEE_SCONE_ONLY_TAG));

        Assertions.assertThat(smsService.getVerifiedSmsUrl(CHAIN_TASK_ID, TeeUtils.TEE_SCONE_ONLY_TAG))
                .isEqualTo(Optional.of(SCONE_SMS_URL));

        verify(smsClientProvider).getSmsClient(SCONE_SMS_URL);
        verify(smsClient).getTeeFramework();
    }

    @Test
    void shouldNotGetVerifiedSmsUrlSinceCannotGetEnclaveProviderFromTag() {
        Assertions.assertThat(smsService.getVerifiedSmsUrl(CHAIN_TASK_ID, "0xabc"))
                .isEmpty();

        verifyNoInteractions(smsClientProvider, smsClient);
    }

    @Test
    void shouldNotGetVerifiedSmsUrlSinceWrongTeeEnclaveProviderOnRemoteSms() {
        when(registryConfiguration.getGramineSms()).thenReturn(GRAMINE_SMS_URL);
        when(smsClientProvider.getSmsClient(GRAMINE_SMS_URL)).thenReturn(smsClient);
        when(smsClient.getTeeFramework()).thenReturn(TeeFramework.SCONE);

        Assertions.assertThat(smsService.getVerifiedSmsUrl(CHAIN_TASK_ID, TeeUtils.TEE_GRAMINE_ONLY_TAG))
                .isEmpty();

        verify(smsClientProvider).getSmsClient(GRAMINE_SMS_URL);
        verify(smsClient).getTeeFramework();
    }
    // endregion

    // region getEnclaveChallenge
    @Test
    void shouldGetEmptyAddressForStandardTask() {
        Assertions.assertThat(smsService.getEnclaveChallenge(CHAIN_TASK_ID, ""))
                .isEqualTo(Optional.of(BytesUtils.EMPTY_ADDRESS));

        verifyNoInteractions(smsClientProvider, smsClient);
    }

    @Test
    void shouldGetEnclaveChallengeForTeeTask() {
        final String expected = "challenge";
        initEnclaveChallengeStubs();
        when(smsClient.generateTeeChallenge(AUTHORIZATION, CHAIN_TASK_ID)).thenReturn(expected);

        Optional<String> received = smsService.getEnclaveChallenge(CHAIN_TASK_ID, URL);
        verify(smsClient).generateTeeChallenge(AUTHORIZATION, CHAIN_TASK_ID);
        Assertions.assertThat(received)
                .isEqualTo(Optional.of(expected));
    }

    @Test
    void shouldNotGetEnclaveChallengeForTeeTaskWhenEmptySmsResponse() {
        initEnclaveChallengeStubs();
        when(smsClient.generateTeeChallenge(AUTHORIZATION, CHAIN_TASK_ID)).thenReturn("");
        Optional<String> received = smsService.getEnclaveChallenge(CHAIN_TASK_ID, URL);
        verify(smsClient).generateTeeChallenge(AUTHORIZATION, CHAIN_TASK_ID);
        Assertions.assertThat(received).isEmpty();
    }
    // endregion

    // region generateEnclaveChallenge
    @Test
    void shouldGenerateEnclaveChallenge() {
        final String expected = "challenge";

        initEnclaveChallengeStubs();
        when(smsClient.generateTeeChallenge(AUTHORIZATION, CHAIN_TASK_ID)).thenReturn(expected);

        Optional<String> received = smsService.generateEnclaveChallenge(CHAIN_TASK_ID, URL);
        Assertions.assertThat(received)
                .contains(expected);
    }

    @Test
    void shouldNotGenerateEnclaveChallengeSinceNoPublicKeyReturned() {
        initEnclaveChallengeStubs();
        when(smsClient.generateTeeChallenge(AUTHORIZATION, CHAIN_TASK_ID)).thenReturn("");

        Optional<String> received = smsService.generateEnclaveChallenge(CHAIN_TASK_ID, URL);
        Assertions.assertThat(received)
                .isEmpty();
    }

    @Test
    void shouldNotGenerateEnclaveChallengeSinceFeignException() {
        initEnclaveChallengeStubs();
        when(smsClient.generateTeeChallenge(AUTHORIZATION, CHAIN_TASK_ID)).thenThrow(FeignException.GatewayTimeout.class);

        Optional<String> received = smsService.generateEnclaveChallenge(CHAIN_TASK_ID, URL);
        Assertions.assertThat(received)
                .isEmpty();
    }

    @Test
    void shouldNotGenerateEnclaveChallengeSinceRuntimeException() {
        initEnclaveChallengeStubs();
        when(smsClient.generateTeeChallenge(AUTHORIZATION, CHAIN_TASK_ID)).thenThrow(RuntimeException.class);

        Optional<String> received = smsService.generateEnclaveChallenge(CHAIN_TASK_ID, URL);
        Assertions.assertThat(received)
                .isEmpty();
    }
    // endregion

    // region pushWorkerpoolResultProxyUrl
    @Test
    void shouldDoNothingWhenSmsCommunicationFails(CapturedOutput output) {
        initResultProxyStubs();
        when(smsClient.getTeeFramework()).thenThrow(FeignException.class);
        smsService.pushWorkerpoolResultProxyUrl(new TaskInitializedEvent(CHAIN_TASK_ID));
        Assertions.assertThat(output.getOut()).contains("Failed to push default result-proxy URL to SMS");
    }

    @Test
    void shouldAddWorkerPoolResultProxyUrl() {
        initResultProxyStubs();
        when(smsClient.getTeeFramework()).thenReturn(TeeFramework.SCONE);
        when(signatureService.getAddress()).thenReturn(ADDRESS);
        when(signatureService.sign(anyString())).thenReturn(new Signature(AUTHORIZATION));
        doThrow(FeignException.NotFound.class).when(smsClient).isWeb2SecretSet(ADDRESS, IEXEC_RESULT_IEXEC_RESULT_PROXY_URL);
        smsService.pushWorkerpoolResultProxyUrl(new TaskInitializedEvent(CHAIN_TASK_ID));
        verify(smsClient).setWeb2Secret(AUTHORIZATION, ADDRESS, IEXEC_RESULT_IEXEC_RESULT_PROXY_URL, RESULT_PROXY_URL);
    }

    @Test
    void shouldUpdateWorkerPoolResultProxyUrl() {
        initResultProxyStubs();
        when(smsClient.getTeeFramework()).thenReturn(TeeFramework.SCONE);
        when(signatureService.getAddress()).thenReturn(ADDRESS);
        when(signatureService.sign(anyString())).thenReturn(new Signature(AUTHORIZATION));
        doNothing().when(smsClient).isWeb2SecretSet(ADDRESS, IEXEC_RESULT_IEXEC_RESULT_PROXY_URL);
        smsService.pushWorkerpoolResultProxyUrl(new TaskInitializedEvent(CHAIN_TASK_ID));
        verify(smsClient).updateWeb2Secret(AUTHORIZATION, ADDRESS, IEXEC_RESULT_IEXEC_RESULT_PROXY_URL, RESULT_PROXY_URL);
    }
    // endregion

    // region utils
    private void initEnclaveChallengeStubs() {
        when(smsClientProvider.getSmsClient(URL)).thenReturn(smsClient);
        when(signatureService.createAuthorization("", CHAIN_TASK_ID, ""))
                .thenReturn(WorkerpoolAuthorization.builder().signature(new Signature(AUTHORIZATION)).build());
    }

    private void initResultProxyStubs() {
        when(resultRepositoryConfiguration.getResultRepositoryURL()).thenReturn(RESULT_PROXY_URL);
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(
                TaskDescription.builder().teeFramework(TeeFramework.SCONE).build());
        when(registryConfiguration.getSconeSms()).thenReturn(SCONE_SMS_URL);
        when(smsClientProvider.getSmsClient(SCONE_SMS_URL)).thenReturn(smsClient);
    }
    // endregion
}
