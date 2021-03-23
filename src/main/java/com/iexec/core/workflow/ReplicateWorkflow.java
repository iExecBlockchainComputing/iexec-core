/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.core.workflow;

import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;

import static com.iexec.common.notification.TaskNotificationType.*;
import static com.iexec.common.replicate.ReplicateStatus.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class ReplicateWorkflow extends Workflow<ReplicateStatus> {

    private static ReplicateWorkflow instance;
    private final Map<ReplicateStatus, TaskNotificationType> actionMap = new LinkedHashMap<>();

    private ReplicateWorkflow() {
        super();
        setWorkflowTransitions();
        setNextActions();
    }

    public static synchronized ReplicateWorkflow getInstance() {
        if (instance == null) {
            instance = new ReplicateWorkflow();
        }
        return instance;
    }

    public Map<ReplicateStatus, TaskNotificationType> getActionMap() {
        return actionMap;
    }

    /*
     * This is where the whole workflow is defined
     */
    private void setWorkflowTransitions() {
        setDefaultWorkflowTransitions();
        addTransitionsToFailed();
        addWorkerLostTransitions();
        addRecoveringTransitions();
        addAbortedTransitions();
    }

    private void setDefaultWorkflowTransitions() {
        // start
        addTransition(CREATED,      toList(STARTING));
        addTransition(STARTING,     toList(STARTED, START_FAILED));
        addTransition(STARTED,      toList(APP_DOWNLOADING));

        // app
        addTransition(APP_DOWNLOADING,      toList(APP_DOWNLOADED, APP_DOWNLOAD_FAILED));
        addTransition(APP_DOWNLOAD_FAILED,  toList(CONTRIBUTING));
        addTransition(APP_DOWNLOADED,       toList(DATA_DOWNLOADING));

        // data
        addTransition(DATA_DOWNLOADING, toList(DATA_DOWNLOADED, DATA_DOWNLOAD_FAILED));
        addTransition(DATA_DOWNLOAD_FAILED, toList(CONTRIBUTING));
        addTransition(DATA_DOWNLOADED, toList(COMPUTING));

        // computation
        addTransition(COMPUTING, toList(COMPUTED, COMPUTE_FAILED));
        addTransition(COMPUTED, CONTRIBUTING);

        // contribution
        addTransition(CONTRIBUTING, toList(CONTRIBUTED, CONTRIBUTE_FAILED));
        addTransition(CONTRIBUTED, toList(REVEALING));
        // addTransition(CONTRIBUTED, toList(REVEAL_FAILED));

        // reveal
        addTransition(REVEALING, toList(REVEALED, REVEAL_FAILED));
        addTransition(REVEALED, toList(RESULT_UPLOAD_REQUESTED, COMPLETING));

        // result upload
        addTransition(RESULT_UPLOAD_REQUESTED, toList(RESULT_UPLOADING, RESULT_UPLOAD_REQUEST_FAILED));
        addTransition(RESULT_UPLOAD_REQUEST_FAILED, toList(COMPLETING));
        addTransition(RESULT_UPLOADING, toList(RESULT_UPLOADED, RESULT_UPLOAD_FAILED));
        addTransition(RESULT_UPLOAD_FAILED, toList(COMPLETING));
        addTransition(RESULT_UPLOADED, toList(COMPLETING));

        // complete
        addTransition(COMPLETING, toList(COMPLETED, COMPLETE_FAILED));
    }

    /*
     * At the end of a task, all replicates that have not been completed
     * should update their statuses to FAILED.
     * We should only have COMPLETED and FAILED as final statuses.
     */
    private void addTransitionsToFailed() {
        List<ReplicateStatus> abortable = getFailableStatuses();
        addTransition(abortable, FAILED);
        addTransition(WORKER_LOST, FAILED);     // when <status> -> WORKER_LOST -> FAILED
        addTransition(RECOVERING, FAILED);      // when <status> -> RECOVERING  -> FAILED
    }

    /*
     * - Default*   ---                   --- Default
     * - RECOVERING ---|-- WORKER_LOST --|--- RECOVERING
     * - ABORTED    ---                   --- ABORTED
     * 
     * (*) except COMPLETED and FAILED
     */
    private void addWorkerLostTransitions() {
        List<ReplicateStatus> defaultStatuses = getWorkflowStatuses();
        List<ReplicateStatus> defaultNonFinal = getNonFinalWorkflowStatuses();

        addTransition(defaultNonFinal, WORKER_LOST);
        addTransition(RECOVERING, WORKER_LOST);
        addTransition(ABORTED, WORKER_LOST);

        addTransition(WORKER_LOST, defaultStatuses);
        addTransition(WORKER_LOST, RECOVERING);
        addTransition(WORKER_LOST, ABORTED);
    }

    /*
     * - Recoverable ---                   All statuses 
     *                  |-- RECOVERING --| except CREATED,
     * - WORKER_LOST ---                   STARTING
     */
    private void addRecoveringTransitions() {
        List<ReplicateStatus> recoverable = getRecoverableStatuses();
        List<ReplicateStatus> all = Arrays.asList(ReplicateStatus.values());

        addTransition(recoverable, RECOVERING);
        addTransition(WORKER_LOST, RECOVERING);

        addTransition(RECOVERING, all);
        removeTransition(RECOVERING, CREATED);
        removeTransition(RECOVERING, STARTING);
    }

    /*
     * Default*    ---                 --- COMPLETED
     *                |--- ABORTED ---|
     * WORKER_LOST ---                 --- FAILED
     * 
     * (*) except COMPLETED and FAILED
     */
    private void addAbortedTransitions() {
        List<ReplicateStatus> abortable = getAbortableStatuses();

        addTransition(abortable, ABORTED);
        addTransition(WORKER_LOST, ABORTED);

        addTransition(ABORTED, COMPLETED);
        addTransition(ABORTED, FAILED);
    }

    private void setNextAction(ReplicateStatus whenStatus, TaskNotificationType nextAction) {
        actionMap.putIfAbsent(whenStatus, nextAction);
    }

    public TaskNotificationType getNextAction(ReplicateStatus whenStatus, ReplicateStatusCause whenCause) {
        TaskNotificationType nextAction = getNextActionWhenStatusAndCause(whenStatus, whenCause);
        if (nextAction == null){
            nextAction = getNextActionWhenStatus(whenStatus);
        }
        return nextAction;
    }

    TaskNotificationType getNextActionWhenStatusAndCause(ReplicateStatus whenStatus, ReplicateStatusCause whenCause) {
        if (whenStatus == null){
            return null;
        }
        if (whenCause == null){
            return null;
        }
        switch (whenStatus){
            case APP_DOWNLOAD_FAILED:
                if (whenCause.equals(ReplicateStatusCause.APP_IMAGE_DOWNLOAD_FAILED)){
                    return PLEASE_CONTRIBUTE;
                }
                return PLEASE_ABORT;
            case DATA_DOWNLOAD_FAILED:
                if (whenCause.equals(ReplicateStatusCause.DATASET_FILE_DOWNLOAD_FAILED)
                        || whenCause.equals(ReplicateStatusCause.DATASET_FILE_BAD_CHECKSUM)
                        || whenCause.equals(ReplicateStatusCause.INPUT_FILES_DOWNLOAD_FAILED)){
                    return PLEASE_CONTRIBUTE;
                }
                return PLEASE_ABORT;
            default:
                return null;
        }
    }

    TaskNotificationType getNextActionWhenStatus(ReplicateStatus whenStatus) {
        if (actionMap.containsKey(whenStatus)){
            return actionMap.get(whenStatus);
        }
        return null;
    }

    private void setNextActions() {
        setNextAction(STARTING, PLEASE_CONTINUE);
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
}
