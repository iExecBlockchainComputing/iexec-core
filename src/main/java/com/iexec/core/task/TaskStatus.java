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
    REVEALED,
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

    public static List<TaskStatus> getWaitingRevealStatuses() {
        return Arrays.asList(
                CONSENSUS_REACHED,
                AT_LEAST_ONE_REVEALED,
                RESULT_UPLOAD_REQUESTED,
                RESULT_UPLOADING,
                RESULT_UPLOADED
        );
    }
}
