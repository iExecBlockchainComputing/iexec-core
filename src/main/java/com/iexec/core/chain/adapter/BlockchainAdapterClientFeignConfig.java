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

import feign.Response;
import feign.RetryableException;
import feign.Retryer;
import feign.auth.BasicAuthRequestInterceptor;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

import java.util.Date;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Slf4j
public class BlockchainAdapterClientFeignConfig {

    private final BlockchainAdapterClientConfig clientConfig;

    public BlockchainAdapterClientFeignConfig(BlockchainAdapterClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    @Bean
    public BasicAuthRequestInterceptor basicAuthRequestInterceptor() {
        return new BasicAuthRequestInterceptor(clientConfig.getBlockchainAdapterUsername(),
                clientConfig.getBlockchainAdapterPassword());
    }

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(MILLISECONDS.toMillis(100), MILLISECONDS.toMillis(100), 5) {
            @Override
            public void continueOrPropagate(RetryableException e) {
                log.info("Retrying {} {} {} {} {}", e.method(), e.getMessage(), e.getCause(), e.request().toString(), e.status());
                super.continueOrPropagate(e);
            }
        };

    }

    @Bean
    public ErrorDecoder feignErrorDecoder() {
        return new ErrorDecoder.Default() {
            @Override
            public Exception decode(String methodKey, Response response) {
                if (response.status() == HttpStatus.PROCESSING.value()) {
                    log.info("error {}", response.status());
                    return new RetryableException(response.status(), methodKey, response.request().httpMethod(), new Date(), response.request());
                }
                return super.decode(methodKey, response);
            }
        };
    }

}
