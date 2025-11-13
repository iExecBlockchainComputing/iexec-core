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

package com.iexec.core.worker;

import com.iexec.commons.poco.tee.TeeUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Slf4j
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
    private boolean gpuEnabled;
    // TODO remove or rename to sgxEnabled in the future
    private boolean teeEnabled;
    private boolean tdxEnabled;
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

    /**
     * Returns excluded tags depending on worker configuration
     *
     * @return The list of excluded tags
     */
    public List<String> getExcludedTags() {
        if (!teeEnabled && !tdxEnabled) {
            return List.of(TeeUtils.TEE_TDX_ONLY_TAG, TeeUtils.TEE_SCONE_ONLY_TAG, TeeUtils.TEE_GRAMINE_ONLY_TAG);
        } else if (!teeEnabled) {
            return List.of(TeeUtils.TEE_SCONE_ONLY_TAG, TeeUtils.TEE_GRAMINE_ONLY_TAG);
        } else if (!tdxEnabled) {
            return List.of(TeeUtils.TEE_TDX_ONLY_TAG);
        } else {
            // /!\ teeEnabled and tdxEnabled are both true in this branch
            log.warn("Worker seems to support both SGX and TDX, this should not happen [wallet:{}]", walletAddress);
            return List.of();
        }
    }

    /**
     * Returns whether the worker can accept more work or not.
     *
     * @return {@literal true} when the worker is at max capacity, {@literal false} otherwise
     */
    public boolean hasNoRemainingComputingSlot() {
        final boolean areAllComputingSlotsInUse = computingChainTaskIds.size() >= maxNbTasks;
        if (areAllComputingSlotsInUse) {
            log.debug("Worker is computing at max capacity [walletAddress:{}, runningReplicateNb:{}, workerMaxNbTasks:{}]",
                    walletAddress, computingChainTaskIds.size(), maxNbTasks);
        }
        return areAllComputingSlotsInUse;
    }
}
