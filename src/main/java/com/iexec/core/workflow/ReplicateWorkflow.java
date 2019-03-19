package com.iexec.core.workflow;

import com.iexec.common.replicate.ReplicateStatus;

import static com.iexec.common.replicate.ReplicateStatus.*;


public class ReplicateWorkflow extends Workflow<ReplicateStatus> {

    private static ReplicateWorkflow instance;

    private ReplicateWorkflow() {
        super();

        // This is where the whole workflow is defined
        addTransition(CREATED, toList(RUNNING, RECOVERING));
        addTransition(RUNNING, toList(APP_DOWNLOADING, RECOVERING));

        // app
        addTransition(APP_DOWNLOADING, toList(APP_DOWNLOADED, APP_DOWNLOAD_FAILED, RECOVERING));

        addTransition(APP_DOWNLOAD_FAILED, toList(
                // DATA_DOWNLOADING,
                CANT_CONTRIBUTE_SINCE_STAKE_TOO_LOW,
                CANT_CONTRIBUTE_SINCE_TASK_NOT_ACTIVE,
                CANT_CONTRIBUTE_SINCE_AFTER_DEADLINE,
                CANT_CONTRIBUTE_SINCE_CONTRIBUTION_ALREADY_SET,
                CAN_CONTRIBUTE));

        addTransition(APP_DOWNLOADED, toList(DATA_DOWNLOADING, RECOVERING));

        // data
        addTransition(DATA_DOWNLOADING, toList(DATA_DOWNLOADED, DATA_DOWNLOAD_FAILED, RECOVERING));

        addTransition(DATA_DOWNLOAD_FAILED, toList(
                // COMPUTING,
                CANT_CONTRIBUTE_SINCE_STAKE_TOO_LOW,
                CANT_CONTRIBUTE_SINCE_TASK_NOT_ACTIVE,
                CANT_CONTRIBUTE_SINCE_AFTER_DEADLINE,
                CANT_CONTRIBUTE_SINCE_CONTRIBUTION_ALREADY_SET,
                CAN_CONTRIBUTE));

        addTransition(DATA_DOWNLOADED, toList(COMPUTING, RECOVERING));

        // computation
        addTransition(COMPUTING, toList(COMPUTED, COMPUTE_FAILED, RECOVERING));

        addTransition(COMPUTED, toList(
                CANT_CONTRIBUTE_SINCE_STAKE_TOO_LOW,
                CANT_CONTRIBUTE_SINCE_TASK_NOT_ACTIVE,
                CANT_CONTRIBUTE_SINCE_AFTER_DEADLINE,
                CANT_CONTRIBUTE_SINCE_CONTRIBUTION_ALREADY_SET,
                CAN_CONTRIBUTE,
                RECOVERING));

        // contribution
        addTransition(CAN_CONTRIBUTE, toList(CONTRIBUTING, OUT_OF_GAS, RECOVERING));
        addTransition(CONTRIBUTING, toList(CONTRIBUTED, CONTRIBUTE_FAILED, RECOVERING));
        addTransitionFromStatusBeforeContributedToGivenStatus(ABORTED_ON_CONTRIBUTION_TIMEOUT);
        addTransitionFromStatusBeforeContributedToGivenStatus(ABORTED_ON_CONSENSUS_REACHED);

        // reveal - completed
        addTransition(CONTRIBUTED, toList(REVEALING, CANT_REVEAL, REVEAL_TIMEOUT, OUT_OF_GAS, RECOVERING));
        addTransition(REVEALING, toList(REVEALED, REVEAL_FAILED, REVEAL_TIMEOUT, RECOVERING));
        addTransition(REVEALED, toList(RESULT_UPLOAD_REQUESTED, COMPLETED, RECOVERING));
        addTransition(RESULT_UPLOAD_REQUESTED, toList(RESULT_UPLOADING, RESULT_UPLOAD_REQUEST_FAILED, RECOVERING));
        addTransition(RESULT_UPLOADING, toList(RESULT_UPLOADED, RESULT_UPLOAD_FAILED, RECOVERING));
        addTransition(RESULT_UPLOADED, COMPLETED);

        // worker_lost
        addTransition(WORKER_LOST, toList(
                ABORTED_ON_CONSENSUS_REACHED,
                ABORTED_ON_CONTRIBUTION_TIMEOUT,
                RESULT_UPLOAD_REQUEST_FAILED,
                RESULT_UPLOAD_FAILED,
                REVEAL_TIMEOUT,
                COMPLETED,
                RECOVERING));

        // from any status to WORKER_LOST or ERROR
        addTransitionFromAllStatusesTo(WORKER_LOST);
        addTransitionFromAllStatusesTo(FAILED);

        addTransitionFromStatusToAllStatuses(RECOVERING);
        addTransition(RECOVERING, COMPLETED);
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

        addTransition(CONTRIBUTED, to);
        addTransition(OUT_OF_GAS, to);
        addTransition(WORKER_LOST, to);
    }
}
