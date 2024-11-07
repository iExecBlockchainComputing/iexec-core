/*
 * Copyright 2024 IEXEC BLOCKCHAIN TECH
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

import com.iexec.resultproxy.api.ResultProxyClient;
import com.iexec.resultproxy.api.ResultProxyClientBuilder;
import feign.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResultRepositoryConfigurationTest {

    private ResultRepositoryConfiguration config;

    private static final String PROTOCOL = "http";
    private static final String HOST = "localhost";
    private static final String PORT = "8080";

    @BeforeEach
    void setUp() {
        config = new ResultRepositoryConfiguration(PROTOCOL, HOST, PORT);
    }

    @Test
    void shouldReturnCorrectResultRepositoryURL() {
        String expectedUrl = "http://localhost:8080";
        assertEquals(expectedUrl, config.getResultRepositoryURL());
    }

    @Test
    void shouldCreateResultProxyClientWithDefaultUrlWhenProxyUrlIsNullOrEmpty() {
        try (MockedStatic<ResultProxyClientBuilder> mockedBuilder = mockStatic(ResultProxyClientBuilder.class)) {
            ResultProxyClient mockClient = mock(ResultProxyClient.class);
            String defaultUrl = config.getResultRepositoryURL();

            mockedBuilder.when(() -> ResultProxyClientBuilder.getInstance(eq(Logger.Level.NONE), eq(defaultUrl)))
                    .thenReturn(mockClient);

            ResultProxyClient clientWithNull = config.createResultProxyClientFromURL(null);
            assertNotNull(clientWithNull);

            ResultProxyClient clientWithEmpty = config.createResultProxyClientFromURL("");
            assertNotNull(clientWithEmpty);

            mockedBuilder.verify(() -> ResultProxyClientBuilder.getInstance(Logger.Level.NONE, defaultUrl), times(2));
        }
    }

    @Test
    void shouldCreateResultProxyClientWithProvidedProxyUrl() {
        try (MockedStatic<ResultProxyClientBuilder> mockedBuilder = mockStatic(ResultProxyClientBuilder.class)) {
            ResultProxyClient mockClient = mock(ResultProxyClient.class);
            String proxyUrl = "http://proxy.example.com";

            mockedBuilder.when(() -> ResultProxyClientBuilder.getInstance(eq(Logger.Level.NONE), eq(proxyUrl)))
                    .thenReturn(mockClient);

            ResultProxyClient client = config.createResultProxyClientFromURL(proxyUrl);
            assertNotNull(client);

            mockedBuilder.verify(() -> ResultProxyClientBuilder.getInstance(Logger.Level.NONE, proxyUrl));
        }
    }
}
