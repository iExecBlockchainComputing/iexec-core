package com.iexec.core.sms;

import com.iexec.common.utils.BytesUtils;
import com.iexec.core.feign.SmsClient;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.mockito.Mockito.*;

class SmsServiceTests {

    private static final String CHAIN_TASK_ID = "chainTaskId";

    @Mock
    private SmsClient smsClient;

    @InjectMocks
    private SmsService smsService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldGetEnclaveChallengeForTeeTask() {
        String expected = "challenge";
        when(smsClient.generateTeeChallenge(CHAIN_TASK_ID)).thenReturn(expected);
        
        Optional<String> received = smsService.getEnclaveChallenge(CHAIN_TASK_ID, true);
        verify(smsClient).generateTeeChallenge(CHAIN_TASK_ID);
        Assertions.assertThat(received)
                .get()
                .isEqualTo(expected);
    }

    @Test
    void shouldNotGetEnclaveChallengeForTeeTaskWhenNullSmsResponse() {
        when(smsClient.generateTeeChallenge(CHAIN_TASK_ID)).thenReturn(null);

        Optional<String> received = smsService.getEnclaveChallenge(CHAIN_TASK_ID, true);
        verify(smsClient).generateTeeChallenge(CHAIN_TASK_ID);
        Assertions.assertThat(received).isEmpty();
    }

    @Test
    void shouldGetEmptyAddressForStandardTask() {
        Assertions.assertThat(smsService.getEnclaveChallenge(CHAIN_TASK_ID, false))
                .get()
                .isEqualTo(BytesUtils.EMPTY_ADDRESS);
        verify(smsClient, never()).generateTeeChallenge(anyString());
    }
}