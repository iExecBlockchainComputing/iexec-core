package com.iexec.core.task;

public enum TaskStatus {
    CREATED, RUNNING, COMPUTED, CONTRIBUTED, AT_LEAST_ONE_REVEALED, REVEALED, UPLOAD_RESULT_REQUESTED, UPLOADING_RESULT, RESULT_UPLOADED, FINALIZED, COMPLETED, ERROR;

    public static boolean isBlockchainStatus(TaskStatus status) {
        return status.equals(TaskStatus.CONTRIBUTED)
                || status.equals(TaskStatus.REVEALED)
                || status.equals(TaskStatus.FINALIZED);
    }
}
