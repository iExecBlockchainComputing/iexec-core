package com.iexec.core.task.executor;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class TaskUpdateRequestManagerTests {


    @InjectMocks
    private TaskUpdateRequestManager taskUpdateRequestManager;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldStartRunnable() throws Exception {

    }

}
