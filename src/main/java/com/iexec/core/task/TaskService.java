package com.iexec.core.task;

import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ChainTaskStatus;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.tee.TeeUtils;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.configuration.ResultRepositoryConfiguration;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.task.event.ConsensusReachedEvent;
import com.iexec.core.task.event.ContributionTimeoutEvent;
import com.iexec.core.task.event.PleaseUploadEvent;
import com.iexec.core.task.event.ResultUploadTimeoutEvent;
import com.iexec.core.task.event.TaskCompletedEvent;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static com.iexec.core.task.TaskStatus.*;


@Slf4j
@Service
public class TaskService {

    private TaskRepository taskRepository;
    private WorkerService workerService;
    private IexecHubService iexecHubService;
    private ReplicatesService replicatesService;
    private ApplicationEventPublisher applicationEventPublisher;
    private ResultRepositoryConfiguration resultRepositoryConfig;

    public TaskService(TaskRepository taskRepository,
                       WorkerService workerService,
                       IexecHubService iexecHubService,
                       ReplicatesService replicatesService,
                       ApplicationEventPublisher applicationEventPublisher,
                       ResultRepositoryConfiguration resultRepositoryConfig) {
        this.taskRepository = taskRepository;
        this.workerService = workerService;
        this.iexecHubService = iexecHubService;
        this.replicatesService = replicatesService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.resultRepositoryConfig = resultRepositoryConfig;
    }

    public Optional<Task> addTask(String chainDealId, int taskIndex, String imageName, String commandLine, int trust, long maxExecutionTime, String tag) {
        if (getTasksByChainDealIdAndTaskIndex(chainDealId, taskIndex).isEmpty()) {
            log.info("Add new task [chainDealId:{}, taskIndex:{}, imageName:{}, commandLine:{}, trust:{}]",
                    chainDealId, taskIndex, imageName, commandLine, trust);
            return Optional.of(taskRepository.save(new Task(chainDealId, taskIndex, imageName, commandLine, trust, maxExecutionTime, tag)));
        }
        log.info("Task already added [chainDealId:{}, taskIndex:{}, imageName:{}, commandLine:{}, trust:{}]",
                chainDealId, taskIndex, imageName, commandLine, trust);
        return Optional.empty();
    }

    public Optional<Task> getTaskByChainTaskId(String chainTaskId) {
        return taskRepository.findByChainTaskId(chainTaskId);
    }

    public List<Task> findByCurrentStatus(TaskStatus status) {
        return taskRepository.findByCurrentStatus(status);
    }

    public List<Task> findByCurrentStatus(List<TaskStatus> statusList) {
        return taskRepository.findByCurrentStatus(statusList);
    }

    private List<Task> getTasksByChainDealIdAndTaskIndex(String chainDealId, int taskIndex) {
        return taskRepository.findByChainDealIdAndTaskIndex(chainDealId, taskIndex);
    }

    private List<Task> getInitializedOrRunningTasks() {
        return taskRepository.findByCurrentStatus(Arrays.asList(INITIALIZED, RUNNING));
    }

    private List<Task> getTasksInNonFinalStatuses() {
        return taskRepository.findByCurrentStatusNotIn(Arrays.asList(FAILED, COMPLETED));
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
        List<Task> runningTasks = getInitializedOrRunningTasks();
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
            boolean blockNumberAvailable = task.getInitializationBlockNumber() != 0 && task.getInitializationBlockNumber() <= blockNumber;
            if (blockNumberAvailable &&
                    !replicatesService.hasWorkerAlreadyParticipated(chainTaskId, walletAddress) &&
                    replicatesService.moreReplicatesNeeded(chainTaskId, task.getNumWorkersNeeded(), task.getMaxExecutionTime())) {
                replicatesService.addNewReplicate(chainTaskId, walletAddress);
                workerService.addChainTaskIdToWorker(chainTaskId, walletAddress);
                return replicatesService.getReplicate(chainTaskId, walletAddress);
            }
        }

        return Optional.empty();
    }

    void tryUpgradeTaskStatus(String chainTaskId) {
        Optional<Task> optional = getTaskByChainTaskId(chainTaskId);
        if (!optional.isPresent()) {
            return;
        }
        Task task = optional.get();

        switch (task.getCurrentStatus()) {
            case RECEIVED:
                received2Initialized(task);
                break;
            case INITIALIZED:
                initialized2Running(task);
                initializedOrRunning2ContributionTimeout(task);
                break;
            case RUNNING:
                running2ConsensusReached(task);
                initializedOrRunning2ContributionTimeout(task);
                break;
            case CONSENSUS_REACHED:
                consensusReached2AtLeastOneReveal2UploadRequested(task);
                consensusReached2Reopened(task);
                break;
            case RESULT_UPLOAD_REQUESTED:
                uploadRequested2UploadingResult(task);
                uploadRequested2UploadRequestTimeout(task);
                break;
            case RESULT_UPLOADING:
                resultUploading2Uploaded(task);
                resultUploading2UploadTimeout(task);
                break;
            case RESULT_UPLOADED:
                resultUploaded2Finalized2Completed(task);
                break;
        }
    }

    private Task updateTaskStatusAndSave(Task task, TaskStatus newStatus) {
        return updateTaskStatusAndSave(task, newStatus, null);
    }

    private Task updateTaskStatusAndSave(Task task, TaskStatus newStatus, ChainReceipt chainReceipt) {
        TaskStatus currentStatus = task.getCurrentStatus();
        task.changeStatus(newStatus, chainReceipt);
        Task savedTask = taskRepository.save(task);
        log.info("UpdateTaskStatus suceeded [chainTaskId:{}, currentStatus:{}, newStatus:{}]", task.getChainTaskId(), currentStatus, newStatus);
        return savedTask;
    }

    private void received2Initialized(Task task) {
        boolean isCurrentStatusReceived = task.getCurrentStatus().equals(RECEIVED);

        if (!isCurrentStatusReceived) {
            log.error("Cannot initialize [chainTaskId:{}, currentStatus:{}]",
                    task.getChainTaskId(), task.getCurrentStatus());
            return;
        }

        boolean canInitialize = iexecHubService.canInitialize(task.getChainDealId(), task.getTaskIndex());
        boolean hasEnoughGas = iexecHubService.hasEnoughGas();

        if (!canInitialize || !hasEnoughGas) {
            log.error("Cant initialize [chainTaskId:{}, canInitialize:{}, hasEnoughGas:{}]",
                    task.getChainTaskId(), canInitialize, hasEnoughGas);
            return;
        }

        updateTaskStatusAndSave(task, INITIALIZING);

        Optional<Pair<String, ChainReceipt>> optionalPair = iexecHubService.initialize(
                task.getChainDealId(), task.getTaskIndex());

        if (!optionalPair.isPresent()) {
            return;
        }

        String existingChainTaskId = task.getChainTaskId();
        String chainTaskId = optionalPair.get().getLeft();
        ChainReceipt chainReceipt = optionalPair.get().getRight();

        if (chainTaskId.isEmpty() || !chainTaskId.equalsIgnoreCase(existingChainTaskId)) {
            log.error("Initialize failed [existingChainTaskId:{}, returnedChainTaskId:{}]",
                    existingChainTaskId, chainTaskId);
            updateTaskStatusAndSave(task, INITIALIZE_FAILED);
            updateTaskStatusAndSave(task, FAILED);
            return;
        }

        Optional<ChainTask> optional = iexecHubService.getChainTask(chainTaskId);
        if (!optional.isPresent()) {
            return;
        }
        ChainTask chainTask = optional.get();

        task.setContributionDeadline(new Date(chainTask.getContributionDeadline()));
        task.setFinalDeadline(new Date(chainTask.getFinalDeadline()));
        long receiptBlockNumber = chainReceipt != null ? chainReceipt.getBlockNumber() : 0;
        if (receiptBlockNumber != 0) {
            task.setInitializationBlockNumber(receiptBlockNumber);
        }
        //TODO Put other fields?

        updateTaskStatusAndSave(task, INITIALIZED, chainReceipt);
        replicatesService.createEmptyReplicateList(chainTaskId);
    }

    private void initialized2Running(Task task) {
        String chainTaskId = task.getChainTaskId();
        boolean condition1 = replicatesService.getNbReplicatesWithCurrentStatus(chainTaskId, ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED) > 0;
        boolean condition2 = replicatesService.getNbReplicatesWithCurrentStatus(chainTaskId, ReplicateStatus.COMPUTED) < task.getNumWorkersNeeded();
        boolean condition3 = task.getCurrentStatus().equals(INITIALIZED);

        if (condition1 && condition2 && condition3) {
            updateTaskStatusAndSave(task, RUNNING);
        }
    }

    private void running2ConsensusReached(Task task) {
        boolean isTaskInRunningStatus = task.getCurrentStatus().equals(RUNNING);

        Optional<ChainTask> optional = iexecHubService.getChainTask(task.getChainTaskId());
        if (!optional.isPresent()) {
            return;
        }
        ChainTask chainTask = optional.get();

        boolean isChainTaskRevealing = chainTask.getStatus().equals(ChainTaskStatus.REVEALING);

        int onChainWinners = chainTask.getWinnerCounter();
        int offChainWinners = replicatesService.getNbOffChainReplicatesWithStatus(task.getChainTaskId(), ReplicateStatus.CONTRIBUTED);
        boolean offChainWinnersEqualsOnChainWinners = offChainWinners == onChainWinners;

        if (isTaskInRunningStatus && isChainTaskRevealing && offChainWinnersEqualsOnChainWinners) {

            // change the the revealDeadline and consensus of the task from the chainTask info
            task.setRevealDeadline(new Date(chainTask.getRevealDeadline()));
            task.setConsensus(chainTask.getConsensusValue());
            updateTaskStatusAndSave(task, CONSENSUS_REACHED);

            applicationEventPublisher.publishEvent(ConsensusReachedEvent.builder()
                    .chainTaskId(task.getChainTaskId())
                    .consensus(task.getConsensus())
                    .build());
        }
    }

    private void initializedOrRunning2ContributionTimeout(Task task) {
        boolean isInitializedOrRunningTask = task.getCurrentStatus().equals(INITIALIZED) ||
                task.getCurrentStatus().equals(RUNNING);
        boolean isNowAfterContributionDeadline = task.getContributionDeadline() != null && new Date().after(task.getContributionDeadline());

        Optional<ChainTask> optional = iexecHubService.getChainTask(task.getChainTaskId());
        if (!optional.isPresent()) {
            return;
        }
        ChainTask chainTask = optional.get();

        boolean isChainTaskActive = chainTask.getStatus() != null && chainTask.getStatus().equals(ChainTaskStatus.ACTIVE);

        if (isInitializedOrRunningTask && isChainTaskActive && isNowAfterContributionDeadline) {
            updateTaskStatusAndSave(task, CONTRIBUTION_TIMEOUT);
            updateTaskStatusAndSave(task, FAILED);
            applicationEventPublisher.publishEvent(ContributionTimeoutEvent.builder()
                    .chainTaskId(task.getChainTaskId())
                    .build());
        }
    }

    private void consensusReached2AtLeastOneReveal2UploadRequested(Task task) {
        boolean condition1 = task.getCurrentStatus().equals(CONSENSUS_REACHED);
        boolean condition2 = replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.REVEALED) > 0;

        if (condition1 && condition2) {
            updateTaskStatusAndSave(task, AT_LEAST_ONE_REVEALED);
            requestUpload(task);
        }
    }

    public void consensusReached2Reopened(Task task) {
        Date now = new Date();

        boolean isConsensusReachedStatus = task.getCurrentStatus().equals(CONSENSUS_REACHED);
        boolean isAfterRevealDeadline = task.getRevealDeadline() != null && now.after(task.getRevealDeadline());
        boolean hasAtLeastOneReveal = replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.REVEALED) > 0;

        if (!isConsensusReachedStatus || !isAfterRevealDeadline || hasAtLeastOneReveal) {
            return;
        }

        boolean canReopen = iexecHubService.canReopen(task.getChainTaskId());
        boolean hasEnoughGas = iexecHubService.hasEnoughGas();

        if (!canReopen || !hasEnoughGas) {
            return;
        }

        updateTaskStatusAndSave(task, REOPENING);
        Optional<ChainReceipt> optionalChainReceipt = iexecHubService.reOpen(task.getChainTaskId());

        if (!optionalChainReceipt.isPresent()) {
            log.error("Reopen failed [chainTaskId:{}, canReopen:{}, hasEnoughGas:{}]",
                    task.getChainTaskId(), canReopen, hasEnoughGas);
            updateTaskStatusAndSave(task, REOPEN_FAILED);
            updateTaskStatusAndSave(task, FAILED);
            return;
        }

        task.setConsensus(null);
        task.setRevealDeadline(new Date(0));
        updateTaskStatusAndSave(task, REOPENED, optionalChainReceipt.get());
        updateTaskStatusAndSave(task, INITIALIZED, optionalChainReceipt.get());
    }

    private void uploadRequested2UploadingResult(Task task) {
        boolean isTaskInUploadRequested = task.getCurrentStatus().equals(RESULT_UPLOAD_REQUESTED);
        boolean isThereAWorkerUploading = replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.RESULT_UPLOADING) > 0;

        if (isTaskInUploadRequested) {
            if (isThereAWorkerUploading) {
                updateTaskStatusAndSave(task, RESULT_UPLOADING);
            } else {
                requestUpload(task);
            }
        }
    }

    private void uploadRequested2UploadRequestTimeout(Task task) {
        boolean isTaskInUploadRequested = task.getCurrentStatus().equals(RESULT_UPLOAD_REQUESTED);
        boolean isNowAfterFinalDeadline = task.getFinalDeadline() != null
                                        && new Date().after(task.getFinalDeadline());

        if (isTaskInUploadRequested && isNowAfterFinalDeadline) {
            updateTaskStatusAndSave(task, RESULT_UPLOAD_REQUEST_TIMEOUT);
            applicationEventPublisher.publishEvent(ResultUploadTimeoutEvent.builder()
                    .chainTaskId(task.getChainTaskId())
                    .build());
            updateTaskStatusAndSave(task, FAILED);
        }
    }

    private void resultUploading2Uploaded(Task task) {
        boolean condition1 = task.getCurrentStatus().equals(RESULT_UPLOADING);
        boolean condition2 = replicatesService.getNbReplicatesContainingStatus(task.getChainTaskId(), ReplicateStatus.RESULT_UPLOADED) > 0;

        if (condition1 && condition2) {
            updateTaskStatusAndSave(task, RESULT_UPLOADED);
            resultUploaded2Finalized2Completed(task);
        } else if (replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.RESULT_UPLOAD_REQUEST_FAILED) > 0 &&
                replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.RESULT_UPLOADING) == 0) {
            // need to request upload again
            requestUpload(task);
        }
    }

    private void resultUploading2UploadTimeout(Task task) {
        boolean isTaskInResultUploading = task.getCurrentStatus().equals(RESULT_UPLOADING);
        boolean isNowAfterFinalDeadline = task.getFinalDeadline() != null
                                        && new Date().after(task.getFinalDeadline());

        if (isTaskInResultUploading && isNowAfterFinalDeadline) {
            updateTaskStatusAndSave(task, RESULT_UPLOAD_TIMEOUT);
            applicationEventPublisher.publishEvent(ResultUploadTimeoutEvent.builder()
                    .chainTaskId(task.getChainTaskId())
                    .build());
            updateTaskStatusAndSave(task, FAILED);
        }
    }

    private void requestUpload(Task task) {

        Optional<Replicate> optionalReplicate = replicatesService.getRandomReplicateWithRevealStatus(task.getChainTaskId());
        if (optionalReplicate.isPresent()) {
            Replicate replicate = optionalReplicate.get();

            // save in the task the workerWallet that is in charge of uploading the result
            task.setUploadingWorkerWalletAddress(replicate.getWalletAddress());
            updateTaskStatusAndSave(task, RESULT_UPLOAD_REQUESTED);

            applicationEventPublisher.publishEvent(new PleaseUploadEvent(task.getChainTaskId(), replicate.getWalletAddress()));
        }
    }

    private void resultUploaded2Finalized2Completed(Task task) {
        boolean isTaskInResultUploaded = task.getCurrentStatus().equals(RESULT_UPLOADED);
        boolean canFinalize = iexecHubService.canFinalize(task.getChainTaskId());

        Optional<ChainTask> optional = iexecHubService.getChainTask(task.getChainTaskId());
        if (!optional.isPresent()) {
            return;
        }
        ChainTask chainTask = optional.get();

        int onChainReveal = chainTask.getRevealCounter();
        int offChainReveal = replicatesService.getNbReplicatesContainingStatus(task.getChainTaskId(), ReplicateStatus.REVEALED);
        boolean offChainRevealEqualsOnChainReveal = offChainReveal == onChainReveal;

        if (!isTaskInResultUploaded || !canFinalize || !offChainRevealEqualsOnChainReveal) {
            return;
        }

        if (!iexecHubService.hasEnoughGas()) {
            return;
        }

        updateTaskStatusAndSave(task, FINALIZING);
        String resultUri = resultRepositoryConfig.getResultRepositoryURL()
                + "/results/" + task.getChainTaskId();
        Optional<ChainReceipt> optionalChainReceipt = iexecHubService.finalizeTask(task.getChainTaskId(), resultUri);

        if (!optionalChainReceipt.isPresent()) {
            log.error("Finalize failed [chainTaskId:{} canFinalize:{}, isAfterRevealDeadline:{}, hasAtLeastOneReveal:{}]",
                    task.getChainTaskId(), isTaskInResultUploaded, canFinalize, offChainRevealEqualsOnChainReveal);
            updateTaskStatusAndSave(task, FINALIZE_FAILED);
            updateTaskStatusAndSave(task, FAILED);
            return;
        }

        updateTaskStatusAndSave(task, FINALIZED, optionalChainReceipt.get());

        updateTaskStatusAndSave(task, COMPLETED);
        applicationEventPublisher.publishEvent(new TaskCompletedEvent(task));
    }
}