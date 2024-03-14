/*
 * Copyright 2021-2024 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.core.task.update;

import com.iexec.blockchain.api.BlockchainAdapterService;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusUpdate;
import com.iexec.commons.poco.chain.ChainReceipt;
import com.iexec.commons.poco.chain.ChainTask;
import com.iexec.commons.poco.chain.ChainTaskStatus;
import com.iexec.commons.poco.task.TaskDescription;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.replicate.ReplicatesList;
import com.iexec.core.replicate.ReplicatesService;
import com.iexec.core.sms.SmsService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.TaskStatus;
import com.iexec.core.task.TaskStatusChange;
import com.iexec.core.task.event.*;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.iexec.common.replicate.ReplicateStatus.RESULT_UPLOAD_REQUESTED;
import static com.iexec.core.task.TaskStatus.*;

@Service
@Slf4j
class TaskUpdateManager {
    public static final String METRIC_TASKS_STATUSES_COUNT = "iexec.core.tasks.count";

    private final TaskService taskService;
    private final IexecHubService iexecHubService;
    private final ReplicatesService replicatesService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final WorkerService workerService;
    private final BlockchainAdapterService blockchainAdapterService;
    private final SmsService smsService;

    private final LinkedHashMap<TaskStatus, AtomicLong> currentTaskStatusesCount;

    public TaskUpdateManager(TaskService taskService,
                             IexecHubService iexecHubService,
                             ReplicatesService replicatesService,
                             ApplicationEventPublisher applicationEventPublisher,
                             WorkerService workerService,
                             BlockchainAdapterService blockchainAdapterService,
                             SmsService smsService) {
        this.taskService = taskService;
        this.iexecHubService = iexecHubService;
        this.replicatesService = replicatesService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.workerService = workerService;
        this.blockchainAdapterService = blockchainAdapterService;
        this.smsService = smsService;

        this.currentTaskStatusesCount = Arrays.stream(TaskStatus.values())
                .collect(Collectors.toMap(
                        Function.identity(),
                        status -> new AtomicLong(),
                        (a, b) -> b,
                        LinkedHashMap::new));

        for (TaskStatus status : TaskStatus.values()) {
            Gauge.builder(METRIC_TASKS_STATUSES_COUNT, () -> currentTaskStatusesCount.get(status).get())
                    .tags(
                            "period", "current",
                            "status", status.name()
                    ).register(Metrics.globalRegistry);
        }
    }

    @PostConstruct
    Future<Void> init() {
        final ExecutorService taskStatusesCountExecutor = Executors.newSingleThreadExecutor();
        final Future<Void> future = taskStatusesCountExecutor.submit(
                this::initializeCurrentTaskStatusesCount,
                null    // Trick to get a `Future<Void>` instead of a `Future<?>`
        );
        taskStatusesCountExecutor.shutdown();
        return future;
    }

    /**
     * The following could take a bit of time, depending on how many tasks are in DB.
     * It is expected to take ~1.7s for 1,000,000 tasks and to be linear (so, ~17s for 10,000,000 tasks).
     * As we use AtomicLongs, the final count should be accurate - no race conditions to expect,
     * even though new deals are detected during the count.
     */
    private void initializeCurrentTaskStatusesCount() {
        currentTaskStatusesCount
                .entrySet()
                .parallelStream()
                .forEach(entry -> entry.getValue().addAndGet(taskService.countByCurrentStatus(entry.getKey())));
        publishTaskStatusesCountUpdate();
    }

    void updateTask(String chainTaskId) {
        Optional<Task> optional = taskService.getTaskByChainTaskId(chainTaskId);
        if (optional.isEmpty()) {
            return;
        }
        Task task = optional.get();
        TaskStatus currentStatus = task.getCurrentStatus();

        boolean isFinalDeadlinePossible =
                !TaskStatus.getStatusesWhereFinalDeadlineIsImpossible().contains(currentStatus);
        if (isFinalDeadlinePossible && new Date().after(task.getFinalDeadline())) {
            // Eventually should fire a "final deadline reached" notification to worker,
            // but here let's just trigger an toFailed(task) leading to a failed status
            // which will itself fire a generic "abort" notification
            toFailed(task, FINAL_DEADLINE_REACHED);
            return;
        }

        switch (currentStatus) {
            case RECEIVED:
                received2Initializing(task);
                break;
            case INITIALIZING:
                initializing2Initialized(task);
                break;
            case INITIALIZED:
                initialized2Running(task);
                initializedOrRunning2ContributionTimeout(task);
                break;
            case RUNNING:
                running2Finalized2Completed(task); // running2Finalized2Completed must be the first call to prevent other transition execution
                running2ConsensusReached(task);
                running2RunningFailed(task);
                initializedOrRunning2ContributionTimeout(task);
                break;
            case CONSENSUS_REACHED:
                consensusReached2AtLeastOneReveal2ResultUploading(task);
                consensusReached2Reopening(task);
                break;
            case AT_LEAST_ONE_REVEALED:
                requestUpload(task);
                break;
            case REOPENING:
                reopening2Reopened(task);
                break;
            case REOPENED:
                updateTaskStatusAndSave(task, INITIALIZED);
                break;
            case RESULT_UPLOADING:
                resultUploading2Uploaded(task);
                resultUploading2UploadTimeout(task);
                break;
            case RESULT_UPLOADED:
                resultUploaded2Finalizing(task);
                break;
            case FINALIZING:
                finalizing2Finalized2Completed(task);
                break;
            case FINALIZED:
                finalizedToCompleted(task);
                break;
            case INITIALIZE_FAILED:
            case RUNNING_FAILED:
            case CONTRIBUTION_TIMEOUT:
            case REOPEN_FAILED:
            case RESULT_UPLOAD_TIMEOUT:
            case FINALIZE_FAILED:
            case FINAL_DEADLINE_REACHED:
                toFailed(task);
                break;
            case COMPLETED:
            case FAILED:
                break;
        }
    }

    /**
     * Creates one or several task status changes for the task before committing all of them to the database.
     *
     * @param task     The task
     * @param statuses List of statuses to append to the task {@code dateStatusList}
     */
    void updateTaskStatusesAndSave(Task task, TaskStatus... statuses) {
        TaskStatus currentStatus = task.getCurrentStatus();
        List<TaskStatusChange> statusChanges = new ArrayList<>();
        for (TaskStatus newStatus : statuses) {
            log.info("Create TaskStatusChange succeeded [chainTaskId:{}, currentStatus:{}, newStatus:{}]",
                    task.getChainTaskId(), task.getCurrentStatus(), newStatus);
            final TaskStatusChange statusChange = TaskStatusChange.builder().status(newStatus).build();
            task.setCurrentStatus(newStatus);
            task.getDateStatusList().add(statusChange);
            statusChanges.add(statusChange);
        }
        saveTask(task, currentStatus, statusChanges);
    }

    void updateTaskStatusAndSave(Task task, TaskStatus newStatus) {
        updateTaskStatusAndSave(task, newStatus, null);
    }

    void updateTaskStatusAndSave(Task task, TaskStatus newStatus, ChainReceipt chainReceipt) {
        TaskStatus currentStatus = task.getCurrentStatus();
        TaskStatusChange statusChange = TaskStatusChange.builder().status(newStatus).chainReceipt(chainReceipt).build();
        task.setCurrentStatus(newStatus);
        task.getDateStatusList().add(statusChange);
        saveTask(task, currentStatus, List.of(statusChange));
    }

    /**
     * Saves the task to the database.
     *
     * @param task          The task
     * @param currentStatus The current status in database
     * @param statusChanges List of changes
     */
    void saveTask(Task task, TaskStatus currentStatus, List<TaskStatusChange> statusChanges) {
        Optional<Task> savedTask = taskService.updateTaskStatus(task, statusChanges);
        // `savedTask.isPresent()` should always be true if the task exists in the repository.
        if (savedTask.isPresent()) {
            updateMetricsAfterStatusUpdate(currentStatus, task.getCurrentStatus());
            log.info("UpdateTaskStatus succeeded [chainTaskId:{}, currentStatus:{}, newStatus:{}]",
                    task.getChainTaskId(), currentStatus, task.getCurrentStatus());
        } else {
            log.warn("UpdateTaskStatus failed. Chain Task is probably unknown [chainTaskId:{}, currentStatus:{}, wishedStatus:{}]",
                    task.getChainTaskId(), currentStatus, task.getCurrentStatus());
        }
    }

    void received2Initializing(Task task) {
        if (task.getCurrentStatus() != RECEIVED) {
            emitError(task, RECEIVED, "received2Initializing");
            return;
        }

        boolean hasEnoughGas = iexecHubService.hasEnoughGas();
        boolean isTaskUnsetOnChain = iexecHubService.isTaskInUnsetStatusOnChain(task.getChainDealId(), task.getTaskIndex());
        boolean isBeforeContributionDeadline = iexecHubService.isBeforeContributionDeadline(task.getChainDealId());

        if (!hasEnoughGas || !isTaskUnsetOnChain || !isBeforeContributionDeadline) {
            log.error("Cannot initialize task [chainTaskId:{}, hasEnoughGas:{}, "
                            + "isTaskUnsetOnChain:{}, isBeforeContributionDeadline:{}]",
                    task.getChainTaskId(), hasEnoughGas, isTaskUnsetOnChain,
                    isBeforeContributionDeadline);
            return;
        }

        Update update = new Update();
        if (task.isTeeTask()) {
            Optional<String> smsUrl = smsService.getVerifiedSmsUrl(task.getChainTaskId(), task.getTag());
            if (smsUrl.isEmpty()) {
                log.error("Couldn't get verified SMS url [chainTaskId: {}]", task.getChainTaskId());
                toFailed(task, INITIALIZE_FAILED);
                return;
            }
            task.setSmsUrl(smsUrl.get()); //SMS URL source of truth for the task
            update.set("smsUrl", smsUrl.get());
        }
        final Optional<String> enclaveChallenge = smsService.getEnclaveChallenge(task.getChainTaskId(), task.getSmsUrl());
        if (enclaveChallenge.isEmpty()) {
            log.error("Can't initialize task, enclave challenge is empty [chainTaskId:{}]", task.getChainTaskId());
            toFailed(task, INITIALIZE_FAILED);
            return;
        }
        task.setEnclaveChallenge(enclaveChallenge.get());
        update.set("enclaveChallenge", enclaveChallenge.get());
        taskService.updateTask(task.getChainTaskId(), update);

        blockchainAdapterService
                .requestInitialize(task.getChainDealId(), task.getTaskIndex())
                .filter(chainTaskId -> chainTaskId.equalsIgnoreCase(task.getChainTaskId()))
                .ifPresentOrElse(chainTaskId -> {
                    log.info("Requested initialize on blockchain [chainTaskId:{}]",
                            task.getChainTaskId());
                    updateTaskStatusAndSave(task, INITIALIZING);
                    //Watch initializing to initialized
                    updateTask(task.getChainTaskId());
                }, () -> {
                    log.error("Failed to request initialize on blockchain [chainTaskId:{}]",
                            task.getChainTaskId());
                    toFailed(task, INITIALIZE_FAILED);
                });
    }

    void initializing2Initialized(Task task) {
        if (task.getCurrentStatus() != INITIALIZING) {
            emitError(task, INITIALIZING, "initializing2Initialized");
            return;
        }
        // TODO: the block where initialization happened can be found
        blockchainAdapterService
                .isInitialized(task.getChainTaskId())
                .ifPresentOrElse(isSuccess -> {
                    if (Boolean.TRUE.equals(isSuccess)) {
                        log.info("Initialized on blockchain (tx mined) [chainTaskId:{}]",
                                task.getChainTaskId());
                        //Without receipt, using deal block for initialization block
                        task.setInitializationBlockNumber(task.getDealBlockNumber());
                        replicatesService.createEmptyReplicateList(task.getChainTaskId());
                        updateTaskStatusAndSave(task, INITIALIZED, null);
                        return;
                    }
                    log.error("Initialization failed on blockchain (tx reverted) [chainTaskId:{}]",
                            task.getChainTaskId());
                    toFailed(task, INITIALIZE_FAILED);
                }, () -> log.error("Unable to check initialization on blockchain " +
                                "(likely too long), should use a detector [chainTaskId:{}]",
                        task.getChainTaskId()));
    }

    void initialized2Running(Task task) {
        if (task.getCurrentStatus() != INITIALIZED) {
            emitError(task, INITIALIZED, "initialized2Running");
            return;
        }
        String chainTaskId = task.getChainTaskId();

        // We explicitly exclude START_FAILED as it could denote some serious issues
        // The task should not transition to `RUNNING` in this case.
        final List<ReplicateStatus> acceptableStatus = List.of(
                ReplicateStatus.STARTED,
                ReplicateStatus.APP_DOWNLOADING,
                ReplicateStatus.APP_DOWNLOAD_FAILED,
                ReplicateStatus.APP_DOWNLOADED,
                ReplicateStatus.DATA_DOWNLOADING,
                ReplicateStatus.DATA_DOWNLOAD_FAILED,
                ReplicateStatus.DATA_DOWNLOADED,
                ReplicateStatus.COMPUTING,
                ReplicateStatus.COMPUTE_FAILED,
                ReplicateStatus.COMPUTED,
                ReplicateStatus.CONTRIBUTING,
                ReplicateStatus.CONTRIBUTE_FAILED,
                ReplicateStatus.CONTRIBUTED
        );
        final List<Replicate> replicates = replicatesService.getReplicates(chainTaskId);
        final long nbReplicatesContainingStartingStatus = replicates.stream()
                .filter(replicate -> acceptableStatus.contains(replicate.getLastRelevantStatus()))
                .count();

        if (nbReplicatesContainingStartingStatus > 0) {
            updateTaskStatusAndSave(task, RUNNING);
        }

        if (replicates.stream().anyMatch(replicate -> replicate.getLastRelevantStatus() == ReplicateStatus.CONTRIBUTED)) {
            log.warn("At least a worker has already contributed before the task was updated to RUNNING [chainTaskId:{}]",
                    chainTaskId);
            updateTask(chainTaskId);
        }
    }

    void running2ConsensusReached(Task task) {
        if (task.getCurrentStatus() != RUNNING) {
            emitError(task, RUNNING, "running2ConsensusReached");
            return;
        }
        final String chainTaskId = task.getChainTaskId();
        final Optional<ReplicatesList> oReplicatesList = replicatesService.getReplicatesList(chainTaskId);
        if (oReplicatesList.isEmpty()) {
            log.error("Can't transition task to `ConsensusReached` when no replicatesList exists [chainTaskId:{}]",
                    chainTaskId);
            return;
        }
        boolean isConsensusReached = taskService.isConsensusReached(oReplicatesList.get());

        if (isConsensusReached) {
            Optional<ChainTask> optional = iexecHubService.getChainTask(chainTaskId);
            if (optional.isEmpty()) return;
            ChainTask chainTask = optional.get();

            // change the revealDeadline and consensus of the task from the chainTask info
            task.setRevealDeadline(new Date(chainTask.getRevealDeadline()));
            task.setConsensus(chainTask.getConsensusValue());
            long consensusBlockNumber = iexecHubService.getConsensusBlock(chainTaskId, task.getInitializationBlockNumber()).getBlockNumber();
            task.setConsensusReachedBlockNumber(consensusBlockNumber);
            taskService.updateTask(task.getChainTaskId(),
                    Update.update("revealDeadline", task.getRevealDeadline())
                            .set("consensus", task.getConsensus())
                            .set("consensusReachedBlockNumber", task.getConsensusReachedBlockNumber()));
            updateTaskStatusAndSave(task, CONSENSUS_REACHED);

            applicationEventPublisher.publishEvent(ConsensusReachedEvent.builder()
                    .chainTaskId(chainTaskId)
                    .consensus(task.getConsensus())
                    .blockNumber(task.getConsensusReachedBlockNumber())
                    .build());
        }
    }

    void running2Finalized2Completed(Task task) {
        if (task.getCurrentStatus() != RUNNING) {
            emitError(task, RUNNING, "running2Finalized2Completed");
            return;
        }
        final String chainTaskId = task.getChainTaskId();

        final TaskDescription taskDescription = iexecHubService.getTaskDescription(task.getChainTaskId());
        if (!taskDescription.isEligibleToContributeAndFinalize()) {
            log.debug("Task not running in a TEE, flow running2Finalized2Completed is not possible [chainTaskId:{}]",
                    chainTaskId);
            return;
        }

        final Optional<ReplicatesList> oReplicatesList = replicatesService.getReplicatesList(chainTaskId);
        if (oReplicatesList.isEmpty()) {
            log.error("Can't transition task to `Finalized` or `Completed` when no replicatesList exists [chainTaskId:{}]",
                    chainTaskId);
            return;
        }

        final ReplicatesList replicates = oReplicatesList.get();
        final int nbReplicatesWithContributeAndFinalizeStatus = replicates.getNbReplicatesWithCurrentStatus(ReplicateStatus.CONTRIBUTE_AND_FINALIZE_DONE);

        if (nbReplicatesWithContributeAndFinalizeStatus == 0) {
            log.debug("No replicate in ContributeAndFinalize status [chainTaskId:{}]",
                    chainTaskId);
            return;
        } else if (nbReplicatesWithContributeAndFinalizeStatus > 1) {
            log.error("Too many replicates in ContributeAndFinalize status [chainTaskId:{}, nbReplicates:{}]",
                    chainTaskId, nbReplicatesWithContributeAndFinalizeStatus);
            toFailed(task, RUNNING_FAILED);
            return;
        }

        toFinalizedToCompleted(task);
    }

    void initializedOrRunning2ContributionTimeout(Task task) {
        if (task.getCurrentStatus() != INITIALIZED && task.getCurrentStatus() != RUNNING) {
            emitError(task, INITIALIZED, "initializedOrRunning2ContributionTimeout");
            emitError(task, RUNNING, "initializedOrRunning2ContributionTimeout");
            return;
        }
        if (task.getContributionDeadline() != null && new Date().after(task.getContributionDeadline())) {
            updateTaskStatusesAndSave(task, CONTRIBUTION_TIMEOUT, FAILED);
            applicationEventPublisher.publishEvent(new ContributionTimeoutEvent(task.getChainTaskId()));
        }
    }

    /**
     * In TEE mode, when all workers have failed to run the task, we send the new status as soon as possible.
     * It avoids a state within which we know the task won't be updated anymore, but we wait for a timeout.
     * <br>
     * We consider that all workers are in a `RUNNING_FAILED` status
     * when all alive workers have tried to run the task, but they have failed.
     *
     * @param task Task to check and to make become {@link TaskStatus#RUNNING_FAILED}.
     */
    void running2RunningFailed(Task task) {
        if (task.getCurrentStatus() != RUNNING) {
            emitError(task, RUNNING, "running2RunningFailed");
            return;
        }
        if (!task.isTeeTask()) {
            return;
        }

        final ReplicatesList replicatesList = replicatesService.getReplicatesList(task.getChainTaskId()).orElseThrow();
        final List<Worker> aliveWorkers = workerService.getAliveWorkers();

        final List<Replicate> replicatesOfAliveWorkers = aliveWorkers
                .stream()
                .map(Worker::getWalletAddress)
                .map(replicatesList::getReplicateOfWorker)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        // If at least an alive worker has not run the task, it is not a `RUNNING_FAILURE`.
        final boolean allAliveWorkersTried = replicatesOfAliveWorkers.size() == aliveWorkers.size();

        if (!allAliveWorkersTried) {
            return;
        }

        // If not all alive workers have failed while running the task, that's not a running failure.
        boolean notAllReplicatesFailed = replicatesOfAliveWorkers
                .stream()
                .map(Replicate::getLastRelevantStatus)
                .anyMatch(Predicate.not(ReplicateStatus::isFailedBeforeComputed));

        if (notAllReplicatesFailed) {
            return;
        }

        // If all alive workers have failed on this task, its computation should be stopped.
        // It could denote that the task is wrong
        // - e.g. failing script, dataset can't be retrieved, app can't be downloaded, ...
        updateTaskStatusesAndSave(task, RUNNING_FAILED, FAILED);
        applicationEventPublisher.publishEvent(new TaskRunningFailedEvent(task.getChainTaskId()));
    }

    void consensusReached2AtLeastOneReveal2ResultUploading(Task task) {
        if (task.getCurrentStatus() != CONSENSUS_REACHED) {
            emitError(task, CONSENSUS_REACHED, "consensusReached2AtLeastOneReveal2ResultUploading");
            return;
        }
        if (replicatesService.getNbReplicatesWithCurrentStatus(task.getChainTaskId(), ReplicateStatus.REVEALED) > 0) {
            updateTaskStatusAndSave(task, AT_LEAST_ONE_REVEALED);
            requestUpload(task);
        }
    }

    void consensusReached2Reopening(Task task) {
        Date now = new Date();

        boolean isConsensusReachedStatus = task.getCurrentStatus() == CONSENSUS_REACHED;
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
        //TODO Update reopen call
        Optional<ChainReceipt> optionalChainReceipt = Optional.empty(); //iexecHubService.reOpen(task.getChainTaskId());

        if (optionalChainReceipt.isEmpty()) {
            log.error("Reopen failed [chainTaskId:{}]", task.getChainTaskId());
            updateTaskStatusAndSave(task, REOPEN_FAILED);
            updateTaskStatusAndSave(task, FAILED);
            return;
        }

        reopening2Reopened(task, optionalChainReceipt.get());
    }

    void reopening2Reopened(Task task) {
        reopening2Reopened(task, null);
    }

    void reopening2Reopened(Task task, ChainReceipt chainReceipt) {
        Optional<ChainTask> oChainTask = iexecHubService.getChainTask(task.getChainTaskId());
        if (oChainTask.isEmpty()) {
            return;
        }
        ChainTask chainTask = oChainTask.get();

        // re-initialize the task if it has been reopened
        if (chainTask.getStatus() == ChainTaskStatus.ACTIVE) {

            // set replicates to REVEAL_TIMEOUT
            for (Replicate replicate : replicatesService.getReplicates(task.getChainTaskId())) {
                replicatesService.setRevealTimeoutStatusIfNeeded(task.getChainTaskId(), replicate);
            }

            task.setConsensus(null);
            task.setRevealDeadline(new Date(0));

            updateTaskStatusAndSave(task, REOPENED, chainReceipt);
            updateTaskStatusAndSave(task, INITIALIZED, chainReceipt);
        }
    }

    void resultUploading2Uploaded(Task task) {
        if (task.getCurrentStatus() != RESULT_UPLOADING) {
            emitError(task, RESULT_UPLOADING, "resultUploading2Uploaded");
            return;
        }
        final Replicate uploadedReplicate = replicatesService.getReplicateWithResultUploadedStatus(task.getChainTaskId())
                .orElse(null);

        if (uploadedReplicate != null) {
            task.setResultLink(uploadedReplicate.getResultLink());
            task.setChainCallbackData(uploadedReplicate.getChainCallbackData());
            taskService.updateTask(task.getChainTaskId(),
                    Update.update("resultLink", uploadedReplicate.getResultLink())
                            .set("chainCallbackData", uploadedReplicate.getChainCallbackData()));
            updateTaskStatusAndSave(task, RESULT_UPLOADED);
            resultUploaded2Finalizing(task);
            return;
        }

        String uploadingReplicateAddress = task.getUploadingWorkerWalletAddress();

        if (uploadingReplicateAddress == null || uploadingReplicateAddress.isEmpty()) {
            requestUpload(task);
            return;
        }

        Optional<Replicate> oReplicate = replicatesService.getReplicate(task.getChainTaskId(), uploadingReplicateAddress);

        if (oReplicate.isEmpty()) {
            requestUpload(task);
            return;
        }

        Replicate replicate = oReplicate.get();

        boolean isReplicateUploading = replicate.getCurrentStatus() == ReplicateStatus.RESULT_UPLOADING;
        boolean isReplicateRecoveringToUpload = replicate.getCurrentStatus() == ReplicateStatus.RECOVERING &&
                replicate.getLastRelevantStatus() == ReplicateStatus.RESULT_UPLOADING;

        if (!isReplicateUploading && !isReplicateRecoveringToUpload) {
            requestUpload(task);
        }
    }

    void resultUploading2UploadTimeout(Task task) {
        if (task.getCurrentStatus() != RESULT_UPLOADING) {
            emitError(task, RESULT_UPLOADING, "resultUploading2UploadTimeout");
            return;
        }
        boolean isNowAfterFinalDeadline = task.getFinalDeadline() != null
                && new Date().after(task.getFinalDeadline());

        if (isNowAfterFinalDeadline) {
            applicationEventPublisher.publishEvent(new ResultUploadTimeoutEvent(task.getChainTaskId()));
            toFailed(task, RESULT_UPLOAD_TIMEOUT);
        }
    }

    void requestUpload(Task task) {
        boolean isThereAWorkerUploading = replicatesService
                .getNbReplicatesWithCurrentStatus(task.getChainTaskId(),
                        ReplicateStatus.RESULT_UPLOADING,
                        RESULT_UPLOAD_REQUESTED) > 0;

        if (isThereAWorkerUploading) {
            log.info("Upload is requested but an upload is already in process. [chainTaskId: {}]", task.getChainTaskId());
            return;
        }

        Optional<Replicate> optionalReplicate = replicatesService.getRandomReplicateWithRevealStatus(task.getChainTaskId());
        if (optionalReplicate.isPresent()) {
            Replicate replicate = optionalReplicate.get();

            // save in the task the workerWallet that is in charge of uploading the result
            task.setUploadingWorkerWalletAddress(replicate.getWalletAddress());
            taskService.updateTask(task.getChainTaskId(), Update.update("uploadingWorkerWalletAddress", replicate.getWalletAddress()));
            updateTaskStatusAndSave(task, RESULT_UPLOADING);
            replicatesService.updateReplicateStatus(
                    task.getChainTaskId(), replicate.getWalletAddress(), ReplicateStatusUpdate.poolManagerRequest(RESULT_UPLOAD_REQUESTED));

            applicationEventPublisher.publishEvent(new PleaseUploadEvent(task.getChainTaskId(), replicate.getWalletAddress()));
        }
    }

    void resultUploaded2Finalizing(Task task) {
        if (task.getCurrentStatus() != RESULT_UPLOADED) {
            emitError(task, RESULT_UPLOADED, "resultUploaded2Finalizing");
            return;
        }
        boolean canFinalize = iexecHubService.canFinalize(task.getChainTaskId());

        Optional<ChainTask> optional = iexecHubService.getChainTask(task.getChainTaskId());
        if (optional.isEmpty()) {
            return;
        }
        ChainTask chainTask = optional.get();

        int onChainReveal = chainTask.getRevealCounter();
        int offChainReveal = replicatesService.getNbReplicatesContainingStatus(task.getChainTaskId(), ReplicateStatus.REVEALED);
        boolean offChainRevealEqualsOnChainReveal = offChainReveal == onChainReveal;

        if (!canFinalize || !offChainRevealEqualsOnChainReveal) {
            return;
        }

        if (!iexecHubService.hasEnoughGas()) {
            return;
        }

        blockchainAdapterService
                .requestFinalize(task.getChainTaskId(), task.getResultLink(),
                        task.getChainCallbackData())
                .ifPresentOrElse(chainTaskId -> {
                    log.info("Requested finalize on blockchain [chainTaskId:{}]",
                            task.getChainTaskId());
                    updateTaskStatusAndSave(task, FINALIZING);
                    //Watch finalizing to finalized
                    updateTask(task.getChainTaskId());
                }, () -> {
                    log.error("Failed to request finalize on blockchain [chainTaskId:{}]",
                            task.getChainTaskId());
                    toFailed(task, FINALIZE_FAILED);
                });
    }

    void finalizing2Finalized2Completed(Task task) {
        if (task.getCurrentStatus() != FINALIZING) {
            emitError(task, FINALIZING, "finalizing2Finalized2Completed");
            return;
        }
        blockchainAdapterService
                .isFinalized(task.getChainTaskId())
                .ifPresentOrElse(isSuccess -> {
                    if (Boolean.TRUE.equals(isSuccess)) {
                        log.info("Finalized on blockchain (tx mined) [chainTaskId:{}]",
                                task.getChainTaskId());
                        toFinalizedToCompleted(task);
                        return;
                    }
                    log.error("Finalization failed on blockchain (tx reverted) [chainTaskId:{}]",
                            task.getChainTaskId());
                    toFailed(task, FINALIZE_FAILED);
                }, () -> log.error("Unable to check finalization on blockchain " +
                                "(likely too long), should use a detector [chainTaskId:{}]",
                        task.getChainTaskId()));
    }

    void finalizedToCompleted(Task task) {
        if (task.getCurrentStatus() != FINALIZED) {
            emitError(task, FINALIZED, "finalizedToCompleted");
            return;
        }
        updateTaskStatusAndSave(task, COMPLETED);
        applicationEventPublisher.publishEvent(new TaskCompletedEvent(task));
    }

    void toFinalizedToCompleted(Task task) {
        updateTaskStatusesAndSave(task, FINALIZED, COMPLETED);
        applicationEventPublisher.publishEvent(new TaskCompletedEvent(task));
    }

    void toFailed(Task task) {
        updateTaskStatusAndSave(task, FAILED);
        applicationEventPublisher.publishEvent(new TaskFailedEvent(task.getChainTaskId()));
    }

    void toFailed(Task task, TaskStatus reason) {
        updateTaskStatusesAndSave(task, reason, FAILED);
        applicationEventPublisher.publishEvent(new TaskFailedEvent(task.getChainTaskId()));
    }

    void emitError(Task task, TaskStatus expectedStatus, String methodName) {
        log.error("Cannot initialize task [chainTaskId:{}, currentStatus:{}, expectedStatus:{}, method:{}]",
                task.getChainTaskId(), task.getCurrentStatus(), expectedStatus, methodName);
    }

    void updateMetricsAfterStatusUpdate(TaskStatus previousStatus, TaskStatus newStatus) {
        currentTaskStatusesCount.get(previousStatus).decrementAndGet();
        currentTaskStatusesCount.get(newStatus).incrementAndGet();
        publishTaskStatusesCountUpdate();
    }

    @EventListener(TaskCreatedEvent.class)
    void onTaskCreatedEvent() {
        currentTaskStatusesCount.get(RECEIVED).incrementAndGet();
        publishTaskStatusesCountUpdate();
    }

    private void publishTaskStatusesCountUpdate() {
        // Copying the map here ensures the original values can't be updated from outside this class.
        // As this data should be read only, no need for any atomic class.
        final LinkedHashMap<TaskStatus, Long> currentTaskStatusesCountToPublish = currentTaskStatusesCount
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entrySet -> entrySet.getValue().get(),
                        (a, b) -> b,
                        LinkedHashMap::new
                ));
        final TaskStatusesCountUpdatedEvent event = new TaskStatusesCountUpdatedEvent(currentTaskStatusesCountToPublish);
        applicationEventPublisher.publishEvent(event);
    }
}
