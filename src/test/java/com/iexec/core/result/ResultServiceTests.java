package com.iexec.core.result;

import com.iexec.commons.poco.chain.WorkerpoolAuthorization;
import com.iexec.commons.poco.security.Signature;
import com.iexec.commons.poco.utils.BytesUtils;
import com.iexec.commons.poco.utils.HashUtils;
import com.iexec.core.chain.SignatureService;
import com.iexec.core.configuration.ResultRepositoryConfiguration;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.resultproxy.api.ResultProxyClient;
import feign.FeignException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;

import java.util.Optional;

import static com.iexec.commons.poco.tee.TeeUtils.TEE_SCONE_ONLY_TAG;
import static com.iexec.commons.poco.utils.BytesUtils.EMPTY_ADDRESS;
import static com.iexec.core.task.TaskTestsUtils.CHAIN_TASK_ID;
import static com.iexec.core.task.TaskTestsUtils.getStubTask;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultServiceTests {
    @Mock
    private ResultProxyClient resultProxyClient;
    @Mock
    private ResultRepositoryConfiguration resultRepositoryConfiguration;
    @Mock
    private SignatureService signatureService;
    @Mock
    private TaskService taskService;

    @InjectMocks
    private ResultService resultService;

    private Credentials enclaveCreds;
    private Credentials schedulerCreds;
    private Signature signature;
    private WorkerpoolAuthorization workerpoolAuthorization;

    @BeforeEach
    void init() {
        enclaveCreds = createCredentials();
        schedulerCreds = createCredentials();
        final String hash = HashUtils.concatenateAndHash(schedulerCreds.getAddress(), CHAIN_TASK_ID, enclaveCreds.getAddress());
        signature = new Signature(Sign.signPrefixedMessage(BytesUtils.stringToBytes(hash), schedulerCreds.getEcKeyPair()));
        workerpoolAuthorization = WorkerpoolAuthorization.builder()
                .workerWallet(schedulerCreds.getAddress())
                .chainTaskId(CHAIN_TASK_ID)
                .enclaveChallenge(enclaveCreds.getAddress())
                .signature(signature)
                .build();
        when(signatureService.getAddress()).thenReturn(schedulerCreds.getAddress());
        when(resultRepositoryConfiguration.createResultProxyClient(any())).thenReturn(resultProxyClient);
    }

    @Test
    void shouldReturnFalseWhenEmptyToken() {
        final Task task = getStubTask();
        task.setEnclaveChallenge(EMPTY_ADDRESS);
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(signatureService.createAuthorization(schedulerCreds.getAddress(), CHAIN_TASK_ID, EMPTY_ADDRESS))
                .thenReturn(workerpoolAuthorization);
        when(resultProxyClient.getJwt(anyString(), any())).thenReturn("");
        assertThat(resultService.isResultUploaded(CHAIN_TASK_ID)).isFalse();
        verify(signatureService).createAuthorization(schedulerCreds.getAddress(), CHAIN_TASK_ID, EMPTY_ADDRESS);
        verify(resultProxyClient).getJwt(signature.getValue(), workerpoolAuthorization);
    }

    @Test
    void shouldReturnFalseWhenUnauthorizedToUpload() {
        final Task task = getStubTask();
        task.setTag(TEE_SCONE_ONLY_TAG);
        task.setEnclaveChallenge(enclaveCreds.getAddress());
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(signatureService.createAuthorization(schedulerCreds.getAddress(), CHAIN_TASK_ID, enclaveCreds.getAddress()))
                .thenReturn(workerpoolAuthorization);
        when(resultProxyClient.getJwt(anyString(), any())).thenReturn("token");
        when(resultProxyClient.isResultUploaded("token", CHAIN_TASK_ID)).thenThrow(FeignException.Unauthorized.class);
        assertThatThrownBy(() -> resultService.isResultUploaded(CHAIN_TASK_ID))
                .isInstanceOf(FeignException.Unauthorized.class);
        verify(signatureService).createAuthorization(schedulerCreds.getAddress(), CHAIN_TASK_ID, enclaveCreds.getAddress());
    }

    @Test
    void shouldReturnTrueWhenStandardTaskResultUploaded() {
        final Task task = getStubTask();
        task.setEnclaveChallenge(EMPTY_ADDRESS);
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(signatureService.createAuthorization(schedulerCreds.getAddress(), CHAIN_TASK_ID, EMPTY_ADDRESS))
                .thenReturn(workerpoolAuthorization);
        when(resultProxyClient.getJwt(anyString(), any())).thenReturn("token");
        assertThat(resultService.isResultUploaded(CHAIN_TASK_ID)).isTrue();
        verify(signatureService).createAuthorization(schedulerCreds.getAddress(), CHAIN_TASK_ID, EMPTY_ADDRESS);
    }

    @Test
    void shouldReturnTrueWhenTeeTaskResultUploaded() {
        final Task task = getStubTask();
        task.setTag(TEE_SCONE_ONLY_TAG);
        task.setEnclaveChallenge(enclaveCreds.getAddress());
        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(signatureService.createAuthorization(schedulerCreds.getAddress(), CHAIN_TASK_ID, enclaveCreds.getAddress()))
                .thenReturn(workerpoolAuthorization);
        when(resultProxyClient.getJwt(anyString(), any())).thenReturn("token");
        assertThat(resultService.isResultUploaded(CHAIN_TASK_ID)).isTrue();
        verify(signatureService).createAuthorization(schedulerCreds.getAddress(), CHAIN_TASK_ID, enclaveCreds.getAddress());
    }

    @Test
    void shouldUseProxyUrlIfProvided() {
        String proxyUrl = "https://custom-proxy.com";
        Task task = getStubTask();
        task.setEnclaveChallenge(EMPTY_ADDRESS);

        when(taskService.getTaskByChainTaskId(CHAIN_TASK_ID)).thenReturn(Optional.of(task));
        when(signatureService.createAuthorization(schedulerCreds.getAddress(), CHAIN_TASK_ID, EMPTY_ADDRESS))
                .thenReturn(workerpoolAuthorization);
        when(resultRepositoryConfiguration.createResultProxyClient(proxyUrl)).thenReturn(resultProxyClient);
        when(resultProxyClient.getJwt(anyString(), any())).thenReturn("token");

        assertThat(resultService.isResultUploaded(CHAIN_TASK_ID, proxyUrl)).isTrue();
        verify(resultRepositoryConfiguration).createResultProxyClient(proxyUrl);
    }

    @SneakyThrows
    private Credentials createCredentials() {
        return Credentials.create(Keys.createEcKeyPair());
    }
}
