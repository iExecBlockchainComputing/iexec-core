/*
 * Copyright 2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.notification;

import com.iexec.common.replicate.ReplicateStatusCause;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskAbortCauseTests {
    @Test
    void shouldConvertToReplicateStatusCause() {
        assertThat(TaskAbortCause.CONSENSUS_REACHED.toReplicateStatusCause())
                .isEqualTo(ReplicateStatusCause.CONSENSUS_REACHED);
        assertThat(TaskAbortCause.CONTRIBUTION_TIMEOUT.toReplicateStatusCause())
                .isEqualTo(ReplicateStatusCause.CONTRIBUTION_TIMEOUT);
        assertThat(TaskAbortCause.UNKNOWN.toReplicateStatusCause())
                .isEqualTo(ReplicateStatusCause.UNKNOWN);
    }
}
