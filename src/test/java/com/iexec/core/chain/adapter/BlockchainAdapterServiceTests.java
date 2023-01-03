/*
 * Copyright 2021 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.chain.adapter;

import com.iexec.blockchain.api.BlockchainAdapterApiClient;
import com.iexec.common.chain.adapter.CommandStatus;
import com.iexec.common.chain.adapter.args.TaskFinalizeArgs;
import com.iexec.common.config.PublicChainConfig;
import feign.FeignException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.mockito.Mockito.*;

class BlockchainAdapterServiceTests {

    public static final String CHAIN_TASK_ID = "CHAIN_TASK_ID";
    public static final String CHAIN_DEAL_ID = "CHAIN_DEAL_ID";
    public static final int TASK_INDEX = 0;
    public static final String LINK = "link";
    public static final String CALLBACK = "callback";
    public static final int PERIOD = 10;
    public static final int MAX_ATTEMPTS = 3;

    @Mock
    private BlockchainAdapterApiClient blockchainAdapterClient;

    @InjectMocks
    private BlockchainAdapterService blockchainAdapterService;

    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
    }

    //Initialize

    @Test
    void requestInitialize() {
        when(blockchainAdapterClient.requestInitializeTask(CHAIN_DEAL_ID, TASK_INDEX))
                .thenReturn(CHAIN_TASK_ID);

        Assertions.assertThat(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, TASK_INDEX))
                .isPresent();
    }

    @Test
    void requestInitializeFailedSinceNot200() {
        when(blockchainAdapterClient.requestInitializeTask(CHAIN_DEAL_ID, TASK_INDEX))
                .thenThrow(FeignException.BadRequest.class);

        Assertions.assertThat(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, TASK_INDEX))
                .isEmpty();
    }

    @Test
    void requestInitializeFailedSinceNoBody() {
        when(blockchainAdapterClient.requestInitializeTask(CHAIN_DEAL_ID, TASK_INDEX))
                .thenReturn("");

        Assertions.assertThat(blockchainAdapterService.requestInitialize(CHAIN_DEAL_ID, TASK_INDEX))
                .isEmpty();
    }

    @Test
    void isInitialized() {
        when(blockchainAdapterClient.getStatusForInitializeTaskRequest(CHAIN_TASK_ID))
                .thenReturn(CommandStatus.SUCCESS);
        Assertions.assertThat(blockchainAdapterService.isInitialized(CHAIN_TASK_ID))
                .isEqualTo(Optional.of(true));
    }

    // Finalize

    @Test
    void requestFinalize() {
        when(blockchainAdapterClient.requestFinalizeTask(CHAIN_TASK_ID, new TaskFinalizeArgs(LINK, CALLBACK)))
                .thenReturn(CHAIN_TASK_ID);

        Assertions.assertThat(blockchainAdapterService.requestFinalize(CHAIN_TASK_ID, LINK, CALLBACK))
                .isPresent();
    }

    @Test
    void requestFinalizeFailedSinceNot200() {
        when(blockchainAdapterClient.requestFinalizeTask(CHAIN_TASK_ID, new TaskFinalizeArgs(LINK, CALLBACK)))
                .thenThrow(FeignException.BadRequest.class);

        Assertions.assertThat(blockchainAdapterService.requestFinalize(CHAIN_TASK_ID, LINK, CALLBACK))
                .isEmpty();
    }

    @Test
    void requestFinalizeFailedSinceNoBody() {
        when(blockchainAdapterClient.requestFinalizeTask(CHAIN_TASK_ID, new TaskFinalizeArgs(LINK, CALLBACK)))
                .thenReturn("");

        Assertions.assertThat(blockchainAdapterService.requestFinalize(CHAIN_TASK_ID, LINK, CALLBACK))
                .isEmpty();
    }

    @Test
    void isFinalized() {
        when(blockchainAdapterClient.getStatusForFinalizeTaskRequest(CHAIN_TASK_ID))
                .thenReturn(CommandStatus.SUCCESS);
        Assertions.assertThat(blockchainAdapterService.isFinalized(CHAIN_TASK_ID))
                .isEqualTo(Optional.of(true));
    }

    // Testing ability to pull & wait

    @Test
    void isCommandCompletedWithSuccess() {
        when(blockchainAdapterClient.getStatusForInitializeTaskRequest(CHAIN_TASK_ID))
                .thenReturn(CommandStatus.RECEIVED)
                .thenReturn(CommandStatus.PROCESSING)
                .thenReturn(CommandStatus.SUCCESS);

        Optional<Boolean> commandCompleted = blockchainAdapterService
                .isCommandCompleted(blockchainAdapterClient::getStatusForInitializeTaskRequest,
                CHAIN_TASK_ID, PERIOD, MAX_ATTEMPTS);
        Assertions.assertThat(commandCompleted).isPresent();
        Assertions.assertThat(commandCompleted.get()).isTrue();
    }

    @Test
    void isCommandCompletedWithFailure() {
        when(blockchainAdapterClient.getStatusForInitializeTaskRequest(CHAIN_TASK_ID))
                .thenReturn(CommandStatus.RECEIVED)
                .thenReturn(CommandStatus.PROCESSING)
                .thenReturn(CommandStatus.FAILURE);

        Optional<Boolean> commandCompleted = blockchainAdapterService
                .isCommandCompleted(blockchainAdapterClient::getStatusForInitializeTaskRequest,
                CHAIN_TASK_ID, PERIOD, MAX_ATTEMPTS);
        Assertions.assertThat(commandCompleted).isPresent();
        Assertions.assertThat(commandCompleted.get()).isFalse();
    }

    @Test
    void isCommandCompletedWithMaxAttempts() {
        when(blockchainAdapterClient.getStatusForInitializeTaskRequest(CHAIN_TASK_ID))
                .thenReturn(CommandStatus.PROCESSING);
        Optional<Boolean> commandCompleted = blockchainAdapterService
                .isCommandCompleted(blockchainAdapterClient::getStatusForInitializeTaskRequest,
                        CHAIN_TASK_ID, PERIOD, MAX_ATTEMPTS);
        Assertions.assertThat(commandCompleted).isEmpty();
    }

    // region getPublicChainConfig
    @Test
    void shouldGetPublicChainConfigOnlyOnce() {
        final PublicChainConfig expectedChainConfig = PublicChainConfig.builder().build();
        when(blockchainAdapterClient.getPublicChainConfig())
                .thenReturn(expectedChainConfig);

        final PublicChainConfig actualChainConfig =
                blockchainAdapterService.getPublicChainConfig();

        Assertions.assertThat(actualChainConfig).isEqualTo(expectedChainConfig);
        Assertions.assertThat(blockchainAdapterService.getPublicChainConfig())
                .isEqualTo(expectedChainConfig);

        // When calling `blockchainAdapterService.getPublicChainConfig()` again,
        // it should retrieve the cached value.
        blockchainAdapterService.getPublicChainConfig();
        verify(blockchainAdapterClient, times(1)).getPublicChainConfig();
    }

    @Test
    void shouldNotGetPublicChainConfigSinceNotFound() {
        when(blockchainAdapterClient.getPublicChainConfig())
                .thenThrow(FeignException.NotFound.class);

        final PublicChainConfig actualChainConfig =
                blockchainAdapterService.getPublicChainConfig();
        Assertions.assertThat(actualChainConfig).isNull();
    }

    // endregion
}