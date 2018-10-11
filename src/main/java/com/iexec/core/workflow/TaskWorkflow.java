package com.iexec.core.workflow;

import com.iexec.core.task.TaskStatus;

import static com.iexec.core.task.TaskStatus.*;


public class TaskWorkflow extends Workflow<TaskStatus> {

    private static TaskWorkflow instance;

    public static synchronized TaskWorkflow getInstance() {
        if (instance == null) {
            instance = new TaskWorkflow();
        }
        return instance;
    }

    private TaskWorkflow() {
        super();

        // This is where the whole workflow is defined
        addTransition(CREATED, RUNNING);
        addTransition(RUNNING, COMPUTED);
        addTransition(COMPUTED, UPLOAD_RESULT_REQUESTED);
        addTransition(UPLOAD_RESULT_REQUESTED, UPLOADING_RESULT);
        addTransition(UPLOADING_RESULT, RESULT_UPLOADED);
        addTransition(UPLOADING_RESULT, ERROR);
    }
}
