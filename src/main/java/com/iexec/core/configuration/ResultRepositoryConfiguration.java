/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@Value
@ConstructorBinding
@ConfigurationProperties(prefix = "result-repository")
@Slf4j
public class ResultRepositoryConfiguration {
    String protocol;
    String host;
    String port;

    public String getResultRepositoryURL() {
        return protocol + "://" + host + ":" + port;
    }

    public ResultProxyClient createResultProxyClient(final String proxyUrl) {
        String urlToUse;
        if (proxyUrl != null && !proxyUrl.isEmpty()) {
            urlToUse = proxyUrl;
            log.debug("Using overridden proxy URL: {}", urlToUse);
        } else {
            urlToUse = getResultRepositoryURL();
            log.debug("Using default result repository URL: {}", urlToUse);
        }
        return ResultProxyClientBuilder.getInstance(Logger.Level.NONE, urlToUse);
    }
}
