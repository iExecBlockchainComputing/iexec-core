package com.iexec.core.worker;

import com.iexec.common.config.PublicConfiguration;
import com.iexec.common.config.WorkerModel;
import com.iexec.common.security.Signature;
import com.iexec.core.configuration.PublicConfigurationService;
import com.iexec.core.security.ChallengeService;
import com.iexec.core.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WorkerControllerTests {

    private static final String TOKEN = "token";
    private static final String WALLET = "0x108ca59d5d0eec2ff66003f8909eb40addd1a67d";
    private static final String CHALLENGE = "challenge";
    private static final Worker WORKER = Worker.builder()
            .walletAddress(WALLET)
            .build();
    private static final Signature SIGN =
            new Signature("0xf11e72d3a1d6e13c5187862cfa0342f135909f36c369e01f2392ad19daee781377328a1441ac993f518d7024aafed664508d8dadf42ebdd97394ebfeb5b837121c");
    private static final Signature INVALID_SIGNATURE =
            new Signature("0xab5861286e8ef54febad4e606d7e7f32bec8e017a909bfa54e95ad6a7d42dc3f1cab39bacff7aeb15514d41750a3d7da0b21759063c50db1a8ac378062977ba51b");
    private static final WorkerModel WORKER_MODEL = WorkerModel.builder()
            .walletAddress(WALLET)
            .build();
    private static final String PUBLIC_CONFIGURATION_HASH = "publicConfigurationHash";

    @Mock
    private WorkerService workerService;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private ChallengeService challengeService;
    @Mock
    private PublicConfigurationService publicConfigurationService;

    @InjectMocks
    private WorkerController workerController;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    //region ping
    @Test
    void shouldAcceptPing() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN)).thenReturn(WALLET);
        when(workerService.updateLastAlive(WALLET)).thenReturn(Optional.of(WORKER));
        when(publicConfigurationService.getPublicConfigurationHash()).thenReturn(PUBLIC_CONFIGURATION_HASH);

        ResponseEntity<String> response = workerController.ping(TOKEN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
        verify(workerService).updateLastAlive(WALLET);
    }

    @Test
    void shouldAcceptPingAndGetSameSessionIdForTwoCalls() {
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
    void shouldNotAcceptPingSinceUnauthorizedJwt() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN)).thenReturn("");

        ResponseEntity<String> response = workerController.ping(TOKEN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(workerService, never()).updateLastAlive(WALLET);
    }

    @Test
    void shouldNotAcceptPingSinceCannotUpdateLastAlive() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN)).thenReturn(WALLET);
        when(workerService.updateLastAlive(WALLET)).thenReturn(Optional.empty());

        ResponseEntity<String> response = workerController.ping(TOKEN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(workerService).updateLastAlive(WALLET);
    }
    //endregion

    //region getChallenge
    @Test
    void shouldGetChallenge() {
        when(workerService.isAllowedToJoin(WALLET)).thenReturn(true);
        when(challengeService.getChallenge(WALLET)).thenReturn(CHALLENGE);

        ResponseEntity<String> response = workerController.getChallenge(WALLET);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(CHALLENGE);
        verify(challengeService).getChallenge(WALLET);
    }

    @Test
    void shouldGetSameChallengeForSameWorker() {
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
    void shouldGetDifferentChallengesForDifferentWorkers() {
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
    void shouldNotGetChallengeSinceNotAllowedToJoin() {
        when(workerService.isAllowedToJoin(WALLET)).thenReturn(false);

        ResponseEntity<String> response = workerController.getChallenge(WALLET);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(challengeService, never()).getChallenge(WALLET);
    }
    //endregion

    //region getToken
    @Test
    void shouldGetToken() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        when(workerService.isAllowedToJoin(WALLET)).thenReturn(true);
        when(challengeService.getChallenge(WALLET)).thenReturn(CHALLENGE);
        when(jwtTokenProvider.createToken(WALLET)).thenReturn(TOKEN);
        ResponseEntity<String> response = workerController.getToken(WALLET, SIGN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldNotGetTokenSinceNotAllowed() {
        when(workerService.isAllowedToJoin(WALLET)).thenReturn(false);
        ResponseEntity<String> response = workerController.getToken(WALLET, SIGN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldNotGetTokenSinceSignatureNotValid() {
        when(workerService.isAllowedToJoin(WALLET)).thenReturn(true);
        when(challengeService.getChallenge(WALLET)).thenReturn(CHALLENGE);
        
        ResponseEntity<String> response = workerController.getToken(WALLET, INVALID_SIGNATURE);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    //endregion

    //region registerWorker
    @Test
    void shouldRegisterWorker() {
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
    void shouldRegisterGPUWorkerWithMaxNbTasksEqualToOne() {
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
    void shouldNotRegisterWorkerSinceUnauthorized() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN)).thenReturn("");

        ResponseEntity<Worker> response =
                workerController.registerWorker(TOKEN, WORKER_MODEL);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(workerService, never()).addWorker(any());
    }
    //endregion

    //region getPublicConfiguration
    @Test
    void shouldGetPublicConfiguration() {
        when(publicConfigurationService.getPublicConfiguration()).thenReturn(new PublicConfiguration());
        assertThat(workerController.getPublicConfiguration().getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
    //endregion

    //region getTasksInProgress
    @Test
    void shouldGetTasksInProgress() {
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
    void shouldNotGetTasksInProgressSinceUnauthorized() {
        when(jwtTokenProvider.getWalletAddressFromBearerToken(TOKEN)).thenReturn("");
        ResponseEntity<List<String>> response =
                workerController.getComputingTasks(TOKEN);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    //endregion

}
