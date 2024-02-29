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

package com.iexec.core.worker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Document
@Data
@Builder
@AllArgsConstructor
public class Worker {

    @Id
    private String id;
    private String name;

    @Indexed(unique = true)
    private String walletAddress;

    private String os;
    private String cpu;
    private int cpuNb;
    private int maxNbTasks;
    private int memorySize;
    private boolean teeEnabled;
    private boolean gpuEnabled;
    @Builder.Default
    private List<String> participatingChainTaskIds = List.of();
    @Builder.Default
    private List<String> computingChainTaskIds = List.of();

    // TODO remove it cleanly in a release
    private Date lastAliveDate;
    private Date lastReplicateDemandDate;

    void addChainTaskId(String chainTaskId) {
        participatingChainTaskIds.add(chainTaskId);
        computingChainTaskIds.add(chainTaskId);
    }

    void removeChainTaskId(String chainTaskId) {
        participatingChainTaskIds.remove(chainTaskId);
        computingChainTaskIds.remove(chainTaskId);
    }

    void removeComputedChainTaskId(String chainTaskId) {
        computingChainTaskIds.remove(chainTaskId);
    }
}
