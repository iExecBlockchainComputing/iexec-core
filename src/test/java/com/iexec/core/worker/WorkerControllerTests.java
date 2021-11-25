package com.iexec.core.worker;

import com.iexec.common.config.WorkerModel;
import com.iexec.common.security.Signature;
import com.iexec.core.chain.ChainConfig;
import com.iexec.core.chain.CredentialsService;
import com.iexec.core.chain.adapter.BlockchainAdapterClientConfig;
import com.iexec.core.configuration.ResultRepositoryConfiguration;
import com.iexec.core.configuration.SmsConfiguration;
import com.iexec.core.configuration.WorkerConfiguration;
import com.iexec.core.security.ChallengeService;
import com.iexec.core.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.web3j.crypto.Credentials;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class WorkerControllerTests {

    private static final String TOKEN = "token";
    private static final String WALLET = "wallet";
    private static final String CHALLENGE = "challenge";
    private static final Worker WORKER = Worker.builder()
            .walletAddress(WALLET)
            .build();
    private static final Signature SIGN =
            new Signature("0x36a0678dbab83abb34e6c28a7f62ca8dd16499d1afd5e5762242b4051359a1ea2ce70da0dfd5614237d47390162e10415362f64598a32f3d5300193ddc92439f1c");
    private static final WorkerModel WORKER_MODEL = WorkerModel.builder()
            .walletAddress(WALLET)
            .build();

    @Mock
    private WorkerService workerService;
    @Mock
    private ChainConfig chainConfig;
    @Mock
    private CredentialsService credentialsService;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private ChallengeService challengeService;
    @Mock
    private WorkerConfiguration workerConfiguration;
    @Mock
    private ResultRepositoryConfiguration resultRepoConfig;
    @Mock
    private SmsConfiguration smsConfiguration;
    @Mock
    private BlockchainAdapterClientConfig blockchainAdapterClientConfig;

    @InjectMocks
    private WorkerController workerController;

    @BeforeEach
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    // ping

    @Test
    public void shouldAcceptPing() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN)).thenReturn(WALLET);
        when(workerService.updateLastAlive(WALLET)).thenReturn(Optional.of(WORKER));

        ResponseEntity<String> response = workerController.ping(TOKEN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        verify(workerService).updateLastAlive(WALLET);
    }

    @Test
    public void shouldAcceptPingAndGetSameSessionIdForTwoCalls() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN)).thenReturn(WALLET);
        when(workerService.updateLastAlive(WALLET)).thenReturn(Optional.of(WORKER));

        ResponseEntity<String> response1 = workerController.ping(TOKEN);
        ResponseEntity<String> response2 = workerController.ping(TOKEN);
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response1.getBody()).isEqualTo(response2.getBody());
        verify(workerService, times(2)).updateLastAlive(WALLET);
    }

    @Test
    public void shouldNotAcceptPingSinceUnauthorizedJwt() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN)).thenReturn("");

        ResponseEntity<String> response = workerController.ping(TOKEN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(workerService, never()).updateLastAlive(WALLET);
    }

    @Test
    public void shouldNotAcceptPingSinceCannotUpdateLastAlive() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN)).thenReturn(WALLET);
        when(workerService.updateLastAlive(WALLET)).thenReturn(Optional.empty());

        ResponseEntity<String> response = workerController.ping(TOKEN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(workerService).updateLastAlive(WALLET);
    }

    // getChallenge

    @Test
    public void shouldGetChallenge() {
        when(workerService.isAllowedToJoin(WALLET)).thenReturn(true);
        when(challengeService.getChallenge(WALLET)).thenReturn(CHALLENGE);

        ResponseEntity<String> response = workerController.getChallenge(WALLET);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(CHALLENGE);
        verify(challengeService).getChallenge(WALLET);
    }

    @Test
    public void shouldGetSameChallengeForSameWorker() {
        when(workerService.isAllowedToJoin(WALLET)).thenReturn(true);
        when(challengeService.getChallenge(WALLET)).thenReturn(CHALLENGE);

        ResponseEntity<String> response1 = workerController.getChallenge(WALLET);
        ResponseEntity<String> response2 = workerController.getChallenge(WALLET);
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response1.getBody())
                .isEqualTo(response2.getBody())
                .isEqualTo(CHALLENGE);
        verify(challengeService, times(2)).getChallenge(WALLET);
    }

    @Test
    public void shouldGetDifferentChallengesForDifferentWorkers() {
        String wallet1 = WALLET;
        String wallet2 = "otherWallet";
        String challenge1 = "challenge1";
        String challenge2 = "challenge2";
        when(workerService.isAllowedToJoin(wallet1)).thenReturn(true);
        when(workerService.isAllowedToJoin(wallet2)).thenReturn(true);
        when(challengeService.getChallenge(wallet1)).thenReturn(challenge1);
        when(challengeService.getChallenge(wallet2)).thenReturn(challenge2);

        ResponseEntity<String> response1 = workerController.getChallenge(WALLET);
        ResponseEntity<String> response2 = workerController.getChallenge("otherWallet");
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response1.getBody()).isNotEqualTo(response2.getBody());
        verify(challengeService).getChallenge(wallet1);
        verify(challengeService).getChallenge(wallet2);
    }

    @Test
    public void shouldNotGetChallengeSinceNotAllowedToJoin() {
        when(workerService.isAllowedToJoin(WALLET)).thenReturn(false);

        ResponseEntity<String> response = workerController.getChallenge(WALLET);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(challengeService, never()).getChallenge(WALLET);
    }

    // getToken

    @Test
    public void shouldGetToken() {
        // TODO
    }

    @Test
    public void shouldNotGetTokenSinceNotAllowed() {
        when(workerService.isAllowedToJoin(WALLET)).thenReturn(false);
        
        ResponseEntity<String> response = workerController.getToken(WALLET, SIGN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void shouldNotGetTokenSinceSignatureNotValid() {
        when(workerService.isAllowedToJoin(WALLET)).thenReturn(true);
        when(challengeService.getChallenge(WALLET)).thenReturn(CHALLENGE);
        
        ResponseEntity<String> response = workerController.getToken(WALLET, SIGN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // registerWorker

    @Test
    public void shouldRegisterWorker() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET);
        when(workerService.addWorker(any())).thenReturn(WORKER);

        ResponseEntity<Worker> response =
                workerController.registerWorker(TOKEN, WORKER_MODEL);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getWalletAddress()).isEqualTo(WALLET);
        verify(workerService).addWorker(any());
    }


    @Test
    public void shouldRegisterGPUWorkerWithMaxNbTasksEqualToOne() {
        WORKER_MODEL.setGpuEnabled(true);
        WORKER.setMaxNbTasks(1);
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET);
        when(workerService.addWorker(any())).thenReturn(WORKER);

        ResponseEntity<Worker> response =
                workerController.registerWorker(TOKEN, WORKER_MODEL);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getWalletAddress()).isEqualTo(WALLET);
        assertThat(response.getBody().getMaxNbTasks()).isEqualTo(1);
        verify(workerService).addWorker(any());
    }

    @Test
    public void shouldNotRegisterWorkerSinceUnauthorized() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN)).thenReturn("");

        ResponseEntity<Worker> response =
                workerController.registerWorker(TOKEN, WORKER_MODEL);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(workerService, never()).addWorker(any());
    }

    // getPublicConfiguration

    @Test
    public void shouldGetPublicConfiguration() {
        when(credentialsService.getCredentials()).thenReturn(mock(Credentials.class));
        assertThat(workerController.getPublicConfiguration().getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    // getTasksInProgress

    @Test
    public void shouldGetTasksInProgress() {
        List<String> list = List.of("t1", "t2");
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN))
                .thenReturn(WALLET);
        when(workerService.getComputingTaskIds(WALLET)).thenReturn(list);
        ResponseEntity<List<String>> response =
                workerController.getComputingTasks(TOKEN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(list);
    }

    @Test
    public void shouldNotGetTasksInProgressSinceUnauthorized() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN)).thenReturn("");
        ResponseEntity<List<String>> response =
                workerController.getComputingTasks(TOKEN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }


}
