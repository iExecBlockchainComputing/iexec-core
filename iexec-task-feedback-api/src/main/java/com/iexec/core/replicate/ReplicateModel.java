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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iexec.common.replicate.ReplicateStatus;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
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
}
