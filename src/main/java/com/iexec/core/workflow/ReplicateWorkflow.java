package com.iexec.core.workflow;

import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateStatus;

import static com.iexec.common.notification.TaskNotificationType.*;
import static com.iexec.common.replicate.ReplicateStatus.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;


public class ReplicateWorkflow extends Workflow<ReplicateStatus> {

    private static ReplicateWorkflow instance;
    private Map<ReplicateStatus, TaskNotificationType> actionMap = new LinkedHashMap<>();

    private ReplicateWorkflow() {
        super();
        setTransitions();
        setNextActions();
    }

    public static synchronized ReplicateWorkflow getInstance() {
        if (instance == null) {
            instance = new ReplicateWorkflow();
        }
        return instance;
    }

    private void setTransitions() {
        // This is where the whole workflow is defined
        addTransition(CREATED, toList(RUNNING, RECOVERING));

        addTransition(RUNNING, toList(STARTED, START_FAILED, RECOVERING));

        addTransition(STARTED, toList(APP_DOWNLOADING, RECOVERING));

        addTransition(RUNNING, CANT_CONTRIBUTE);

        // app
        addTransition(APP_DOWNLOADING, toList(APP_DOWNLOADED, APP_DOWNLOAD_FAILED, RECOVERING));

        addTransition(APP_DOWNLOAD_FAILED, toList(
                // DATA_DOWNLOADING,
                CANT_CONTRIBUTE,
                CAN_CONTRIBUTE));

        addTransition(APP_DOWNLOADED, toList(DATA_DOWNLOADING, RECOVERING));
        addTransition(APP_DOWNLOADED, CANT_CONTRIBUTE);

        // data
        addTransition(DATA_DOWNLOADING, toList(DATA_DOWNLOADED, DATA_DOWNLOAD_FAILED, RECOVERING));

        addTransition(DATA_DOWNLOAD_FAILED, toList(
                // COMPUTING,
                CANT_CONTRIBUTE,
                CAN_CONTRIBUTE));

        addTransition(DATA_DOWNLOADED, toList(COMPUTING, RECOVERING));
        addTransition(DATA_DOWNLOADED, CANT_CONTRIBUTE);

        // computation
        addTransition(COMPUTING, toList(COMPUTED, COMPUTE_FAILED, RECOVERING));

        addTransition(COMPUTED, toList(
                //CANT_CONTRIBUTE,
                //CAN_CONTRIBUTE,
                CONTRIBUTING,
                RECOVERING));

        addTransition(COMPUTE_FAILED, toList(
                CANT_CONTRIBUTE,
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
        addTransition(REVEALED, toList(RESULT_UPLOAD_REQUESTED, COMPLETING, RECOVERING));
        addTransition(RESULT_UPLOAD_REQUESTED, toList(RESULT_UPLOADING, RESULT_UPLOAD_REQUEST_FAILED, RECOVERING));
        addTransition(RESULT_UPLOADING, toList(RESULT_UPLOADED, RESULT_UPLOAD_FAILED, RECOVERING));

        addTransition(RESULT_UPLOADED, toList(COMPLETING, RECOVERING));
        addTransition(COMPLETING, toList(COMPLETED, COMPLETE_FAILED, RECOVERING));

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

    private void addTransitionFromStatusBeforeContributedToGivenStatus(ReplicateStatus to) {
        for (ReplicateStatus from : getStatusesBeforeContributed()) {
            addTransition(from, to);
        }

        addTransition(CONTRIBUTED, to);
        addTransition(OUT_OF_GAS, to);
        addTransition(WORKER_LOST, to);
    }

    private void setNextAction(ReplicateStatus whenStatus, TaskNotificationType nextAction) {
        actionMap.putIfAbsent(whenStatus, nextAction);
    }

    public TaskNotificationType getNextAction(ReplicateStatus whenStatus) {
        if (actionMap.containsKey(whenStatus)){
            return actionMap.get(whenStatus);
        }
        return null;
    }

    private void setNextActions() {
        setNextAction(RUNNING, PLEASE_CONTINUE);
        setNextAction(STARTED, PLEASE_DOWNLOAD_APP);
        setNextAction(START_FAILED, PLEASE_ABORT);

        setNextAction(APP_DOWNLOADING, PLEASE_CONTINUE);
        setNextAction(APP_DOWNLOADED, PLEASE_DOWNLOAD_DATA);
        setNextAction(APP_DOWNLOAD_FAILED, PLEASE_ABORT);

        setNextAction(DATA_DOWNLOADING, PLEASE_CONTINUE);
        setNextAction(DATA_DOWNLOADED, PLEASE_COMPUTE);
        setNextAction(DATA_DOWNLOAD_FAILED, PLEASE_ABORT);

        setNextAction(COMPUTING, PLEASE_CONTINUE);
        setNextAction(COMPUTED, PLEASE_CONTRIBUTE);
        setNextAction(COMPUTE_FAILED, PLEASE_ABORT);

        setNextAction(CONTRIBUTING, PLEASE_CONTINUE);
        setNextAction(CONTRIBUTED, PLEASE_WAIT);
        setNextAction(CONTRIBUTE_FAILED, PLEASE_ABORT);

        setNextAction(REVEALING, PLEASE_CONTINUE);
        setNextAction(REVEALED, PLEASE_WAIT);
        setNextAction(REVEAL_FAILED, PLEASE_ABORT);

        setNextAction(RESULT_UPLOADING, PLEASE_CONTINUE);
        setNextAction(RESULT_UPLOADED, PLEASE_WAIT);
        setNextAction(RESULT_UPLOAD_FAILED, PLEASE_ABORT);

        setNextAction(COMPLETING, PLEASE_CONTINUE);
        setNextAction(COMPLETED, PLEASE_WAIT);
        setNextAction(COMPLETE_FAILED, PLEASE_ABORT);
    }

    /*
     * Use this to update the json files when transitions
     * or actions are changed. 
     */
    public static void main(String[] args) throws Exception {
        String transitionsFilePath = "src/main/java/com/iexec/core/workflow/replicate-transitions.json";
        String actionsFilePath = "src/main/java/com/iexec/core/workflow/replicate-actions.json";
        ReplicateWorkflow rw = ReplicateWorkflow.getInstance();
        
        rw.saveWorkflowAsJsonFile(transitionsFilePath, rw.getTransitions());
        rw.saveWorkflowAsJsonFile(actionsFilePath, rw.actionMap);
    }
}
