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

package com.iexec.core.configuration;

import lombok.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Setter
@Component
public class WorkerConfiguration {

    @Value("${workers.askForReplicatePeriod}")
    private long askForReplicatePeriod;

    @Value("${workers.requiredWorkerVersion}")
    private String requiredWorkerVersion;

    @Value("${workers.whitelist}")
    private String[] whitelist;

    // getters are overridden since the whitelist should return a list, not an array
    public long getAskForReplicatePeriod() {
        return askForReplicatePeriod;
    }

    public String getRequiredWorkerVersion() {
        return requiredWorkerVersion;
    }

    public List<String> getWhitelist() {
        return Arrays.asList(whitelist);
    }
}
