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
        addTransition(COMPUTED, CANT_CONTRIBUTE);
        addTransition(COMPUTED, OUT_OF_GAS);
        addTransition(COMPUTED, CONTRIBUTING);

        addTransition(CONTRIBUTING, CONTRIBUTED);
        addTransition(CONTRIBUTING, CONTRIBUTE_FAILED);

        addTransitionFromStatusBeforeContributedToGivenStatus(ABORTED_ON_CONTRIBUTION_TIMEOUT);
        addTransition(CONTRIBUTED, ABORTED_ON_CONTRIBUTION_TIMEOUT);
        addTransition(OUT_OF_GAS, ABORTED_ON_CONTRIBUTION_TIMEOUT);
        addTransitionFromStatusBeforeContributedToGivenStatus(ABORTED_ON_CONSENSUS_REACHED);
        addTransition(CONTRIBUTED, ABORTED_ON_CONSENSUS_REACHED);
        addTransition(OUT_OF_GAS, ABORTED_ON_CONSENSUS_REACHED);

        addTransition(CONTRIBUTED, CANT_REVEAL);
        addTransition(CONTRIBUTED, OUT_OF_GAS);
        addTransition(CONTRIBUTED, REVEALING);
        addTransition(CONTRIBUTED, REVEAL_TIMEOUT);
        addTransition(REVEALING, REVEAL_TIMEOUT);
        addTransition(REVEALING, REVEALED);
        addTransition(REVEALING, REVEAL_FAILED);
        addTransition(REVEALED, RESULT_UPLOADING);
        addTransition(REVEALED, COMPLETED);
        addTransition(RESULT_UPLOADING, RESULT_UPLOADED);
        addTransition(RESULT_UPLOADING, RESULT_UPLOAD_REQUEST_FAILED);
        addTransition(WORKER_LOST, RESULT_UPLOAD_REQUEST_FAILED);
        addTransition(RESULT_UPLOADED, COMPLETED);

        // from any status to WORKER_LOST or ERROR
        addTransitionToAllStatus(WORKER_LOST);
        addTransitionToAllStatus(ERROR);


    }

    public static synchronized ReplicateWorkflow getInstance() {
        if (instance == null) {
            instance = new ReplicateWorkflow();
        }
        return instance;
    }

    private void addTransitionFromStatusBeforeContributedToGivenStatus(ReplicateStatus to) {
        for (ReplicateStatus from : getStatusesBeforeContributed()) {
            addTransition(from, to);
        }
    }

    private void addTransitionToAllStatus(ReplicateStatus status) {
        addTransition(CREATED, status);
        addTransition(RUNNING, status);
        addTransition(APP_DOWNLOADING, status);
        addTransition(APP_DOWNLOADED, status);
        addTransition(APP_DOWNLOAD_FAILED, status);
        addTransition(COMPUTING, status);
        addTransition(COMPUTED, status);
        addTransition(CONTRIBUTING, status);
        addTransition(CANT_CONTRIBUTE, status);
        addTransition(CONTRIBUTED, status);
        addTransition(CONTRIBUTE_FAILED, status);
        addTransition(REVEALING, status);
        addTransition(REVEALED, status);
        addTransition(RESULT_UPLOADING, status);
        addTransition(RESULT_UPLOADED, status);
        addTransition(RESULT_UPLOAD_REQUEST_FAILED, status);
        addTransition(COMPLETED, status);
        addTransition(ABORTED_ON_CONTRIBUTION_TIMEOUT, status);
        addTransition(ABORTED_ON_CONSENSUS_REACHED, status);
        addTransition(OUT_OF_GAS, status);
    }
}
