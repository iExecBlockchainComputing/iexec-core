package com.iexec.core.workflow;

class DummyWorkflow extends Workflow<String>{

    private static DummyWorkflow instance;

    static synchronized DummyWorkflow getInstance() {
        if (instance == null) {
            instance = new DummyWorkflow();
        }
        return instance;
    }

    private DummyWorkflow() {
        super();

        // This is where the whole workflow is defined
        // 1 -- 2 -- [3, 4] -- 5
        addTransition("STATUS_1", "STATUS_2");

        addTransition("STATUS_2", "STATUS_3");
        addTransition("STATUS_2", "STATUS_4");

        addTransition("STATUS_3", "STATUS_5");
        addTransition("STATUS_4", "STATUS_5");
    }
}