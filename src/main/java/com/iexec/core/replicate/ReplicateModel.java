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

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplicateModel {

    private String self;
    private String chainTaskId;
    private String walletAddress;
    private ReplicateStatus currentStatus;
    //TODO: Move/extract details here instead of encapsulating them within status updates
    private List<ReplicateStatusUpdate> statusUpdateList;
    private String resultLink;
    private String chainCallbackData;
    private String contributionHash;
    private String appStdout;

    public static ReplicateModel fromEntity(Replicate entity) {
        return ReplicateModel.builder()
                .chainTaskId(entity.getChainTaskId())
                .walletAddress(entity.getWalletAddress())
                .currentStatus(entity.getCurrentStatus())
                .statusUpdateList(entity.getStatusUpdateList())
                .resultLink(entity.getResultLink())
                .chainCallbackData(entity.getChainCallbackData())
                .contributionHash(entity.getContributionHash())
                .build();
    }
}
