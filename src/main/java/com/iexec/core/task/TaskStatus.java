package com.iexec.core.task;

public enum TaskStatus {
    CREATED, RUNNING, COMPUTED, CONSENSUS_REACHED, AT_LEAST_ONE_REVEALED, REVEALED, UPLOAD_RESULT_REQUESTED,
    UPLOADING_RESULT, RESULT_UPLOADED, FINALIZE_STARTED, FINALIZE_COMPLETED, FINALIZE_FAILED, COMPLETED, ERROR;

    public static boolean isBlockchainStatus(TaskStatus status) {
        return status.equals(TaskStatus.CONSENSUS_REACHED)
                || status.equals(TaskStatus.REVEALED)
                || status.equals(TaskStatus.FINALIZE_COMPLETED);
    }
}
