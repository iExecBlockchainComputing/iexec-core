/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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
import jakarta.validation.constraints.NotEmpty;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Value
@Validated
@ConfigurationProperties(prefix = "result-repository")
@Slf4j
public class ResultRepositoryConfiguration {
    @URL(message = "URL must be a valid URL")
    @NotEmpty(message = "URL must not be empty")
    String url;

    public ResultProxyClient createResultProxyClientFromURL(final String url) {
        final boolean useDefaultUrl = StringUtils.isBlank(url);
        final String resultProxyClientURL = useDefaultUrl ? getUrl() : url;
        log.debug("result-proxy URL [url:{}, default-url:{}]", resultProxyClientURL, useDefaultUrl);
        return ResultProxyClientBuilder.getInstance(Logger.Level.NONE, resultProxyClientURL);
    }

}
