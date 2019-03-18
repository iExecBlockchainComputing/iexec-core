package com.iexec.core.replicate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.disconnection.InterruptedReplicateModel;
import com.iexec.common.disconnection.RecoveryAction;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.tee.TeeUtils;
import com.iexec.core.chain.SignatureService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskExecutorEngine;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
public class ReplicateSupplyService {

    private ReplicatesService replicatesService;
    private SignatureService signatureService;
    private TaskExecutorEngine taskExecutorEngine;
    private TaskService taskService;
    private WorkerService workerService;


    public ReplicateSupplyService(ReplicatesService replicatesService,
                                 SignatureService signatureService,
                                 TaskExecutorEngine taskExecutorEngine,
                                 TaskService taskService,
                                 WorkerService workerService) {
        this.replicatesService = replicatesService;
        this.signatureService = signatureService;
        this.taskExecutorEngine = taskExecutorEngine;
        this.taskService = taskService;
        this.workerService = workerService;
    }

    // in case the task has been modified between reading and writing it, it is retried up to 5 times
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 5)
    Optional<Replicate> getAvailableReplicate(long blockNumber, String walletAddress) {
        // return empty if the worker is not registered
        Optional<Worker> optional = workerService.getWorker(walletAddress);
        if (!optional.isPresent()) {
            return Optional.empty();
        }
        Worker worker = optional.get();

        // return empty if there is no task to contribute
        List<Task> runningTasks = taskService.getInitializedOrRunningTasks();
        if (runningTasks.isEmpty()) {
            return Optional.empty();
        }

        // return empty if the worker already has enough running tasks
        if (!workerService.canAcceptMoreWorks(walletAddress)) {
            return Optional.empty();
        }

        for (Task task : runningTasks) {
            // skip the task if it needs TEE and the worker doesn't support it
            boolean doesTaskNeedTEE = TeeUtils.isTrustedExecutionTag(task.getTag());
            if(doesTaskNeedTEE && !worker.isTeeEnabled()) {
                continue;
            }

            String chainTaskId = task.getChainTaskId();

            boolean blockNumberAvailable = task.getInitializationBlockNumber() != 0
                    && task.getInitializationBlockNumber() <= blockNumber;
            boolean hasWorkerAlreadyParticipated = replicatesService.hasWorkerAlreadyParticipated(
                    chainTaskId, walletAddress);
            boolean moreReplicatesNeeded = replicatesService.moreReplicatesNeeded(chainTaskId,
                    task.getNumWorkersNeeded(), task.getMaxExecutionTime());

            if (blockNumberAvailable && !hasWorkerAlreadyParticipated && moreReplicatesNeeded) {
                replicatesService.addNewReplicate(chainTaskId, walletAddress);
                workerService.addChainTaskIdToWorker(chainTaskId, walletAddress);
                return replicatesService.getReplicate(chainTaskId, walletAddress);
            }
        }

        return Optional.empty();
    }

    public List<InterruptedReplicateModel> getInterruptedReplicates(long blockNumber, String walletAddress) {

        List<String> chainTaskIdList = workerService.getChainTaskIds(walletAddress);
        List<Task> tasksWithWorkerParticipation = taskService.getTasksByChainTaskIds(chainTaskIdList);
        List<InterruptedReplicateModel> interruptedReplicates = new ArrayList<>();

        for (Task task : tasksWithWorkerParticipation) {

            Optional<Replicate> oReplicate = replicatesService.getReplicate(
                    task.getChainTaskId(), walletAddress);
            if (!oReplicate.isPresent()) continue;

            Replicate replicate = oReplicate.get();

            boolean isRecoverable = replicate.isRecoverable();
            if (!isRecoverable) continue;

            RecoveryAction recoveryAction = getAppropriateRecoveryAction(task, replicate, blockNumber);
            if (recoveryAction == null) continue;

            // generate contribution authorization
            Optional<ContributionAuthorization> authorization = signatureService.createAuthorization(
                    walletAddress, task.getChainTaskId(), TeeUtils.isTrustedExecutionTag(task.getTag()));

            InterruptedReplicateModel interruptedReplicate = InterruptedReplicateModel.builder()
                    .contributionAuthorization(authorization.get())
                    .recoveryAction(recoveryAction)
                    .build();

            // change replicate status
            replicatesService.updateReplicateStatus(task.getChainTaskId(), walletAddress,
                    ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);

            interruptedReplicates.add(interruptedReplicate);
        }

        return interruptedReplicates;
    }

    public RecoveryAction getAppropriateRecoveryAction(Task task, Replicate replicate, long blockNumber) {
        ChainReceipt chainReceipt = new ChainReceipt(blockNumber, "");

        if (task.inContributionPhase()) {
            return recoverReplicateInContributionPhase(task, replicate, chainReceipt);
        }

        if (task.getCurrentStatus().equals(TaskStatus.CONTRIBUTION_TIMEOUT)) {
            return RecoveryAction.ABORT_CONTRIBUTION_TIMEOUT;
        }

        if (task.getCurrentStatus().equals(TaskStatus.CONSENSUS_REACHED)) {
            if (!replicate.containsContributedStatus())
                return RecoveryAction.ABORT_CONSENSUS_REACHED;
        }

        RecoveryAction recoveryAction = null;

        if (task.inRevealPhase()) {
            recoveryAction = recoverReplicateInRevealPhase(task, replicate, chainReceipt);
        }

        if (task.inResultUploadPhase()) {
            recoveryAction = recoverReplicateInResultUploadPhase(task, replicate);
        }

        if (task.inCompletionPhase()) {
            return recoverReplicateIfRevealed(replicate);
        }

        return recoveryAction;
    }

    /**
     * CREATED, ..., CAN_CONTRIBUTE         => RecoveryAction.CONTRIBUTE
     * CONTRIBUTING + !onChain              => RecoveryAction.CONTRIBUTE
     * CONTRIBUTING + done onChain          => updateStatus to CONTRIBUTED & go to next case
     * CONTRIBUTED + !CONSENSUS_REACHED     => RecoveryAction.WAIT
     * CONTRIBUTED + CONSENSUS_REACHED      => RecoveryAction.REVEAL
     */

    private RecoveryAction recoverReplicateInContributionPhase(Task task, Replicate replicate, ChainReceipt chainReceipt) {
        String chainTaskId = task.getChainTaskId();
        String walletAddress = replicate.getWalletAddress();

        boolean beforeContributing = replicate.isBeforeStatus(ReplicateStatus.CONTRIBUTING);
        boolean didReplicateStartContributing = replicate.getLastRelevantStatus().equals(ReplicateStatus.CONTRIBUTING);
        boolean didReplicateContributeOnChain = replicatesService.didReplicateContributeOnchain(chainTaskId, walletAddress);

        if (beforeContributing) {
            return RecoveryAction.CONTRIBUTE;
        }

        if (didReplicateStartContributing && !didReplicateContributeOnChain) {
            return RecoveryAction.CONTRIBUTE;
        }

        if (didReplicateStartContributing && didReplicateContributeOnChain) {

            replicatesService.updateReplicateStatus(chainTaskId, walletAddress,
                    ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.POOL_MANAGER,
                    chainReceipt, "");
        }

        // we read the replicate from db to consider the changes added in the previous case
        Optional<Replicate> oReplicateWithLatestChanges = replicatesService.getReplicate(chainTaskId, walletAddress);
        if (!oReplicateWithLatestChanges.isPresent()) return null;

        boolean didReplicateContribute = oReplicateWithLatestChanges.get()
                .getLastRelevantStatus()
                .equals(ReplicateStatus.CONTRIBUTED);

        if (didReplicateContribute) {

            if (!taskService.isConsensusReached(task)) {
                return RecoveryAction.WAIT;
            }

            taskExecutorEngine.updateTask(chainTaskId);
            return RecoveryAction.REVEAL;
        }

        return null;
    }

    /**
     * CONTRIBUTED                      => RecoveryAction.REVEAL
     * REVEALING + !onChain             => RecoveryAction.REVEAL
     * REVEALING + done onChain         => update replicateStatus to REVEALED, update task & go to next case
     * REVEALED (no upload req)         => RecoveryAction.WAIT
     * RESULT_UPLOAD_REQUESTED          => RecoveryAction.UPLOAD_RESULT
     */

    private RecoveryAction recoverReplicateInRevealPhase(Task task, Replicate replicate, ChainReceipt chainReceipt) {
        String chainTaskId = task.getChainTaskId();
        String walletAddress = replicate.getWalletAddress();

        boolean isInStatusContributed = replicate.getLastRelevantStatus().equals(ReplicateStatus.CONTRIBUTED);
        boolean didReplicateStartRevealing = replicate.getLastRelevantStatus().equals(ReplicateStatus.REVEALING);
        boolean didReplicateRevealOnChain = replicatesService.didReplicateRevealOnchain(chainTaskId, walletAddress);

        if (isInStatusContributed) {
            return RecoveryAction.REVEAL;
        }

        if (didReplicateStartRevealing && !didReplicateRevealOnChain) {
            return RecoveryAction.REVEAL;
        }

        if (didReplicateStartRevealing && didReplicateRevealOnChain) {
            replicatesService.updateReplicateStatus(chainTaskId, walletAddress,
                    ReplicateStatus.REVEALED, ReplicateStatusModifier.POOL_MANAGER,
                    chainReceipt, "");

            CompletableFuture<Boolean> completableFuture = taskExecutorEngine.updateTask(chainTaskId);
            completableFuture.join();
        }

        // we read the replicate from db to consider the changes added in the previous case
        Optional<Replicate> oReplicateWithLatestChanges = replicatesService.getReplicate(chainTaskId, walletAddress);
        if (!oReplicateWithLatestChanges.isPresent()) return null;
        
        replicate = oReplicateWithLatestChanges.get();

        boolean didReplicateReveal = replicate.getLastRelevantStatus()
                .equals(ReplicateStatus.REVEALED);

        boolean wasReplicateRequestedToUpload = replicate.getLastRelevantStatus()
                .equals(ReplicateStatus.RESULT_UPLOAD_REQUESTED);

        if (didReplicateReveal) {               
            return RecoveryAction.WAIT;
        }

        if (wasReplicateRequestedToUpload) {
            return RecoveryAction.UPLOAD_RESULT;
        }

        return null;
    }

    /**
     * RESULT_UPLOAD_REQUESTED          => RecoveryAction.UPLOAD_RESULT
     * RESULT_UPLOADING + !done yet     => RecoveryAction.UPLOAD_RESULT
     * RESULT_UPLOADING + done          => RecoveryAction.WAIT
     *                                     update to ReplicateStatus.RESULT_UPLOADED
     * RESULT_UPLOADED                  => RecoveryAction.WAIT 
     */

    private RecoveryAction recoverReplicateInResultUploadPhase(Task task, Replicate replicate) {
        String chainTaskId = task.getChainTaskId();
        String walletAddress = replicate.getWalletAddress();

        boolean wasReplicateRequestedToUpload = replicate.getLastRelevantStatus().equals(ReplicateStatus.RESULT_UPLOAD_REQUESTED);
        boolean didReplicateStartUploading = replicate.getLastRelevantStatus().equals(ReplicateStatus.RESULT_UPLOADING);
        boolean didReplicateUploadWithoutNotifying = replicatesService.hasResultBeenUploaded(task.getChainTaskId());
        boolean hasReplicateAlreadyUploaded = replicate.getLastRelevantStatus().equals(ReplicateStatus.RESULT_UPLOADED);

        if (wasReplicateRequestedToUpload) {
            return RecoveryAction.UPLOAD_RESULT;
        }

        if (didReplicateStartUploading && !didReplicateUploadWithoutNotifying) {
            return RecoveryAction.UPLOAD_RESULT;
        }

        if (didReplicateStartUploading && didReplicateUploadWithoutNotifying) {
            replicatesService.updateReplicateStatus(chainTaskId, walletAddress,
                    ReplicateStatus.RESULT_UPLOADED, ReplicateStatusModifier.POOL_MANAGER);

            taskExecutorEngine.updateTask(chainTaskId);
            return RecoveryAction.WAIT;
        }

        if (hasReplicateAlreadyUploaded) {
            return RecoveryAction.WAIT;
        }

        return null;
    }

    /**
     * REVEALED          => RecoveryAction.COMPLETE
     * !REVEALED         => null
     */

    private RecoveryAction recoverReplicateIfRevealed(Replicate replicate) {
        // refresh task
        Optional<Task> oTask = taskService.getTaskByChainTaskId(replicate.getChainTaskId());
        if (!oTask.isPresent()) return null;

        return replicate.containsRevealedStatus()
           ?   oTask.get().getCurrentStatus().equals(TaskStatus.COMPLETED)
           ?   RecoveryAction.COMPLETE
           :   RecoveryAction.WAIT
           :   null;
    }
}