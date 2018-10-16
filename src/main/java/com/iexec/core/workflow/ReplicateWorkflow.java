package com.iexec.core.workflow;

import com.iexec.common.replicate.ReplicateStatus;

import static com.iexec.common.replicate.ReplicateStatus.*;

public class ReplicateWorkflow extends Workflow<ReplicateStatus> {

    private static ReplicateWorkflow instance;

    private ReplicateWorkflow() {
        super();

        // This is where the whole workflow is defined
        addTransition(CREATED, RUNNING);
        addTransition(RUNNING, COMPUTED);
        addTransition(COMPUTED, UPLOADING_RESULT);
        addTransition(UPLOADING_RESULT, RESULT_UPLOADED);
        addTransition(UPLOADING_RESULT, UPLOAD_RESULT_REQUEST_FAILED);
        addTransition(UPLOADING_RESULT, ERROR);
        //from any status to WORKER_LOST
        addTransition(CREATED, WORKER_LOST);
        addTransition(RUNNING, WORKER_LOST);
        addTransition(COMPUTED, WORKER_LOST);
        addTransition(UPLOADING_RESULT, WORKER_LOST);
        addTransition(RESULT_UPLOADED, WORKER_LOST);
        addTransition(UPLOAD_RESULT_REQUEST_FAILED, WORKER_LOST);
    }

    public static synchronized ReplicateWorkflow getInstance() {
        if (instance == null) {
            instance = new ReplicateWorkflow();
        }
        return instance;
    }
}
