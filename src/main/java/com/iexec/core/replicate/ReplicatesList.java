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

package com.iexec.core.replicate;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
@Document
@NoArgsConstructor
public class ReplicatesList {

    @Id
    private String id;

    @Version
    private Long version;

    @Indexed(unique = true)
    private String chainTaskId;

    private List<Replicate> replicates;

    ReplicatesList(String chainTaskId) {
        this.chainTaskId = chainTaskId;
        this.replicates = new ArrayList<>();
    }

    ReplicatesList(String chainTaskId, List<Replicate> replicates) {
        this.chainTaskId = chainTaskId;
        this.replicates = replicates;
    }

    public Optional<Replicate> getReplicateOfWorker(String workerWalletAddress) {
        for (Replicate replicate : replicates) {
            if (replicate.getWalletAddress().equals(workerWalletAddress)) {
                return Optional.of(replicate);
            }
        }
        return Optional.empty();
    }
}
