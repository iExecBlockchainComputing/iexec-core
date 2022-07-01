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

package com.iexec.core.logs;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.iexec.common.replicate.ComputeLogs;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskLogs {

    @Id
    @JsonIgnore
    private String id;

    @Version
    @JsonIgnore
    private Long version;

    @Indexed(unique = true)
    private String chainTaskId;

    private List<ComputeLogs> computeLogsList;

    public TaskLogs(String chainTaskId) {
        this.chainTaskId = chainTaskId;
        this.computeLogsList = new ArrayList<>();
    }

    public TaskLogs(String chainTaskId, List<ComputeLogs> computeLogsList) {
        this.chainTaskId = chainTaskId;
        this.computeLogsList = computeLogsList;
    }

    public boolean containsWalletAddress(String walletAddress) {
        return computeLogsList.stream().anyMatch(
            computeLog -> computeLog.getWalletAddress().equals(walletAddress)
        );
    }
}
