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
import com.iexec.core.registry.PlatformRegistryConfiguration;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientProvider;
import feign.FeignException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SmsServiceTests {

    private static final String GRAMINE_SMS_URL = "http://gramine-sms";
    private static final String SCONE_SMS_URL = "http://scone-sms";
    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final String url = "url";

    @Mock
    private SmsClient smsClient;

    @Mock
    private SmsClientProvider smsClientProvider;
    @Mock
    private PlatformRegistryConfiguration registryConfiguration;

    @InjectMocks
    private SmsService smsService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        when(registryConfiguration.getSconeSms()).thenReturn(SCONE_SMS_URL);
        when(registryConfiguration.getGramineSms()).thenReturn(GRAMINE_SMS_URL);
    }

    // region isSmsClientReady
    static Stream<Arguments> validData() {
        List<String> supportedTeeTags =
                List.of(
                        TeeUtils.TEE_SCONE_ONLY_TAG,
                        TeeUtils.TEE_GRAMINE_ONLY_TAG);
        //Ensure all TeeEnclaveProvider are handled
        // (adding a new one would break assertion)
        Assertions.assertThat(supportedTeeTags)
                .hasSize(TeeFramework.values().length);
        return Stream.of(
                Arguments.of(supportedTeeTags.get(0), SCONE_SMS_URL),
                Arguments.of(supportedTeeTags.get(1), GRAMINE_SMS_URL)
        );
    }

    @ParameterizedTest
    @MethodSource("validData")
    void shouldGetVerifiedSmsUrl(String inputTag, String expectedSmsUrl) {
        when(smsClientProvider.getSmsClient(expectedSmsUrl)).thenReturn(smsClient);
        when(smsClient.getTeeFramework()).thenReturn(TeeUtils.getTeeFramework(inputTag));

        Assertions.assertThat(smsService.getVerifiedSmsUrl(CHAIN_TASK_ID, inputTag))
                .isEqualTo(Optional.of(expectedSmsUrl));

        verify(smsClientProvider).getSmsClient(expectedSmsUrl);
        verify(smsClient).getTeeFramework();
    }

    @Test
    void shouldNotGetVerifiedSmsUrlSinceCannotGetEnclaveProviderFromTag() {
        Assertions.assertThat(smsService.getVerifiedSmsUrl(CHAIN_TASK_ID, "0xabc"))
                .isEmpty();

        verify(smsClientProvider, times(0)).getSmsClient(anyString());
        verify(smsClient, times(0)).getTeeFramework();
    }

    @Test
    void shouldNotGetVerifiedSmsUrlSinceWrongTeeEnclaveProviderOnRemoteSms() {
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
        when(smsClientProvider.getSmsClient(url)).thenReturn(smsClient);
        when(smsClient.generateTeeChallenge(CHAIN_TASK_ID)).thenReturn("");

        Assertions.assertThat(smsService.getEnclaveChallenge(CHAIN_TASK_ID, ""))
                .get()
                .isEqualTo(BytesUtils.EMPTY_ADDRESS);
        verify(smsClient, never()).generateTeeChallenge(anyString());
    }

    @Test
    void shouldGetEnclaveChallengeForTeeTask() {
        String expected = "challenge";
        when(smsClientProvider.getSmsClient(url)).thenReturn(smsClient);
        when(smsClient.generateTeeChallenge(CHAIN_TASK_ID)).thenReturn(expected);

        Optional<String> received = smsService.getEnclaveChallenge(CHAIN_TASK_ID, url);
        verify(smsClient).generateTeeChallenge(CHAIN_TASK_ID);
        Assertions.assertThat(received)
                .get()
                .isEqualTo(expected);
    }

    @Test
    void shouldNotGetEnclaveChallengeForTeeTaskWhenEmptySmsResponse() {
        when(smsClientProvider.getSmsClient(url)).thenReturn(smsClient);
        when(smsClient.generateTeeChallenge(CHAIN_TASK_ID)).thenReturn("");
        Optional<String> received = smsService.getEnclaveChallenge(CHAIN_TASK_ID, url);
        verify(smsClient).generateTeeChallenge(CHAIN_TASK_ID);
        Assertions.assertThat(received).isEmpty();
    }
    // endregion

    // region generateEnclaveChallenge
    @Test
    void shouldGenerateEnclaveChallenge() {
        final String expected = "challenge";

        when(smsClientProvider.getSmsClient(url)).thenReturn(smsClient);
        when(smsClient.generateTeeChallenge(CHAIN_TASK_ID)).thenReturn(expected);

        Optional<String> received = smsService.generateEnclaveChallenge(CHAIN_TASK_ID, url);
        Assertions.assertThat(received)
                .contains(expected);
    }

    @Test
    void shouldNotGenerateEnclaveChallengeSinceNoPublicKeyReturned() {
        when(smsClientProvider.getSmsClient(url)).thenReturn(smsClient);
        when(smsClient.generateTeeChallenge(CHAIN_TASK_ID)).thenReturn("");

        Optional<String> received = smsService.generateEnclaveChallenge(CHAIN_TASK_ID, url);
        Assertions.assertThat(received)
                .isEmpty();
    }

    @Test
    void shouldNotGenerateEnclaveChallengeSinceFeignException() {
        when(smsClientProvider.getSmsClient(url)).thenReturn(smsClient);
        when(smsClient.generateTeeChallenge(CHAIN_TASK_ID)).thenThrow(FeignException.GatewayTimeout.class);

        Optional<String> received = smsService.generateEnclaveChallenge(CHAIN_TASK_ID, url);
        Assertions.assertThat(received)
                .isEmpty();
    }

    @Test
    void shouldNotGenerateEnclaveChallengeSinceRuntimeException() {
        when(smsClientProvider.getSmsClient(url)).thenReturn(smsClient);
        when(smsClient.generateTeeChallenge(CHAIN_TASK_ID)).thenThrow(RuntimeException.class);

        Optional<String> received = smsService.generateEnclaveChallenge(CHAIN_TASK_ID, url);
        Assertions.assertThat(received)
                .isEmpty();
    }
    // endregion
}
