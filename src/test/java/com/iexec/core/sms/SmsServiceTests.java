package com.iexec.core.sms;

import com.iexec.common.chain.ChainDeal;
import com.iexec.common.chain.IexecHubAbstractService;
import com.iexec.common.task.TaskDescription;
import com.iexec.common.utils.BytesUtils;
import com.iexec.sms.api.SmsClient;
import com.iexec.sms.api.SmsClientCreationException;
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

    private static final String CHAIN_DEAL_ID = "chainDealId";
    private static final String CHAIN_TASK_ID = "chainTaskId";
    private static final ChainDeal CHAIN_DEAL = ChainDeal
            .builder()
            .chainDealId(CHAIN_DEAL_ID)
            .build();
    private static final TaskDescription TASK_DESCRIPTION = TaskDescription
            .builder()
            .chainTaskId(CHAIN_TASK_ID)
            .build();

    @Mock
    private SmsClient smsClient;

    @Mock
    private SmsClientProvider smsClientProvider;
    @Mock
    private IexecHubAbstractService iexecHubService;

    @InjectMocks
    private SmsService smsService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    // region isSmsClientReady
    @Test
    void smsClientShouldBeReady() {
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.of(CHAIN_DEAL));
        when(smsClientProvider.getOrCreateSmsClientForUninitializedTask(CHAIN_DEAL, CHAIN_TASK_ID)).thenReturn(mock(SmsClient.class));

        Assertions.assertThat(smsService.isSmsClientReady(CHAIN_DEAL_ID, CHAIN_TASK_ID)).isTrue();

        verify(smsClientProvider).getOrCreateSmsClientForUninitializedTask(CHAIN_DEAL, CHAIN_TASK_ID);
    }

    @Test
    void smsClientShouldBeNotReadySinceNoChainDeal() {
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.empty());

        Assertions.assertThat(smsService.isSmsClientReady(CHAIN_DEAL_ID, CHAIN_TASK_ID)).isFalse();

        verify(smsClientProvider, times(0)).getOrCreateSmsClientForUninitializedTask(CHAIN_DEAL, CHAIN_TASK_ID);
    }

    @Test
    void smsClientShouldBeNotReady() {
        when(iexecHubService.getChainDeal(CHAIN_DEAL_ID)).thenReturn(Optional.of(CHAIN_DEAL));
        when(smsClientProvider.getOrCreateSmsClientForUninitializedTask(CHAIN_DEAL, CHAIN_TASK_ID)).thenThrow(SmsClientCreationException.class);

        Assertions.assertThat(smsService.isSmsClientReady(CHAIN_DEAL_ID, CHAIN_TASK_ID)).isFalse();

        verify(smsClientProvider).getOrCreateSmsClientForUninitializedTask(CHAIN_DEAL, CHAIN_TASK_ID);
    }
    // endregion

    // region getEnclaveChallenge
    @Test
    void shouldGetEmptyAddressForStandardTask() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(TASK_DESCRIPTION);
        when(smsClientProvider.getOrCreateSmsClientForTask(TASK_DESCRIPTION)).thenReturn(smsClient);
        Assertions.assertThat(smsService.getEnclaveChallenge(CHAIN_TASK_ID, false))
                .get()
                .isEqualTo(BytesUtils.EMPTY_ADDRESS);
        verify(smsClient, never()).generateTeeChallenge(anyString());
    }

    @Test
    void shouldGetEnclaveChallengeForTeeTask() {
        String expected = "challenge";
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(TASK_DESCRIPTION);
        when(smsClientProvider.getOrCreateSmsClientForTask(TASK_DESCRIPTION)).thenReturn(smsClient);
        when(smsClient.generateTeeChallenge(CHAIN_TASK_ID)).thenReturn(expected);
        
        Optional<String> received = smsService.getEnclaveChallenge(CHAIN_TASK_ID, true);
        verify(smsClient).generateTeeChallenge(CHAIN_TASK_ID);
        Assertions.assertThat(received)
                .get()
                .isEqualTo(expected);
    }

    @Test
    void shouldNotGetEnclaveChallengeForTeeTaskWhenEmptySmsResponse() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(TASK_DESCRIPTION);
        when(smsClientProvider.getOrCreateSmsClientForTask(TASK_DESCRIPTION)).thenReturn(smsClient);
        when(smsClient.generateTeeChallenge(CHAIN_TASK_ID)).thenReturn("");
        Optional<String> received = smsService.getEnclaveChallenge(CHAIN_TASK_ID, true);
        verify(smsClient).generateTeeChallenge(CHAIN_TASK_ID);
        Assertions.assertThat(received).isEmpty();
    }

    @Test
    void shouldNotGetEnclaveChallengeForTeeTaskWhenNullSmsResponse() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(TASK_DESCRIPTION);
        when(smsClientProvider.getOrCreateSmsClientForTask(TASK_DESCRIPTION)).thenReturn(smsClient);
        when(smsClient.generateTeeChallenge(CHAIN_TASK_ID)).thenReturn(null);

        Optional<String> received = smsService.getEnclaveChallenge(CHAIN_TASK_ID, true);
        verify(smsClient).generateTeeChallenge(CHAIN_TASK_ID);
        Assertions.assertThat(received).isEmpty();
    }
    // endregion

    // region generateEnclaveChallenge
    @Test
    void shouldNotGetEnclaveChallengeOnFeignException() {
        when(iexecHubService.getTaskDescription(CHAIN_TASK_ID)).thenReturn(TASK_DESCRIPTION);
        when(smsClientProvider.getOrCreateSmsClientForTask(TASK_DESCRIPTION)).thenReturn(smsClient);
        Request request = Request.create(Request.HttpMethod.HEAD, "http://localhost",
                Collections.emptyMap(), Request.Body.empty(), null);
        Assertions.assertThat(smsService.generateEnclaveChallenge(
                new FeignException.Unauthorized("", request, new byte[0], null),
                CHAIN_TASK_ID
                )
        ).isEmpty();
        verifyNoInteractions(smsClient);
    }
    // endregion
}