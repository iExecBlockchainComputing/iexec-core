package com.iexec.core.workflow;

import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateStatus;

import java.util.Arrays;
import java.util.HashMap;

import static com.iexec.common.notification.TaskNotificationType.*;
import static com.iexec.common.replicate.ReplicateStatus.*;


public class ReplicateWorkflow extends Workflow<ReplicateStatus> {

    private static ReplicateWorkflow instance;

    HashMap<ReplicateStatus, TaskNotificationType> actionMap = new HashMap<>();


    private void setNextActionWhenStatus(TaskNotificationType actionTodo, ReplicateStatus whenStatus) {
        actionMap.putIfAbsent(whenStatus, actionTodo);
    }

    public TaskNotificationType getNextAction(ReplicateStatus whenStatus) {
        if (actionMap.containsKey(whenStatus)){
            return actionMap.get(whenStatus);
        }
        return null;
    }

    private void setNextActionWhenStatus(TaskNotificationType actionTodo, ReplicateStatus... whenStatuses) {
        for (ReplicateStatus whenStatus : whenStatuses){
            setNextActionWhenStatus(actionTodo, whenStatus);
        }
    }


    private ReplicateWorkflow() {
        super();
        ReplicateStatus current;
        TaskNotificationType next;

/*
        switch (current){
            case CREATED:
                next = PLEASE_START;
                break;
            case RUNNING:
            case APP_DOWNLOADING:
                next = PLEASE_DOWNLOAD_APP;
                break;
            case APP_DOWNLOADED:
            case DATA_DOWNLOADING:
                next = PLEASE_DOWNLOAD_DATA;
                break;

                default:
                break;

        }*/

actionMap.put(RUNNING, PLEASE_CONTINUE);
actionMap.put(STARTED, PLEASE_DOWNLOAD_APP);
actionMap.put(START_FAILED, PLEASE_ABORT);

actionMap.put(APP_DOWNLOADING, PLEASE_CONTINUE);
actionMap.put(APP_DOWNLOADED, PLEASE_DOWNLOAD_DATA);
actionMap.put(APP_DOWNLOAD_FAILED, PLEASE_ABORT);

actionMap.put(DATA_DOWNLOADING, PLEASE_CONTINUE);
actionMap.put(DATA_DOWNLOADED, PLEASE_COMPUTE);
actionMap.put(DATA_DOWNLOAD_FAILED, PLEASE_ABORT);

actionMap.put(COMPUTING, PLEASE_CONTINUE);
actionMap.put(COMPUTED, PLEASE_CONTRIBUTE);
actionMap.put(COMPUTE_FAILED, PLEASE_ABORT);

actionMap.put(CONTRIBUTING, PLEASE_CONTINUE);
actionMap.put(CONTRIBUTED, PLEASE_WAIT);
actionMap.put(CONTRIBUTE_FAILED, PLEASE_ABORT);

actionMap.put(REVEALING, PLEASE_CONTINUE);
actionMap.put(REVEALED, PLEASE_WAIT);
actionMap.put(REVEAL_FAILED, PLEASE_ABORT);

actionMap.put(RESULT_UPLOADING, PLEASE_CONTINUE);
actionMap.put(RESULT_UPLOADED, PLEASE_WAIT);
actionMap.put(RESULT_UPLOAD_FAILED, PLEASE_ABORT);

actionMap.put(COMPLETING, PLEASE_CONTINUE);
actionMap.put(COMPLETED, PLEASE_WAIT);
actionMap.put(COMPLETE_FAILED, PLEASE_ABORT);


//setNextActionWhenStatus(PLEASE_WAIT, );
        /*
setNextActionWhenStatus(PLEASE_START, CREATED);
setNextActionWhenStatus(PLEASE_DOWNLOAD_APP, STARTED);//, APP_DOWNLOADING);
setNextActionWhenStatus(PLEASE_DOWNLOAD_DATA, APP_DOWNLOADED);//, DATA_DOWNLOADING);
setNextActionWhenStatus(PLEASE_COMPUTE, DATA_DOWNLOADED);//, COMPUTING);
setNextActionWhenStatus(PLEASE_CONTRIBUTE, COMPUTED);//, COMPUTING);
setNextActionWhenStatus(PLEASE_REVEAL, CONTRIBUTED);//, REVEALING);
setNextActionWhenStatus(PLEASE_WAIT, REVEALED);
setNextActionWhenStatus(PLEASE_UPLOAD, RESULT_UPLOAD_REQUESTED);//, RESULT_UPLOADING);
setNextActionWhenStatus(PLEASE_ABORT, APP_DOWNLOAD_FAILED, DATA_DOWNLOAD_FAILED, COMPUTE_FAILED, CONTRIBUTE_FAILED, REVEAL_FAILED, RESULT_UPLOAD_FAILED);
setNextActionWhenStatus(PLEASE_CONTINUE, APP_DOWNLOADING, DATA_DOWNLOADING, COMPUTING, COMPUTING, REVEALING);
*/
/*
setNextActionWhenStatus(PLEASE_ABORT_CONSENSUS_REACHED,);
setNextActionWhenStatus(PLEASE_ABORT_CONTRIBUTION_TIMEOUT,);
setNextActionWhenStatus(PLEASE_COMPLETE);
*/





        /*

        setNextActionWhenStatus(PLEASE_START,xœ
                CREATED);
        setNextActionWhenStatus(PLEASE_DOWNLOAD_APP,
                RUNNING,
                APP_DOWNLOADING);
        doActionWhen()

setNextAction(CREATED, PLEASE_START);
setNextAction(RUNNING, PLEASE_DOWNLOAD_APP);
setNextAction(APP_DOWNLOADING,PLEASE_DOWNLOAD_APP);
setNextAction(APP_DOWNLOADED,PLEASE_DOWNLOAD_DATA);
setNextAction(DATA_DOWNLOADING,PLEASE_DOWNLOAD_DATA);
setNextAction(DATA_DOWNLOADED,PLEASE_COMPUTE);
setNextAction(COMPUTING,PLEASE_COMPUTE);
setNextAction(COMPUTED,PLEASE_CONTRIBUTE);
setNextAction(CAN_CONTRIBUTE,PLEASE_START);
setNextAction(CONTRIBUTING,PLEASE_START);
setNextAction(CONTRIBUTED,PLEASE_START);
setNextAction(REVEALING,PLEASE_START);
setNextAction(REVEALED,PLEASE_START);
setNextAction(RESULT_UPLOAD_REQUESTED,PLEASE_START);
setNextAction(RESULT_UPLOAD_REQUEST_FAILED,PLEASE_START);
setNextAction(RESULT_UPLOADING,PLEASE_START);
setNextAction(RESULT_UPLOADED,PLEASE_START);
setNextAction(RESULT_UPLOAD_FAILED,PLEASE_START);
        setNextAction();


*/



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
        addTransition(REVEALED, toList(RESULT_UPLOAD_REQUESTED, COMPLETED, RECOVERING));
        addTransition(RESULT_UPLOAD_REQUESTED, toList(RESULT_UPLOADING, RESULT_UPLOAD_REQUEST_FAILED, RECOVERING));
        addTransition(RESULT_UPLOADING, toList(RESULT_UPLOADED, RESULT_UPLOAD_FAILED, RECOVERING));

        addTransition(RESULT_UPLOADED, COMPLETING);
        addTransition(COMPLETING, toList(COMPLETED, COMPLETE_FAILED));

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
