/*
 * Copyright 2023-2023 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.task;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskStatusTests {

    // region contribution phase
    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"INITIALIZED", "RUNNING"})
    void isInContributionPhase(TaskStatus taskStatus) {
        assertTrue(TaskStatus.isInContributionPhase(taskStatus));
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"INITIALIZED", "RUNNING"}, mode = EnumSource.Mode.EXCLUDE)
    void isNotInContributionPhase(TaskStatus taskStatus) {
        assertFalse(TaskStatus.isInContributionPhase(taskStatus));
    }
    // endregion

    // region reveal phase
    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"CONSENSUS_REACHED", "AT_LEAST_ONE_REVEALED", "RESULT_UPLOADING", "RESULT_UPLOADED"})
    void isInRevealPhase(TaskStatus taskStatus) {
        assertTrue(TaskStatus.isInRevealPhase(taskStatus));
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"CONSENSUS_REACHED", "AT_LEAST_ONE_REVEALED", "RESULT_UPLOADING", "RESULT_UPLOADED"}, mode = EnumSource.Mode.EXCLUDE)
    void isNotInRevealPhase(TaskStatus taskStatus) {
        assertFalse(TaskStatus.isInRevealPhase(taskStatus));
    }
    // endregion

    // region result upload phase
    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"RESULT_UPLOADING"})
    void isInResultUploadPhase(TaskStatus taskStatus) {
        assertTrue(TaskStatus.isInResultUploadPhase(taskStatus));
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"RESULT_UPLOADING"}, mode = EnumSource.Mode.EXCLUDE)
    void isNotInResultUploadPhase(TaskStatus taskStatus) {
        assertFalse(TaskStatus.isInResultUploadPhase(taskStatus));
    }
    // endregion

    // region completion phase
    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"FINALIZING", "FINALIZED", "COMPLETED"})
    void isInCompletionPhase(TaskStatus taskStatus) {
        assertTrue(TaskStatus.isInCompletionPhase(taskStatus));
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"FINALIZING", "FINALIZED", "COMPLETED"}, mode = EnumSource.Mode.EXCLUDE)
    void isNotInCompletionPhase(TaskStatus taskStatus) {
        assertFalse(TaskStatus.isInCompletionPhase(taskStatus));
    }
    // endregion

    // region
    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"COMPLETED", "FAILED"})
    void isFinalStatus(TaskStatus taskStatus) {
        assertTrue(TaskStatus.isFinalStatus(taskStatus));
    }

    @ParameterizedTest
    @EnumSource(value = TaskStatus.class, names = {"COMPLETED", "FAILED"}, mode = EnumSource.Mode.EXCLUDE)
    void isNotFinalStatus(TaskStatus taskStatus) {
        assertFalse(TaskStatus.isFinalStatus(taskStatus));
    }
    // endregion

}
