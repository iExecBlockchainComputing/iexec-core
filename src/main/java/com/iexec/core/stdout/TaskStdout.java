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

package com.iexec.core.stdout;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

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
public class TaskStdout {

    @Id
    @JsonIgnore
    private String id;

    @Version
    @JsonIgnore
    private Long version;

    @Indexed(unique = true)
    private String chainTaskId;

    private List<ReplicateStdout> replicateStdoutList;

    public TaskStdout(String chainTaskId) {
        this.chainTaskId = chainTaskId;
        this.replicateStdoutList = new ArrayList<>();
    }

    public TaskStdout(String chainTaskId, List<ReplicateStdout> replicateStdoutList) {
        this.chainTaskId = chainTaskId;
        this.replicateStdoutList = replicateStdoutList;
    }

    public boolean containsWalletAddress(String walletAddress) {
        return replicateStdoutList.stream().anyMatch(
            replicateStdout -> replicateStdout.getWalletAddress().equals(walletAddress)
        );
    }
}
