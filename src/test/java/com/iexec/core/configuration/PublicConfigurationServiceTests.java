/*
 * Copyright 2022-2024 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.configuration;

import com.iexec.commons.poco.chain.SignerService;
import com.iexec.core.chain.ChainConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class PublicConfigurationServiceTests {
    @Mock
    private ChainConfig chainConfig;
    @Mock
    private SignerService signerService;
    @Mock
    private WorkerConfiguration workerConfiguration;
    @Mock
    private ResultRepositoryConfiguration resultRepoConfig;
    @Mock
    private ConfigServerClientConfig configServerClientConfig;

    @InjectMocks
    private PublicConfigurationService publicConfigurationService;

    // region getPublicConfiguration
    @Test
    void shouldGetPublicConfiguration() {
        // This would be done by Spring in production
        publicConfigurationService.buildPublicConfiguration();

        assertNotNull(publicConfigurationService.getPublicConfiguration());
    }

    @Test
    void shouldNotGetPublicConfigurationWhenNotInitialized() {
        assertThrows(IllegalArgumentException.class, publicConfigurationService::getPublicConfiguration);
    }
    // endregion

    // region getPublicConfiguration
    @Test
    void shouldGetPublicConfigurationHash() {
        // This would be done by Spring in production
        publicConfigurationService.buildPublicConfiguration();

        assertNotNull(publicConfigurationService.getPublicConfigurationHash());
    }

    @Test
    void shouldNotGetPublicConfigurationHashWhenNotInitialized() {
        assertThrows(IllegalArgumentException.class, publicConfigurationService::getPublicConfigurationHash);
    }
    // endregion
}
