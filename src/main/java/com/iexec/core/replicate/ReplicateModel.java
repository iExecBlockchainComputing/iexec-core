/*
 * Copyright 2022 IEXEC BLOCKCHAIN TECH
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
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import lombok.*;

import java.util.List;
import java.util.stream.Collectors;

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
    private List<ReplicateStatusUpdateModel> statusUpdateList;
    private String resultLink;
    private String chainCallbackData;
    private String contributionHash;
    private String appLogs;
    private Integer appExitCode; //null means unset
    private String teeSessionGenerationError; // null means unset

    public static ReplicateModel fromEntity(Replicate entity) {
        final List<ReplicateStatusUpdateModel> statusUpdateList =
                entity.getStatusUpdateList()
                        .stream()
                        .map(ReplicateStatusUpdateModel::fromEntity)
                        .collect(Collectors.toList());

        Integer appExitCode = null;
        String teeSessionGenerationError = null;
        for (ReplicateStatusUpdate replicateStatusUpdate : entity.getStatusUpdateList()) {
            ReplicateStatusDetails details = replicateStatusUpdate.getDetails();
            if (details != null) {
                if (appExitCode == null) {
                    appExitCode = details.getExitCode();
                }
                if (teeSessionGenerationError == null) {
                    teeSessionGenerationError = details.getTeeSessionGenerationError();
                }
                if (appExitCode != null && teeSessionGenerationError != null) {
                    break;
                }
            }
        }

        return ReplicateModel.builder()
                .chainTaskId(entity.getChainTaskId())
                .walletAddress(entity.getWalletAddress())
                .currentStatus(entity.getCurrentStatus())
                .statusUpdateList(statusUpdateList)
                .resultLink(entity.getResultLink())
                .chainCallbackData(entity.getChainCallbackData())
                .contributionHash(entity.getContributionHash())
                .appExitCode(appExitCode)
                .teeSessionGenerationError(teeSessionGenerationError)
                .build();
    }
}
