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
import com.iexec.common.replicate.ReplicateStatusCause;
import com.iexec.common.replicate.ReplicateStatusDetails;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReplicateStatusUpdateModel {
    private ReplicateStatus status;
    private Date date;
    private ReplicateStatusCause cause;

    public static ReplicateStatusUpdateModel fromEntity(ReplicateStatusUpdate update) {
        if (update == null) {
            return new ReplicateStatusUpdateModel();
        }

        final ReplicateStatusDetails details = update.getDetails();
        return ReplicateStatusUpdateModel.builder()
                .status(update.getStatus())
                .date(update.getDate())
                .cause(details == null ? null : details.getCause())
                .build();
    }
}
