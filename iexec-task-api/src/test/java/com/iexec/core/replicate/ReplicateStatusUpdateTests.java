/*
 * Copyright 2022-2023 IEXEC BLOCKCHAIN TECH
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
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReplicateStatusUpdateTests {
    @Test
    void shouldGenerateModelFromNull() {
        assertEquals(ReplicateStatusUpdateModel.builder().build(), ReplicateStatusUpdateModel.fromEntity(null));
    }

    @Test
    void shouldGenerateModeFromNonNull() {
        final Date now = new Date();
        final ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.builder()
                .status(ReplicateStatus.COMPLETED)
                .date(now)
                .build();
        final ReplicateStatusUpdateModel expectedStatusUpdateModel = ReplicateStatusUpdateModel
                .builder()
                .status(ReplicateStatus.COMPLETED)
                .date(now)
                .build();
        assertEquals(expectedStatusUpdateModel, ReplicateStatusUpdateModel.fromEntity(statusUpdate));
    }
}
