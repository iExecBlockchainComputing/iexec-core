/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core;

import java.util.concurrent.Executor;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableRetry
@EnableScheduling
@EnableFeignClients
@EnableAsync
public class AppConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**");
    }

    /**
     * We need this bean with the name "taskExecutor"
     * to help spring choose an executor for
     * {@link org.springframework.scheduling.annotation.Async}
     * tasks.
     * <p>
     * Resolves the message:
     * "More than one TaskExecutor bean found
     * within the context, and none is named 'taskExecutor'.
     * Mark one of them as primary or name it 'taskExecutor'
     * (possibly as an alias) in order to use it for async
     * processing."
     * 
     * @return
     */
    @Bean
    public Executor taskExecutor() {
        return new SimpleAsyncTaskExecutor();
    }
}