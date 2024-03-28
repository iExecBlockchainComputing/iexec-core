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
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.iexec.core.task.TaskStatus.*;

@Service
@Slf4j
class TaskUpdateManager {

    private final TaskService taskService;
    private final IexecHubService iexecHubService;
    private final ReplicatesService replicatesService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final WorkerService workerService;
    private final BlockchainAdapterService blockchainAdapterService;
    private final SmsService smsService;

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
    }

    void updateTask(String chainTaskId) {
        log.debug("Task update process starts [chainTaskId:{}]", chainTaskId);
        final Task task = taskService.getTaskByChainTaskId(chainTaskId).orElse(null);
        if (task == null) {
            log.warn("Off-chain task model could not be retrieved [chainTaskId:{}]", chainTaskId);
            return;
        }
        final ChainTask chainTask = iexecHubService.getChainTask(chainTaskId).orElse(null);
        if (chainTask == null && (task.getCurrentStatus() == INITIALIZED || task.getCurrentStatus().ordinal() >= RUNNING.ordinal())) {
            log.warn("On-chain task model could not be retrieved [chainTaskId:{}]", chainTaskId);
            return;
        }

        final boolean isFinalDeadlinePossible =
                !TaskStatus.getStatusesWhereFinalDeadlineIsImpossible().contains(task.getCurrentStatus());
        final TaskStatus currentStatus = (isFinalDeadlinePossible && new Date().after(task.getFinalDeadline()))
                ? FINAL_DEADLINE_REACHED : task.getCurrentStatus();

        switch (currentStatus) {
            case RECEIVED:
                received2Initializing(task);
                break;
            case INITIALIZING:
                initializing2Initialized(task);
                break;
            case INITIALIZED:
                initialized2Running(task, chainTask);
                break;
            case RUNNING:
                transitionFromRunningState(task, chainTask);
                break;
            case CONSENSUS_REACHED:
                consensusReached2AtLeastOneReveal2ResultUploading(task);
                //consensusReached2Reopening(task);
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
                resultUploading2Uploaded(chainTask, task);
                break;
            case RESULT_UPLOADED:
                resultUploaded2Finalizing(chainTask, task);
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
                toFailed(task);
                break;
            case FINAL_DEADLINE_REACHED:
                // Eventually should fire a "final deadline reached" notification to worker,
                // but here let's just trigger an toFailed(task) leading to a failed status
                // which will itself fire a generic "abort" notification
                toFailed(task, FINAL_DEADLINE_REACHED);
                break;
            case COMPLETED:
            case FAILED:
                break;
        }
        log.debug("Task update process completed [chainTaskId:{}]", chainTaskId);
    }

    // region database
    /**
     * Creates one or several task status changes for the task before committing all of them to the database.
     *
     * @param task     The task
     * @param statuses List of statuses to append to the task {@code dateStatusList}
     */
    void updateTaskStatusesAndSave(Task task, TaskStatus... statuses) {
        final TaskStatus currentStatus = task.getCurrentStatus();
        final List<TaskStatusChange> statusChanges = new ArrayList<>();
        for (TaskStatus newStatus : statuses) {
            log.info("Create TaskStatusChange succeeded [chainTaskId:{}, currentStatus:{}, newStatus:{}]",
                    task.getChainTaskId(), task.getCurrentStatus(), newStatus);
            final TaskStatusChange statusChange = TaskStatusChange.builder().status(newStatus).build();
            // task update required by tests
            task.setCurrentStatus(newStatus);
            task.getDateStatusList().add(statusChange);
            statusChanges.add(statusChange);
        }
        saveTask(task.getChainTaskId(), currentStatus, statuses[statuses.length - 1], statusChanges);
    }

    void updateTaskStatusAndSave(Task task, TaskStatus newStatus) {
        updateTaskStatusAndSave(task, newStatus, null);
    }

    void updateTaskStatusAndSave(Task task, TaskStatus newStatus, ChainReceipt chainReceipt) {
        final TaskStatus currentStatus = task.getCurrentStatus();
        final TaskStatusChange statusChange = TaskStatusChange.builder().status(newStatus).chainReceipt(chainReceipt).build();
        // task update required by tests
        task.setCurrentStatus(newStatus);
        task.getDateStatusList().add(statusChange);
        saveTask(task.getChainTaskId(), currentStatus, newStatus, List.of(statusChange));
    }

    /**
     * Saves the task to the database.
     *
     * @param chainTaskId   ID of the task
     * @param currentStatus The current status in database
     * @param wishedStatus  The status the task should have after the update
     * @param statusChanges List of changes
     */
    void saveTask(String chainTaskId, TaskStatus currentStatus, TaskStatus wishedStatus, List<TaskStatusChange> statusChanges) {
        long updatedTaskCount = taskService.updateTaskStatus(chainTaskId, currentStatus, wishedStatus, statusChanges);
        // `savedTask.isPresent()` should always be true if the task exists in the repository.
        if (updatedTaskCount != 0L) {
            log.info("UpdateTaskStatus succeeded [chainTaskId:{}, currentStatus:{}, newStatus:{}]",
                    chainTaskId, currentStatus, wishedStatus);
        } else {
            log.warn("UpdateTaskStatus failed. Chain Task is probably unknown [chainTaskId:{}, currentStatus:{}, wishedStatus:{}]",
                    chainTaskId, currentStatus, wishedStatus);
        }
    }
    // endregion

    // region status transitions
    void received2Initializing(Task task) {
        log.debug("received2Initializing [chainTaskId:{}]", task.getChainTaskId());
        if (task.getCurrentStatus() != RECEIVED) {
            emitError(task, RECEIVED, "received2Initializing");
            return;
        }

        boolean hasEnoughGas = iexecHubService.hasEnoughGas();
        boolean isTaskUnsetOnChain = iexecHubService.isTaskInUnsetStatusOnChain(task.getChainDealId(), task.getTaskIndex());
        boolean isBeforeContributionDeadline = iexecHubService.isBeforeContributionDeadline(task.getChainDealId());

        if (!hasEnoughGas || !isTaskUnsetOnChain || !isBeforeContributionDeadline) {
            log.error("Cannot initialize task [chainTaskId:{}, hasEnoughGas:{}, isTaskUnsetOnChain:{}, isBeforeContributionDeadline:{}]",
                    task.getChainTaskId(), hasEnoughGas, isTaskUnsetOnChain, isBeforeContributionDeadline);
            return;
        }

        final Update update = new Update();
        // First check on SMS URL before submitting transaction to fail fast
        if (task.isTeeTask()) {
            final Optional<String> smsUrl = smsService.getVerifiedSmsUrl(task.getChainTaskId(), task.getTag());
            if (smsUrl.isEmpty()) {
                log.error("Couldn't get verified SMS url [chainTaskId:{}]", task.getChainTaskId());
                toFailed(task, INITIALIZE_FAILED);
                return;
            }
            task.setSmsUrl(smsUrl.get()); //SMS URL source of truth for the task
            update.set("smsUrl", smsUrl.get());
        }
        taskService.updateTask(task.getChainTaskId(), task.getCurrentStatus(), update);

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
        log.debug("initializing2Initialized [chainTaskId:{}]", task.getChainTaskId());
        if (task.getCurrentStatus() != INITIALIZING) {
            emitError(task, INITIALIZING, "initializing2Initialized");
            return;
        }
        // TODO: the block where initialization happened can be found
        final Optional<Boolean> isInitialized = blockchainAdapterService.isInitialized(task.getChainTaskId());
        if (isInitialized.isEmpty()) {
            log.error("Unable to check initialization on blockchain (likely too long), should use a detector [chainTaskId:{}]",
                    task.getChainTaskId());
        } else if (Boolean.TRUE.equals(isInitialized.get())) {
            log.info("Initialized on blockchain (tx mined) [chainTaskId:{}]", task.getChainTaskId());
            final Update update = new Update();
            // Without receipt, using deal block for initialization block
            task.setInitializationBlockNumber(task.getDealBlockNumber());
            update.set("initializationBlockNumber", task.getDealBlockNumber());

            // Create enclave challenge after task has been initialized on-chain
            final Optional<String> enclaveChallenge = smsService.getEnclaveChallenge(task.getChainTaskId(), task.getSmsUrl());
            if (enclaveChallenge.isEmpty()) {
                log.error("Can't initialize task, enclave challenge is empty [chainTaskId:{}]", task.getChainTaskId());
                toFailed(task, INITIALIZE_FAILED);
                return;
            }
            task.setEnclaveChallenge(enclaveChallenge.get());
            update.set("enclaveChallenge", enclaveChallenge.get());
            taskService.updateTask(task.getChainTaskId(), task.getCurrentStatus(), update);

            replicatesService.createEmptyReplicateList(task.getChainTaskId());
            updateTaskStatusAndSave(task, INITIALIZED, null);
        } else {
            log.error("Initialization failed on blockchain (tx reverted) [chainTaskId:{}]", task.getChainTaskId());
            toFailed(task, INITIALIZE_FAILED);
        }
    }

    void initialized2Running(Task task, ChainTask chainTask) {
        log.debug("initialized2Running [chainTaskId:{}]", task.getChainTaskId());
        if (task.getCurrentStatus() != INITIALIZED) {
            emitError(task, INITIALIZED, "initialized2Running");
            return;
        }

        if (chainTask.getStatus().ordinal() < ChainTaskStatus.REVEALING.ordinal()
                && task.getContributionDeadline() != null && new Date().after(task.getContributionDeadline())) {
            updateTaskStatusesAndSave(task, CONTRIBUTION_TIMEOUT, FAILED);
            applicationEventPublisher.publishEvent(new ContributionTimeoutEvent(task.getChainTaskId()));
        }

        final String chainTaskId = task.getChainTaskId();

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
                ReplicateStatus.CONTRIBUTED,
                ReplicateStatus.CONTRIBUTE_AND_FINALIZE_ONGOING,
                ReplicateStatus.CONTRIBUTE_AND_FINALIZE_FAILED,
                ReplicateStatus.CONTRIBUTE_AND_FINALIZE_DONE
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

    void transitionFromRunningState(Task task, ChainTask chainTask) {
        log.debug("transitionFromRunningState [chainTaskId:{}]", task.getChainTaskId());
        if (task.getCurrentStatus() != RUNNING) {
            emitError(task, RUNNING, "transitionFromRunningState");
            return;
        }

        // check timeout first
        if (chainTask.getStatus().ordinal() < ChainTaskStatus.REVEALING.ordinal() &&
                task.getContributionDeadline() != null && new Date().after(task.getContributionDeadline())) {
            updateTaskStatusesAndSave(task, CONTRIBUTION_TIMEOUT, FAILED);
            applicationEventPublisher.publishEvent(new ContributionTimeoutEvent(task.getChainTaskId()));
            return;
        }

        final String chainTaskId = task.getChainTaskId();
        final ReplicatesList replicatesList = replicatesService.getReplicatesList(chainTaskId).orElse(null);
        if (replicatesList == null) {
            log.error("Can't transition task when no replicatesList exists [chainTaskId:{}]",
                    chainTaskId);
            return;
        }

        final TaskDescription taskDescription = iexecHubService.getTaskDescription(task.getChainTaskId());

        if (taskDescription.isEligibleToContributeAndFinalize()) {
            // running2Finalized2Completed must be the first call to prevent other transition execution
            log.debug("Task is running in a TEE without callback, flow contributeAndFinalize is possible [chainTaskId:{}]",
                    chainTaskId);
            running2Finalized2Completed(task, replicatesList);
        } else {
            // Task is either TEE with callback or non-TEE task
            running2ConsensusReached(chainTask, task, replicatesList);
        }

        // If task is still in RUNNING state, check if replicates have run on all alive workers
        if (task.getCurrentStatus() == RUNNING) {
            running2RunningFailed(task, replicatesList);
        }
    }

    private void running2ConsensusReached(ChainTask chainTask, Task task, ReplicatesList replicatesList) {
        log.debug("running2ConsensusReached [chainTaskId:{}]", task.getChainTaskId());
        final String chainTaskId = task.getChainTaskId();
        final boolean isConsensusReached = taskService.isConsensusReached(replicatesList);

        if (isConsensusReached) {
            // change the revealDeadline and consensus of the task from the chainTask info
            task.setRevealDeadline(new Date(chainTask.getRevealDeadline()));
            task.setConsensus(chainTask.getConsensusValue());
            long consensusBlockNumber = iexecHubService.getConsensusBlock(chainTaskId, task.getInitializationBlockNumber()).getBlockNumber();
            task.setConsensusReachedBlockNumber(consensusBlockNumber);
            taskService.updateTask(task.getChainTaskId(), task.getCurrentStatus(),
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

    private void running2Finalized2Completed(Task task, ReplicatesList replicatesList) {
        log.debug("running2Finalized2Completed [chainTaskId:{}]", task.getChainTaskId());
        final String chainTaskId = task.getChainTaskId();
        final int nbReplicatesWithContributeAndFinalizeStatus = replicatesList.getNbReplicatesWithCurrentStatus(ReplicateStatus.CONTRIBUTE_AND_FINALIZE_DONE);

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

    /**
     * In TEE mode, when all workers have failed to run the task, we send the new status as soon as possible.
     * It avoids a state within which we know the task won't be updated anymore, but we wait for a timeout.
     * <br>
     * We consider that all workers are in a `RUNNING_FAILED` status
     * when all alive workers have tried to run the task, but they have failed.
     *
     * @param task Task to check and to make become {@link TaskStatus#RUNNING_FAILED}.
     */
    private void running2RunningFailed(Task task, ReplicatesList replicatesList) {
        log.debug("running2RunningFailed [chainTaskId:{}]", task.getChainTaskId());
        if (!task.isTeeTask()) {
            log.debug("This flow only applies to TEE tasks [chainTaskId:{}]", task.getChainTaskId());
            return;
        }

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
        log.debug("consensusReached2AtLeastOneReveal2ResultUploading [chainTaskId:{}]", task.getChainTaskId());
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
        log.debug("consensusReached2Reopening [chainTaskId:{}]", task.getChainTaskId());
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
        log.debug("reopening2Reopened [chainTaskId:{}]", task.getChainTaskId());
        reopening2Reopened(task, null);
    }

    void reopening2Reopened(Task task, ChainReceipt chainReceipt) {
        log.debug("reopening2Reopened [chainTaskId:{}]", task.getChainTaskId());
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
            taskService.updateTask(task.getChainTaskId(), task.getCurrentStatus(),
                    Update.update("consensus", null)
                            .set("revealDeadline", new Date(0)));

            updateTaskStatusAndSave(task, REOPENED, chainReceipt);
            updateTaskStatusAndSave(task, INITIALIZED, chainReceipt);
        }
    }

    void resultUploading2Uploaded(ChainTask chainTask, Task task) {
        log.debug("resultUploading2Uploaded [chainTaskId:{}]", task.getChainTaskId());
        if (task.getCurrentStatus() != RESULT_UPLOADING) {
            emitError(task, RESULT_UPLOADING, "resultUploading2Uploaded");
            return;
        }

        // check timeout first
        final boolean isNowAfterFinalDeadline = task.getFinalDeadline() != null && new Date().after(task.getFinalDeadline());
        if (isNowAfterFinalDeadline) {
            applicationEventPublisher.publishEvent(new ResultUploadTimeoutEvent(task.getChainTaskId()));
            toFailed(task, RESULT_UPLOAD_TIMEOUT);
            return;
        }

        final Replicate uploadedReplicate = replicatesService.getReplicateWithResultUploadedStatus(task.getChainTaskId())
                .orElse(null);

        if (uploadedReplicate != null) {
            task.setResultLink(uploadedReplicate.getResultLink());
            task.setChainCallbackData(uploadedReplicate.getChainCallbackData());
            taskService.updateTask(task.getChainTaskId(), task.getCurrentStatus(),
                    Update.update("resultLink", uploadedReplicate.getResultLink())
                            .set("chainCallbackData", uploadedReplicate.getChainCallbackData()));
            updateTaskStatusAndSave(task, RESULT_UPLOADED);
            resultUploaded2Finalizing(chainTask, task);
            return;
        }

        String uploadingReplicateAddress = task.getUploadingWorkerWalletAddress();

        if (uploadingReplicateAddress == null || uploadingReplicateAddress.isEmpty()) {
            requestUpload(task);
            return;
        }

        final Replicate replicate = replicatesService.getReplicate(task.getChainTaskId(), uploadingReplicateAddress).orElse(null);

        if (replicate == null) {
            requestUpload(task);
            return;
        }

        boolean isReplicateUploading = replicate.getCurrentStatus() == ReplicateStatus.RESULT_UPLOADING;
        boolean isReplicateRecoveringToUpload = replicate.getCurrentStatus() == ReplicateStatus.RECOVERING &&
                replicate.getLastRelevantStatus() == ReplicateStatus.RESULT_UPLOADING;

        if (!isReplicateUploading && !isReplicateRecoveringToUpload) {
            requestUpload(task);
        }
    }

    void requestUpload(Task task) {
        log.debug("requestUpload [chainTaskId:{}]", task.getChainTaskId());
        boolean isThereAWorkerUploading = replicatesService
                .getNbReplicatesWithCurrentStatus(task.getChainTaskId(),
                        ReplicateStatus.RESULT_UPLOADING,
                        ReplicateStatus.RESULT_UPLOAD_REQUESTED) > 0;

        if (isThereAWorkerUploading) {
            log.info("Upload is requested but an upload is already in process. [chainTaskId: {}]", task.getChainTaskId());
            return;
        }

        replicatesService.getRandomReplicateWithRevealStatus(task.getChainTaskId()).ifPresent(replicate -> {
            // save in the task the workerWallet that is in charge of uploading the result
            task.setUploadingWorkerWalletAddress(replicate.getWalletAddress());
            taskService.updateTask(task.getChainTaskId(), task.getCurrentStatus(),
                    Update.update("uploadingWorkerWalletAddress", replicate.getWalletAddress()));
            updateTaskStatusAndSave(task, RESULT_UPLOADING);
            replicatesService.updateReplicateStatus(
                    task.getChainTaskId(), replicate.getWalletAddress(),
                    ReplicateStatusUpdate.poolManagerRequest(ReplicateStatus.RESULT_UPLOAD_REQUESTED));

            applicationEventPublisher.publishEvent(new PleaseUploadEvent(task.getChainTaskId(), replicate.getWalletAddress()));
        });
    }

    void resultUploaded2Finalizing(ChainTask chainTask, Task task) {
        log.debug("resultUploaded2Finalizing [chainTaskId:{}]", task.getChainTaskId());
        if (task.getCurrentStatus() != RESULT_UPLOADED) {
            emitError(task, RESULT_UPLOADED, "resultUploaded2Finalizing");
            return;
        }
        boolean canFinalize = iexecHubService.canFinalize(task.getChainTaskId());

        int onChainReveal = chainTask.getRevealCounter();
        int offChainReveal = replicatesService.getNbReplicatesContainingStatus(task.getChainTaskId(), ReplicateStatus.REVEALED);
        boolean offChainRevealEqualsOnChainReveal = offChainReveal == onChainReveal;

        if (!canFinalize || !offChainRevealEqualsOnChainReveal) {
            log.debug("[canFinalize:{}, offChainRevealEqualsOnChainReveal:{}]", canFinalize, offChainRevealEqualsOnChainReveal);
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
        log.debug("finalizing2Finalized2Completed [chainTaskId:{}]", task.getChainTaskId());
        if (task.getCurrentStatus() != FINALIZING) {
            emitError(task, FINALIZING, "finalizing2Finalized2Completed");
            return;
        }
        final Optional<Boolean> isFinalized = blockchainAdapterService.isFinalized(task.getChainTaskId());
        if (isFinalized.isEmpty()) {
            log.error("Unable to check finalization on blockchain (likely too long), should use a detector [chainTaskId:{}]",
                    task.getChainTaskId());
        } else if (Boolean.TRUE.equals(isFinalized.get())) {
            log.info("Finalized on blockchain (tx mined) [chainTaskId:{}]", task.getChainTaskId());
            toFinalizedToCompleted(task);
        } else {
            log.error("Finalization failed on blockchain (tx reverted) [chainTaskId:{}]", task.getChainTaskId());
            toFailed(task, FINALIZE_FAILED);
        }
    }

    void finalizedToCompleted(Task task) {
        log.debug("finalizedToCompleted [chainTaskId:{}]", task.getChainTaskId());
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
    // endregion

    void emitError(Task task, TaskStatus expectedStatus, String methodName) {
        log.error("Cannot update task [chainTaskId:{}, currentStatus:{}, expectedStatus:{}, method:{}]",
                task.getChainTaskId(), task.getCurrentStatus(), expectedStatus, methodName);
    }
}
