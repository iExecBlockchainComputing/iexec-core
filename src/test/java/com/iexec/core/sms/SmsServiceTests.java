package com.iexec.core.sms;

import com.iexec.common.utils.BytesUtils;
import com.iexec.core.feign.SmsClient;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

public class SmsServiceTests {

    private static final String CHAIN_TASK_ID = "chainTaskId";

    @Mock
    private SmsClient smsClient;

    @InjectMocks
    private SmsService smsService;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldGetEnclaveChallengeForTeeTask() {
        String expected = "challenge";
        when(smsClient.generateTeeChallenge(CHAIN_TASK_ID)).thenReturn(expected);
        
        String received = smsService.getEnclaveChallenge(CHAIN_TASK_ID, true);
        verify(smsClient).generateTeeChallenge(CHAIN_TASK_ID);
        Assertions.assertThat(received).isEqualTo(expected);
    }

    @Test
    public void shouldNotGetEnclaveChallengeForTeeTaskWhenNullSmsResponse() {
        when(smsClient.generateTeeChallenge(CHAIN_TASK_ID)).thenReturn(null);
        
        String received = smsService.getEnclaveChallenge(CHAIN_TASK_ID, true);
        verify(smsClient).generateTeeChallenge(CHAIN_TASK_ID);
        Assertions.assertThat(received).isEmpty();
    }

    @Test
    public void shouldGetEmptyAddressForStandardTask() {
        Assertions.assertThat(smsService.getEnclaveChallenge(CHAIN_TASK_ID, false))
                .isEqualTo(BytesUtils.EMPTY_ADDRESS);
        verify(smsClient, never()).generateTeeChallenge(anyString());
    }
}