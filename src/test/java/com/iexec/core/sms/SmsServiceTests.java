package com.iexec.core.sms;

import com.iexec.common.utils.BytesUtils;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientProvider;
import feign.FeignException;
import feign.Request;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.*;

class SmsServiceTests {

    private static final String CHAIN_TASK_ID = "chainTaskId";

    @Mock
    private SmsClient smsClient;

    @Mock
    private SmsClientProvider smsClientProvider;

    @InjectMocks
    private SmsService smsService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldGetEmptyAddressForStandardTask() {
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID)).thenReturn(smsClient);

        Assertions.assertThat(smsService.getEnclaveChallenge(CHAIN_TASK_ID, false))
                .get()
                .isEqualTo(BytesUtils.EMPTY_ADDRESS);
        verify(smsClient, never()).generateTeeChallenge(anyString());
    }

    @Test
    void shouldGetEnclaveChallengeForTeeTask() {
        String expected = "challenge";
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.generateTeeChallenge(CHAIN_TASK_ID)).thenReturn(expected);
        
        Optional<String> received = smsService.getEnclaveChallenge(CHAIN_TASK_ID, true);
        verify(smsClient).generateTeeChallenge(CHAIN_TASK_ID);
        Assertions.assertThat(received)
                .get()
                .isEqualTo(expected);
    }

    @Test
    void shouldNotGetEnclaveChallengeForTeeTaskWhenEmptySmsResponse() {
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.generateTeeChallenge(CHAIN_TASK_ID)).thenReturn("");
        Optional<String> received = smsService.getEnclaveChallenge(CHAIN_TASK_ID, true);
        verify(smsClient).generateTeeChallenge(CHAIN_TASK_ID);
        Assertions.assertThat(received).isEmpty();
    }

    @Test
    void shouldNotGetEnclaveChallengeForTeeTaskWhenNullSmsResponse() {
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID)).thenReturn(smsClient);
        when(smsClient.generateTeeChallenge(CHAIN_TASK_ID)).thenReturn(null);

        Optional<String> received = smsService.getEnclaveChallenge(CHAIN_TASK_ID, true);
        verify(smsClient).generateTeeChallenge(CHAIN_TASK_ID);
        Assertions.assertThat(received).isEmpty();
    }

    @Test
    void shouldNotGetEnclaveChallengeOnFeignException() {
        when(smsClientProvider.getOrCreateSmsClientForTask(CHAIN_TASK_ID)).thenReturn(smsClient);
        Request request = Request.create(Request.HttpMethod.HEAD, "http://localhost",
                Collections.emptyMap(), Request.Body.empty(), null);
        Assertions.assertThat(smsService.generateEnclaveChallenge(
                new FeignException.Unauthorized("", request, new byte[0], null),
                CHAIN_TASK_ID
                )
        ).isEmpty();
        verifyNoInteractions(smsClient);
    }
}