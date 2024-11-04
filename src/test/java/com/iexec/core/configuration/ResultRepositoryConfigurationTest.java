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

            ResultProxyClient clientWithNull = config.createResultProxyClient(null);
            assertNotNull(clientWithNull);

            ResultProxyClient clientWithEmpty = config.createResultProxyClient("");
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

            ResultProxyClient client = config.createResultProxyClient(proxyUrl);
            assertNotNull(client);

            mockedBuilder.verify(() -> ResultProxyClientBuilder.getInstance(Logger.Level.NONE, proxyUrl));
        }
    }
}
