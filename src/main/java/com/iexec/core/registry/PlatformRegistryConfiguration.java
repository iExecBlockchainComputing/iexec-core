/*
 * Copyright 2022-2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.registry;

import jakarta.validation.constraints.NotNull;
import lombok.Value;
import org.hibernate.validator.constraints.URL;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Value
@Validated
@ConfigurationProperties(prefix = "sms")
public class PlatformRegistryConfiguration {
    @URL
    @NotNull
    String scone;

    @URL
    @NotNull
    String gramine;

    @URL
    @NotNull
    String tdx;

    public PlatformRegistryConfiguration(
            @DefaultValue("") final String scone,
            @DefaultValue("") final String gramine,
            @DefaultValue("") final String tdx
    ) {
        this.scone = scone;
        this.gramine = gramine;
        this.tdx = tdx;
    }
}
