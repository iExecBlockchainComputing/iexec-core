package com.iexec.core.prediction;

import com.iexec.core.chain.IexecHubService;
import com.iexec.core.replicate.ReplicatesService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


public class PredictionServiceTests {

    private final static String WALLET_WORKER_1 = "0x1";
    private final static String CHAIN_TASK_ID = "0xtaskId";
    private final static long maxExecutionTime = 60000;

    @Mock
    private ReplicatesService replicatesService;
    @Mock
    private IexecHubService iexecHubService;

    @InjectMocks
    private PredictionService predictionService;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void should() {

    }

}