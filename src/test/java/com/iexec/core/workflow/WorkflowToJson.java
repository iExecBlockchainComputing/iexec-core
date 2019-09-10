package com.iexec.core.workflow;

import org.junit.Test;

public class WorkflowToJson {

    /*
     * This updates the json files when transitions
     * or actions are modified. 
     */
    @Test
    public void workflowToJson() {
        String transitionsFilePath = "src/main/resources/workflow/replicate-transitions.json";
        String actionsFilePath = "src/main/resources/workflow/replicate-actions.json";
        ReplicateWorkflow rw = ReplicateWorkflow.getInstance();
        
        rw.saveWorkflowAsJsonFile(transitionsFilePath, rw.getTransitions());
        rw.saveWorkflowAsJsonFile(actionsFilePath, rw.getActionMap());
    }
}