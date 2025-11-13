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

package com.iexec.core.worker;

import com.iexec.commons.poco.tee.TeeUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerTests {
    private static final String WORKER = "0x1a69b2eb604db8eba185df03ea4f5288dcbbd248";

    private Worker getDummyWorker(final int cpuNb, final List<String> participatingIds, final List<String> computingIds) {
        return Worker.builder()
                .walletAddress(WORKER)
                .cpuNb(cpuNb)
                .maxNbTasks(cpuNb)
                .gpuEnabled(false)
                .participatingChainTaskIds(participatingIds)
                .computingChainTaskIds(computingIds)
                .build();
    }

    // region getExcludedTag
    @Test
    void shouldExcludeAllTagsForStandardWorker() {
        final Worker worker = Worker.builder().build();
        assertThat(worker.getExcludedTags())
                .containsExactly(TeeUtils.TEE_TDX_ONLY_TAG, TeeUtils.TEE_SCONE_ONLY_TAG, TeeUtils.TEE_GRAMINE_ONLY_TAG);
    }

    @Test
    void shouldExcludeTdxTagForSgxWorker() {
        final Worker worker = Worker.builder().teeEnabled(true).build();
        assertThat(worker.getExcludedTags())
                .containsExactly(TeeUtils.TEE_TDX_ONLY_TAG);
    }

    @Test
    void shouldExcludeSgxTagsForTdxWorker() {
        final Worker worker = Worker.builder().tdxEnabled(true).build();
        assertThat(worker.getExcludedTags())
                .containsExactly(TeeUtils.TEE_SCONE_ONLY_TAG, TeeUtils.TEE_GRAMINE_ONLY_TAG);
    }

    @Test
    void shouldExcludeNoTag() {
        final Worker worker = Worker.builder().teeEnabled(true).tdxEnabled(true).build();
        assertThat(worker.getExcludedTags()).isEmpty();
    }
    // endregion

    // region hasRemainingComputingSlot
    @Test
    void shouldHaveRemainingComputingSlot() {
        final Worker worker = getDummyWorker(
                3,
                List.of("task1", "task2", "task3", "task4", "task5"),
                List.of("task1", "task3"));
        assertThat(worker.hasNoRemainingComputingSlot()).isFalse();
    }

    @Test
    void shouldNotHaveRemainingComputingSlot() {
        final Worker worker = getDummyWorker(
                2,
                List.of("task1", "task2", "task3", "task4"),
                List.of("task1", "task3"));
        assertThat(worker.hasNoRemainingComputingSlot()).isTrue();
    }
    // endregion
}
