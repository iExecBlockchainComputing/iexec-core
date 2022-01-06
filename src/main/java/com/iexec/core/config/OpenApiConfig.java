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

package com.iexec.core.config;

import com.iexec.core.utils.version.VersionService;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private final VersionService versionService;

    public OpenApiConfig(VersionService versionService) {
        this.versionService = versionService;
    }

    /*
     * Swagger URI: /swagger-ui/index.html
     */
    @Bean
    public OpenAPI api() {
        return new OpenAPI().info(
                new Info()
                        .title("iExec Core Scheduler")
                        .version(versionService.getVersion())
        );
    }
}
