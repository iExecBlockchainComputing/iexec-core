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

package com.iexec.core.task;

import java.util.Arrays;
import java.util.List;

public enum TaskStatus {
    RECEIVED,
    INITIALIZING,
    INITIALIZED,
    INITIALIZE_FAILED,
    RUNNING,
    CONTRIBUTION_TIMEOUT,
    CONSENSUS_REACHED,
    REOPENING,
    REOPENED,
    REOPEN_FAILED,
    AT_LEAST_ONE_REVEALED,
    RESULT_UPLOAD_REQUESTED,
    RESULT_UPLOAD_REQUEST_TIMEOUT,
    RESULT_UPLOADING,
    RESULT_UPLOADED,
    RESULT_UPLOAD_TIMEOUT,
    FINALIZING,
    FINALIZED,
    FINALIZE_FAILED,
    COMPLETED,
    FAILED;

    public static List<TaskStatus> getWaitingContributionStatuses() {
        return Arrays.asList(
                //RECEIVED,
                //INITIALIZING, -> contribution stage is only after INITIALIZED
                INITIALIZED,
                RUNNING
        );
    }

    public static List<TaskStatus> getWaitingRevealStatuses() {
        return Arrays.asList(
            CONSENSUS_REACHED,
            AT_LEAST_ONE_REVEALED,
            RESULT_UPLOAD_REQUESTED,
            RESULT_UPLOADING,
            RESULT_UPLOADED
        );
    }

    public static boolean isInContributionPhase(TaskStatus status) {
        return getWaitingContributionStatuses().contains(status);
    }

    public static boolean isInRevealPhase(TaskStatus status) {
        return getWaitingRevealStatuses().contains(status);
    }

    public static boolean isInResultUploadPhase(TaskStatus status) {
        return Arrays.asList(
            RESULT_UPLOAD_REQUESTED,
            RESULT_UPLOADING
        ).contains(status);
    }

    public static boolean isInCompletionPhase(TaskStatus status) {
        return Arrays.asList(
            FINALIZING,
            FINALIZED,
            COMPLETED
        ).contains(status);
    }

}
