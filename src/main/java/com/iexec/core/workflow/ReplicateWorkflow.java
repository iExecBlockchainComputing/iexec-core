package com.iexec.core.workflow;

import com.iexec.common.replicate.ReplicateStatus;

import static com.iexec.common.replicate.ReplicateStatus.*;

public class ReplicateWorkflow extends Workflow<ReplicateStatus> {

    private static ReplicateWorkflow instance;

    private ReplicateWorkflow() {
        super();

        // This is where the whole workflow is defined
        addTransition(CREATED, RUNNING);
        addTransition(RUNNING, APP_DOWNLOADING);
        addTransition(APP_DOWNLOADING, APP_DOWNLOADED);
        addTransition(APP_DOWNLOADING, APP_DOWNLOAD_FAILED);
        addTransition(APP_DOWNLOADED, COMPUTING);
        addTransition(APP_DOWNLOAD_FAILED, COMPUTING);
        addTransition(COMPUTING, COMPUTED);
        addTransition(COMPUTED, CONTRIBUTING);

        addTransition(CONTRIBUTING, CONTRIBUTED);
        addTransition(CONTRIBUTING, CONTRIBUTE_FAILED);
        addTransition(CONTRIBUTE_FAILED, ABORT_CONSENSUS_REACHED);
        addTransition(CONTRIBUTED, REVEALING);
        addTransition(REVEALING, REVEALED);
        addTransition(REVEALING, REVEAL_FAILED);
        addTransition(REVEALED, RESULT_UPLOADING);
        addTransition(RESULT_UPLOADING, RESULT_UPLOADED);
        addTransition(RESULT_UPLOADING, RESULT_UPLOAD_REQUEST_FAILED);
        addTransition(RESULT_UPLOADING, ERROR);
        addTransition(RESULT_UPLOADED, COMPLETED);
        addTransition(COMPUTED, ERROR);

        // cases after error
        addTransition(ERROR, ABORT_CONSENSUS_REACHED);

        // from any status to WORKER_LOST
        addTransition(CREATED, WORKER_LOST);
        addTransition(RUNNING, WORKER_LOST);
        addTransition(COMPUTED, WORKER_LOST);
        addTransition(CONTRIBUTED, WORKER_LOST);
        addTransition(REVEALED, WORKER_LOST);
        addTransition(RESULT_UPLOADING, WORKER_LOST);
        addTransition(RESULT_UPLOADED, WORKER_LOST);
        addTransition(RESULT_UPLOAD_REQUEST_FAILED, WORKER_LOST);
    }

    public static synchronized ReplicateWorkflow getInstance() {
        if (instance == null) {
            instance = new ReplicateWorkflow();
        }
        return instance;
    }
}
