package com.iexec.core.workflow;

import com.iexec.common.replicate.ReplicateStatus;

import java.util.Arrays;
import java.util.List;

import static com.iexec.common.replicate.ReplicateStatus.*;


public class ReplicateWorkflow extends Workflow<ReplicateStatus> {

    private static ReplicateWorkflow instance;

    private ReplicateWorkflow() {
        super();

        // This is where the whole workflow is defined
        addTransition(CREATED, toList(RUNNING, RECOVERING));
        addTransition(RUNNING, toList(APP_DOWNLOADING, RECOVERING));
        addTransition(RUNNING, getCantContributeStatus());

        // app
        addTransition(APP_DOWNLOADING, toList(APP_DOWNLOADED, APP_DOWNLOAD_FAILED, RECOVERING));

        addTransition(APP_DOWNLOAD_FAILED, toList(
                // DATA_DOWNLOADING,
                CANT_CONTRIBUTE_SINCE_DETERMINISM_HASH_NOT_FOUND,
                CANT_CONTRIBUTE_SINCE_CHAIN_UNREACHABLE,
                CANT_CONTRIBUTE_SINCE_STAKE_TOO_LOW,
                CANT_CONTRIBUTE_SINCE_TASK_NOT_ACTIVE,
                CANT_CONTRIBUTE_SINCE_AFTER_DEADLINE,
                CANT_CONTRIBUTE_SINCE_CONTRIBUTION_ALREADY_SET,
                CAN_CONTRIBUTE));

        addTransition(APP_DOWNLOADED, toList(DATA_DOWNLOADING, RECOVERING));
        addTransition(APP_DOWNLOADED, getCantContributeStatus());

        // data
        addTransition(DATA_DOWNLOADING, toList(DATA_DOWNLOADED, DATA_DOWNLOAD_FAILED, RECOVERING));

        addTransition(DATA_DOWNLOAD_FAILED, toList(
                // COMPUTING,
                CANT_CONTRIBUTE_SINCE_DETERMINISM_HASH_NOT_FOUND,
                CANT_CONTRIBUTE_SINCE_CHAIN_UNREACHABLE,
                CANT_CONTRIBUTE_SINCE_STAKE_TOO_LOW,
                CANT_CONTRIBUTE_SINCE_TASK_NOT_ACTIVE,
                CANT_CONTRIBUTE_SINCE_AFTER_DEADLINE,
                CANT_CONTRIBUTE_SINCE_CONTRIBUTION_ALREADY_SET,
                CAN_CONTRIBUTE));

        addTransition(DATA_DOWNLOADED, toList(COMPUTING, RECOVERING));
        addTransition(DATA_DOWNLOADED, getCantContributeStatus());

        // computation
        addTransition(COMPUTING, toList(COMPUTED, COMPUTE_FAILED, RECOVERING));

        addTransition(COMPUTED, toList(
                CANT_CONTRIBUTE_SINCE_DETERMINISM_HASH_NOT_FOUND,
                CANT_CONTRIBUTE_SINCE_TEE_EXECUTION_NOT_VERIFIED,
                CANT_CONTRIBUTE_SINCE_CHAIN_UNREACHABLE,
                CANT_CONTRIBUTE_SINCE_STAKE_TOO_LOW,
                CANT_CONTRIBUTE_SINCE_TASK_NOT_ACTIVE,
                CANT_CONTRIBUTE_SINCE_AFTER_DEADLINE,
                CANT_CONTRIBUTE_SINCE_CONTRIBUTION_ALREADY_SET,
                CAN_CONTRIBUTE,
                RECOVERING));

        addTransition(COMPUTE_FAILED, toList(
                CANT_CONTRIBUTE_SINCE_DETERMINISM_HASH_NOT_FOUND,
                CANT_CONTRIBUTE_SINCE_TEE_EXECUTION_NOT_VERIFIED,
                CANT_CONTRIBUTE_SINCE_CHAIN_UNREACHABLE,
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

        /*
        * From to FAILED
        * From uncompletable status to generic FAILED
        */
        addTransition(getUncompletableStatuses(),FAILED);
        addTransition(Arrays.asList(
                WORKER_LOST,                    //could happen if uncompletableStatus (-> WORKER_LOST) -> FAILED
                RECOVERING                      //could happen if uncompletableStatus (-> RECOVERING) -> FAILED
        ),FAILED);

        /*
        * From to WORKER_LOST
        * From completable status to WORKER_LOST
        * from2workerLost = allCompletableStatuses - from2failed
        */
        addTransition(getCompletableStatuses(), WORKER_LOST);
        addTransition(Arrays.asList(
                //COMPLETED,                    //no WORKER_LOST after COMPLETED
                //FAILED,                       //no WORKER_LOST after FAILED
                RECOVERING                      //could happen if completableStatus (-> RECOVERING) -> WORKER_LOST
        ), WORKER_LOST);

        /*
         * TODO: From to RECOVERING
         * From completable status to RECOVERING
         */
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
