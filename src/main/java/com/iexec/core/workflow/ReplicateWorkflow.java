package com.iexec.core.workflow;

import com.iexec.common.replicate.ReplicateStatus;

import static com.iexec.common.replicate.ReplicateStatus.*;

public class ReplicateWorkflow extends Workflow<ReplicateStatus> {

    private static ReplicateWorkflow instance;

    public static synchronized ReplicateWorkflow getInstance() {
        if (instance == null) {
            instance = new ReplicateWorkflow();
        }
        return instance;
    }

    private ReplicateWorkflow() {
        super();

        // TODO: add all the transitions needed here
        addTransition(CREATED, RUNNING);
        addTransition(RUNNING, COMPUTED);
    }
}
