package com.iexec.core.workflow;

import org.junit.Test;

import static com.iexec.common.replicate.ReplicateStatus.COMPUTED;
import static com.iexec.common.replicate.ReplicateStatus.CREATED;
import static com.iexec.common.replicate.ReplicateStatus.RUNNING;
import static org.assertj.core.api.Assertions.assertThat;

public class ReplicateWorkflowTests {

    @Test
    public void shouldAllBeValidTransitions(){
        assertThat(ReplicateWorkflow.getInstance().isValidTransition(CREATED, RUNNING)).isTrue();
        assertThat(ReplicateWorkflow.getInstance().isValidTransition(CREATED, COMPUTED)).isFalse();
    }
}
