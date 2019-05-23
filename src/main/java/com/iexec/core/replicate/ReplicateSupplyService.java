package com.iexec.core.replicate;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.disconnection.InterruptedReplicateModel;
import com.iexec.common.disconnection.RecoveryAction;
import com.iexec.common.replicate.ReplicateDetails;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.chain.SignatureService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.sms.SmsService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskExecutorEngine;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class ReplicateSupplyService {

    private ReplicatesService replicatesService;
    private SignatureService signatureService;
    private TaskExecutorEngine taskExecutorEngine;
    private TaskService taskService;
    private WorkerService workerService;
    private SmsService smsService;
    private Web3jService web3jService;


    public ReplicateSupplyService(ReplicatesService replicatesService,
                                  SignatureService signatureService,
                                  TaskExecutorEngine taskExecutorEngine,
                                  TaskService taskService,
                                  WorkerService workerService,
                                  SmsService smsService,
                                  Web3jService web3jService) {
        this.replicatesService = replicatesService;
        this.signatureService = signatureService;
        this.taskExecutorEngine = taskExecutorEngine;
        this.taskService = taskService;
        this.workerService = workerService;
        this.smsService = smsService;
        this.web3jService = web3jService;
    }

    // in case the task has been modified between reading and writing it, it is retried up to 5 times
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 5)
    Optional<ContributionAuthorization> getAuthOfAvailableReplicate(long workerLastBlock, String walletAddress) {
        // return empty if the worker is not registered
        Optional<Worker> optional = workerService.getWorker(walletAddress);
        if (!optional.isPresent()) {
            return Optional.empty();
        }
        Worker worker = optional.get();

        // return empty if the worker is not sync
        //TODO Check if worker node is sync
        boolean isWorkerLastBlockAvailable = workerLastBlock > 0;
        if (!isWorkerLastBlockAvailable) {
            return Optional.empty();
        }

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
            String chainTaskId = task.getChainTaskId();

            // skip the task if it needs TEE and the worker doesn't support it
            boolean doesTaskNeedTEE = task.isTeeNeeded();
            if(doesTaskNeedTEE && !worker.isTeeEnabled()) {
                continue;
            }

            taskService.initializeTaskAccessForNewReplicateLock(chainTaskId);
            if (taskService.isTaskBeingAccessedForNewReplicate(chainTaskId)){
                continue;//skip task if being accessed
            }
            taskService.lockTaskAccessForNewReplicate(chainTaskId);

            boolean isFewBlocksAfterInitialization = isFewBlocksAfterInitialization(task);
            boolean hasWorkerAlreadyParticipated = replicatesService.hasWorkerAlreadyParticipated(
                    chainTaskId, walletAddress);
            boolean moreReplicatesNeeded = replicatesService.moreReplicatesNeeded(chainTaskId,
                    task.getNumWorkersNeeded(), task.getMaxExecutionTime());

            if (isFewBlocksAfterInitialization && !hasWorkerAlreadyParticipated && moreReplicatesNeeded) {

                String enclaveChallenge = smsService.getEnclaveChallenge(chainTaskId, doesTaskNeedTEE);
                if (enclaveChallenge.isEmpty()){
                    taskService.unlockTaskAccessForNewReplicate(chainTaskId);//avoid dead lock
                    continue;
                }

                replicatesService.addNewReplicate(chainTaskId, walletAddress);
                taskService.unlockTaskAccessForNewReplicate(chainTaskId);
                workerService.addChainTaskIdToWorker(chainTaskId, walletAddress);

                // generate contribution authorization
                return Optional.of(signatureService.createAuthorization(
                        walletAddress, chainTaskId, enclaveChallenge));
            }
        }

        return Optional.empty();
    }




    private boolean isFewBlocksAfterInitialization(Task task) {
        long coreLastBlock = web3jService.getLatestBlockNumber();
        long initializationBlock = task.getInitializationBlockNumber();
        boolean isFewBlocksAfterInitialization = coreLastBlock >= initializationBlock + 2;
        return coreLastBlock > 0 && initializationBlock > 0 && isFewBlocksAfterInitialization;
    }

    public List<InterruptedReplicateModel> getInterruptedReplicates(long blockNumber, String walletAddress) {

        List<String> chainTaskIdList = workerService.getChainTaskIds(walletAddress);
        List<Task> tasksWithWorkerParticipation = taskService.getTasksByChainTaskIds(chainTaskIdList);
        List<InterruptedReplicateModel> interruptedReplicates = new ArrayList<>();

        for (Task task : tasksWithWorkerParticipation) {
            String chainTaskId = task.getChainTaskId();

            Optional<Replicate> oReplicate = replicatesService.getReplicate(chainTaskId, walletAddress);
            if (!oReplicate.isPresent()) continue;

            Replicate replicate = oReplicate.get();

            boolean isRecoverable = replicate.isRecoverable();
            if (!isRecoverable) continue;

            String enclaveChallenge = smsService.getEnclaveChallenge(chainTaskId, task.isTeeNeeded());
            if (task.isTeeNeeded() && enclaveChallenge.isEmpty()) continue;

            Optional<RecoveryAction> oRecoveryAction = getAppropriateRecoveryAction(task, replicate, blockNumber);
            if (!oRecoveryAction.isPresent()) continue;

            // generate contribution authorization
            ContributionAuthorization authorization = signatureService.createAuthorization(
                    walletAddress, chainTaskId, enclaveChallenge);

            InterruptedReplicateModel interruptedReplicate = InterruptedReplicateModel.builder()
                    .contributionAuthorization(authorization)
                    .recoveryAction(oRecoveryAction.get())
                    .build();

            // change replicate status
            replicatesService.updateReplicateStatus(chainTaskId, walletAddress,
                    ReplicateStatus.RECOVERING, ReplicateStatusModifier.POOL_MANAGER);

            interruptedReplicates.add(interruptedReplicate);
        }

        return interruptedReplicates;
    }

    public Optional<RecoveryAction> getAppropriateRecoveryAction(Task task, Replicate replicate, long blockNumber) {

        if (task.inContributionPhase()) {
            return recoverReplicateInContributionPhase(task, replicate, blockNumber);
        }

        if (task.getCurrentStatus().equals(TaskStatus.CONTRIBUTION_TIMEOUT)) {
            return Optional.of(RecoveryAction.ABORT_CONTRIBUTION_TIMEOUT);
        }

        if (task.getCurrentStatus().equals(TaskStatus.CONSENSUS_REACHED) && !replicate.containsContributedStatus()) {
            return Optional.of(RecoveryAction.ABORT_CONSENSUS_REACHED);
        }

        Optional<RecoveryAction> oRecoveryAction = Optional.empty();

        if (task.inRevealPhase()) {
            oRecoveryAction = recoverReplicateInRevealPhase(task, replicate, blockNumber);
        }

        if (task.inResultUploadPhase()) {
            oRecoveryAction = recoverReplicateInResultUploadPhase(task, replicate);
        }

        if (task.inCompletionPhase()) {
            return recoverReplicateIfRevealed(replicate);
        }

        return oRecoveryAction;
    }

    /**
     * CREATED, ..., CAN_CONTRIBUTE         => RecoveryAction.CONTRIBUTE
     * CONTRIBUTING + !onChain              => RecoveryAction.CONTRIBUTE
     * CONTRIBUTING + done onChain          => updateStatus to CONTRIBUTED & go to next case
     * CONTRIBUTED + !CONSENSUS_REACHED     => RecoveryAction.WAIT
     * CONTRIBUTED + CONSENSUS_REACHED      => RecoveryAction.REVEAL
     */

    private Optional<RecoveryAction> recoverReplicateInContributionPhase(Task task, Replicate replicate, long blockNumber) {
        String chainTaskId = task.getChainTaskId();
        String walletAddress = replicate.getWalletAddress();

        if (!replicate.getLastRelevantStatus().isPresent()) return Optional.empty();

        boolean beforeContributing = replicate.isBeforeStatus(ReplicateStatus.CONTRIBUTING);
        boolean didReplicateStartContributing = replicate.getLastRelevantStatus().get().equals(ReplicateStatus.CONTRIBUTING);
        boolean didReplicateContributeOnChain = replicatesService.didReplicateContributeOnchain(chainTaskId, walletAddress);

        if (beforeContributing) {
            return Optional.of(RecoveryAction.CONTRIBUTE);
        }

        if (didReplicateStartContributing && !didReplicateContributeOnChain) {
            return Optional.of(RecoveryAction.CONTRIBUTE);
        }

        if (didReplicateStartContributing && didReplicateContributeOnChain) {

            replicatesService.updateReplicateStatus(chainTaskId, walletAddress,
                    ReplicateStatus.CONTRIBUTED, ReplicateStatusModifier.POOL_MANAGER,
                    ReplicateDetails.builder().chainReceipt(new ChainReceipt(blockNumber, "")).build());
        }

        // we read the replicate from db to consider the changes added in the previous case
        Optional<Replicate> oReplicateWithLatestChanges = replicatesService.getReplicate(chainTaskId, walletAddress);
        if (!oReplicateWithLatestChanges.isPresent()) return Optional.empty();

        Replicate replicateWithLatestChanges = oReplicateWithLatestChanges.get();
        if (!replicateWithLatestChanges.getLastRelevantStatus().isPresent()) return Optional.empty();

        boolean didReplicateContribute = replicateWithLatestChanges.getLastRelevantStatus().get()
                .equals(ReplicateStatus.CONTRIBUTED);

        if (didReplicateContribute) {

            if (!taskService.isConsensusReached(task)) {
                return Optional.of(RecoveryAction.WAIT);
            }

            taskExecutorEngine.updateTask(chainTaskId);
            return Optional.of(RecoveryAction.REVEAL);
        }

        return Optional.empty();
    }

    /**
     * CONTRIBUTED                      => RecoveryAction.REVEAL
     * REVEALING + !onChain             => RecoveryAction.REVEAL
     * REVEALING + done onChain         => update replicateStatus to REVEALED, update task & go to next case
     * REVEALED (no upload req)         => RecoveryAction.WAIT
     * RESULT_UPLOAD_REQUESTED          => RecoveryAction.UPLOAD_RESULT
     */

    private Optional<RecoveryAction> recoverReplicateInRevealPhase(Task task, Replicate replicate, long blockNumber) {
        String chainTaskId = task.getChainTaskId();
        String walletAddress = replicate.getWalletAddress();

        if (!replicate.getLastRelevantStatus().isPresent()) return Optional.empty();

        boolean isInStatusContributed = replicate.getLastRelevantStatus().get().equals(ReplicateStatus.CONTRIBUTED);
        boolean didReplicateStartRevealing = replicate.getLastRelevantStatus().get().equals(ReplicateStatus.REVEALING);
        boolean didReplicateRevealOnChain = replicatesService.didReplicateRevealOnchain(chainTaskId, walletAddress);

        if (isInStatusContributed) {
            return Optional.of(RecoveryAction.REVEAL);
        }

        if (didReplicateStartRevealing && !didReplicateRevealOnChain) {
            return Optional.of(RecoveryAction.REVEAL);
        }

        if (didReplicateStartRevealing && didReplicateRevealOnChain) {
            replicatesService.updateReplicateStatus(chainTaskId, walletAddress,
                    ReplicateStatus.REVEALED, ReplicateStatusModifier.POOL_MANAGER,
                    ReplicateDetails.builder().chainReceipt(new ChainReceipt(blockNumber, "")).build());

            CompletableFuture<Boolean> completableFuture = taskExecutorEngine.updateTask(chainTaskId);
            completableFuture.join();
        }

        // we read the replicate from db to consider the changes added in the previous case
        Optional<Replicate> oReplicateWithLatestChanges = replicatesService.getReplicate(chainTaskId, walletAddress);

        replicate = oReplicateWithLatestChanges.get();
        if (!replicate.getLastRelevantStatus().isPresent()) return Optional.empty();

        boolean didReplicateReveal = replicate.getLastRelevantStatus().get()
                .equals(ReplicateStatus.REVEALED);

        boolean wasReplicateRequestedToUpload = replicate.getLastRelevantStatus().get()
                .equals(ReplicateStatus.RESULT_UPLOAD_REQUESTED);

        if (didReplicateReveal) {
            return Optional.of(RecoveryAction.WAIT);
        }

        if (wasReplicateRequestedToUpload) {
            return Optional.of(RecoveryAction.UPLOAD_RESULT);
        }

        return Optional.empty();
    }

    /**
     * RESULT_UPLOAD_REQUESTED          => RecoveryAction.UPLOAD_RESULT
     * RESULT_UPLOADING + !done yet     => RecoveryAction.UPLOAD_RESULT
     * RESULT_UPLOADING + done          => RecoveryAction.WAIT
     * update to ReplicateStatus.RESULT_UPLOADED
     * RESULT_UPLOADED                  => RecoveryAction.WAIT
     */

    private Optional<RecoveryAction> recoverReplicateInResultUploadPhase(Task task, Replicate replicate) {
        String chainTaskId = task.getChainTaskId();
        String walletAddress = replicate.getWalletAddress();

        if (!replicate.getLastRelevantStatus().isPresent()) return Optional.empty();

        boolean wasReplicateRequestedToUpload = replicate.getLastRelevantStatus().get().equals(ReplicateStatus.RESULT_UPLOAD_REQUESTED);
        boolean didReplicateStartUploading = replicate.getLastRelevantStatus().get().equals(ReplicateStatus.RESULT_UPLOADING);
        boolean didReplicateUploadWithoutNotifying = replicatesService.isResultUploaded(task.getChainTaskId());
        boolean hasReplicateAlreadyUploaded = replicate.getLastRelevantStatus().get().equals(ReplicateStatus.RESULT_UPLOADED);

        if (wasReplicateRequestedToUpload) {
            return Optional.of(RecoveryAction.UPLOAD_RESULT);
        }

        if (didReplicateStartUploading && !didReplicateUploadWithoutNotifying) {
            return Optional.of(RecoveryAction.UPLOAD_RESULT);
        }

        if (didReplicateStartUploading && didReplicateUploadWithoutNotifying) {
            replicatesService.updateReplicateStatus(chainTaskId, walletAddress,
                    ReplicateStatus.RESULT_UPLOADED, ReplicateStatusModifier.POOL_MANAGER);

            taskExecutorEngine.updateTask(chainTaskId);
            return Optional.of(RecoveryAction.WAIT);
        }

        if (hasReplicateAlreadyUploaded) {
            return Optional.of(RecoveryAction.WAIT);
        }

        return Optional.empty();
    }

    /**
     * REVEALED + task in COMPLETED status          => RecoveryAction.COMPLETE
     * REVEALED + task not in COMPLETED status      => RecoveryAction.WAIT
     * !REVEALED                                    => null
     */

    private Optional<RecoveryAction> recoverReplicateIfRevealed(Replicate replicate) {
        // refresh task
        Optional<Task> oTask = taskService.getTaskByChainTaskId(replicate.getChainTaskId());
        if (!oTask.isPresent()) return Optional.empty();

        if (replicate.containsRevealedStatus()) {
            if (oTask.get().getCurrentStatus().equals(TaskStatus.COMPLETED)) {
                return Optional.of(RecoveryAction.COMPLETE);
            }

            return Optional.of(RecoveryAction.WAIT);
        }

        return Optional.empty();
    }
}