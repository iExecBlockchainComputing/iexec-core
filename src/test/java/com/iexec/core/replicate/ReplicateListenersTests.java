package com.iexec.core.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.core.detector.replicate.ContributionUnnotifiedDetector;
import com.iexec.core.task.TaskExecutorEngine;
import com.iexec.core.task.listener.ReplicateListeners;
import com.iexec.core.worker.WorkerService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static com.iexec.common.replicate.ReplicateStatus.*;
import static com.iexec.common.replicate.ReplicateStatusCause.TASK_NOT_ACTIVE;
import static org.mockito.ArgumentMatchers.any;

public class ReplicateListenersTests {

    private final static String CHAIN_TASK_ID = "chainTaskId";
    private final static String WORKER_WALLET = "0xwallet1";

    @Mock
    private TaskExecutorEngine taskExecutorEngine;
    @Mock
    private WorkerService workerService;
    @Mock
    private ContributionUnnotifiedDetector contributionUnnotifiedDetector;
    @Mock
    private ReplicatesService replicatesService;

    @InjectMocks
    private ReplicateListeners replicateListeners;


    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shoulUpdateTaskOnReplicateUpdate() {
        List<ReplicateStatus> someStatuses = ReplicateStatus.getSuccessStatuses(); //not exhaustive

        for (ReplicateStatus randomStatus: someStatuses){
            ReplicateUpdatedEvent replicateUpdatedEvent = ReplicateUpdatedEvent.builder()
                    .chainTaskId(CHAIN_TASK_ID)
                    .walletAddress(WORKER_WALLET)
                    .replicateStatusUpdate(new ReplicateStatusUpdate(randomStatus))
                    .build();

            replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);
        }

        Mockito.verify(taskExecutorEngine, Mockito.times(someStatuses.size())).updateTask(any());
    }

    @Test
    public void shouldTriggerDetectOnchainContributedSinceTaskNotActive() {
        ReplicateUpdatedEvent replicateUpdatedEvent = ReplicateUpdatedEvent.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .walletAddress(WORKER_WALLET)
                .replicateStatusUpdate(new ReplicateStatusUpdate(CONTRIBUTING, TASK_NOT_ACTIVE))
                .build();

        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);

        Mockito.verify(contributionUnnotifiedDetector, Mockito.times(1)).detectOnchainContributed();
    }

    @Test
    public void shouldNotTriggerDetectOnchain() {
        List<ReplicateStatus> someStatuses = ReplicateStatus.getSuccessStatuses(); //not exhaustive
        someStatuses.remove(CONTRIBUTING);

        for (ReplicateStatus randomStatus: someStatuses){
            ReplicateUpdatedEvent replicateUpdatedEvent = ReplicateUpdatedEvent.builder()
                    .chainTaskId(CHAIN_TASK_ID)
                    .walletAddress(WORKER_WALLET)
                    .replicateStatusUpdate(new ReplicateStatusUpdate(randomStatus))
                    .build();

            replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);
        }

        Mockito.verify(contributionUnnotifiedDetector, Mockito.times(0)).detectOnchainContributed();
    }

    @Test
    public void shouldAddFailedStatusSinceUncompletableReplicateStatus() {
        List<ReplicateStatus> uncompletableStatuses = ReplicateStatus.getUncompletableStatuses();

        for (ReplicateStatus uncompletableStatus: uncompletableStatuses){
            ReplicateUpdatedEvent replicateUpdatedEvent = ReplicateUpdatedEvent.builder()
                    .chainTaskId(CHAIN_TASK_ID)
                    .walletAddress(WORKER_WALLET)
                    // CANT_CONTRIBUTE_SINCE_*, ...
                    .replicateStatusUpdate(new ReplicateStatusUpdate(uncompletableStatus))
                    .build();

            replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);
        }

        Mockito.verify(replicatesService, Mockito.times(uncompletableStatuses.size()))
                .updateReplicateStatus(CHAIN_TASK_ID, WORKER_WALLET, FAILED);
    }

    @Test
    public void shouldNotAddFailedStatusSinceCompletableReplicateStatus() {
        List<ReplicateStatus> completableStatuses = ReplicateStatus.getCompletableStatuses();

        for (ReplicateStatus completableStatus: completableStatuses){
            ReplicateUpdatedEvent replicateUpdatedEvent = ReplicateUpdatedEvent.builder()
                    .chainTaskId(CHAIN_TASK_ID)
                    .walletAddress(WORKER_WALLET)
                    // CREATED, ...
                    .replicateStatusUpdate(new ReplicateStatusUpdate(completableStatus))
                    .build();

            replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);
        }

        Mockito.verify(replicatesService, Mockito.times(0))
                .updateReplicateStatus(CHAIN_TASK_ID, WORKER_WALLET, FAILED);
    }

    @Test
    public void shouldRemoveComputedChainTaskIdFromWorkerSinceFailed() {
        ReplicateUpdatedEvent replicateUpdatedEvent = ReplicateUpdatedEvent.builder()
                .chainTaskId(CHAIN_TASK_ID)
                .walletAddress(WORKER_WALLET)
                .replicateStatusUpdate(new ReplicateStatusUpdate(FAILED))
                .build();

        replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);

        Mockito.verify(workerService, Mockito.times(1))
            .removeChainTaskIdFromWorker(CHAIN_TASK_ID, WORKER_WALLET);
    }

    @Test
    public void shouldNotRemoveComputedChainTaskIdFromWorker() {
        List<ReplicateStatus> someStatuses = ReplicateStatus.getSuccessStatuses(); //not exhaustive
        someStatuses.remove(FAILED);

        for (ReplicateStatus randomStatus: someStatuses){
            ReplicateUpdatedEvent replicateUpdatedEvent = ReplicateUpdatedEvent.builder()
                    .chainTaskId(CHAIN_TASK_ID)
                    .walletAddress(WORKER_WALLET)
                    // CREATED, ...
                    .replicateStatusUpdate(new ReplicateStatusUpdate(randomStatus))
                    .build();

            replicateListeners.onReplicateUpdatedEvent(replicateUpdatedEvent);
        }
        
        Mockito.verify(workerService, Mockito.times(0))
                .removeChainTaskIdFromWorker(CHAIN_TASK_ID, WORKER_WALLET);
    }


}