package com.iexec.core.task;

import com.iexec.common.chain.ChainContribution;
import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ChainTaskStatus;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.result.TaskNotification;
import com.iexec.common.result.TaskNotificationType;
import com.iexec.core.chain.IexecClerkService;
import com.iexec.core.pubsub.NotificationService;
import com.iexec.core.replicate.Replicate;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import com.iexec.core.workflow.ReplicateWorkflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.iexec.common.replicate.ReplicateStatus.getChainStatus;
import static com.iexec.core.chain.ContributionUtils.*;
import static com.iexec.core.task.TaskStatus.*;
import static com.iexec.core.utils.DateTimeUtils.now;

@Slf4j
@Service
public class TaskService {

    private TaskRepository taskRepository;
    private WorkerService workerService;
    private NotificationService notificationService;
    private IexecClerkService iexecClerkService;

    public TaskService(TaskRepository taskRepository,
                       WorkerService workerService,
                       NotificationService notificationService,
                       IexecClerkService iexecClerkService) {
        this.taskRepository = taskRepository;
        this.workerService = workerService;
        this.notificationService = notificationService;
        this.iexecClerkService = iexecClerkService;
    }

    public Task addTask(String dappName, String commandLine, int trust, String chainTaskId) {
        log.info("Adding new task [commandLine:{}, trust:{}]", commandLine, trust);
        return taskRepository.save(new Task(dappName, commandLine, trust, chainTaskId));
    }

    public Optional<Task> getTaskByChainTaskId(String chainTaskId) {
        return taskRepository.findByChainTaskId(chainTaskId);
    }

    public List<Task> getTasksByIds(List<String> ids) {
        return taskRepository.findById(ids);
    }

    public List<Task> getTasksByChainTaskIds(List<String> chainTaskIds) {
        return taskRepository.findByChainTaskId(chainTaskIds);
    }

    // in case the task has been modified between reading and writing it, it is retried up to 5 times
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 5)
    public void updateReplicateStatus(String chainTaskId, String walletAddress, ReplicateStatus newStatus) {
        Optional<Task> optional = taskRepository.findByChainTaskId(chainTaskId);
        if (!optional.isPresent()) {
            log.warn("No task found for replicate update [chainTaskId:{}, walletAddress:{}, status:{}]", chainTaskId, walletAddress, newStatus);
            return;
        }

        Task task = optional.get();
        for (Replicate replicate : task.getReplicates()) {
            if (replicate.getWalletAddress().equals(walletAddress)) {
                ReplicateStatus currentStatus = replicate.getCurrentStatus();

                if (!ReplicateWorkflow.getInstance().isValidTransition(currentStatus, newStatus)) {
                    log.error("The replicate can't be updated to the new status (bad workflow transition) [chainTaskId:{}, walletAddress:{}, currentStatus:{}, newStatus:{}]",
                            chainTaskId, walletAddress, currentStatus, newStatus);
                    return;
                }

                ChainContributionStatus wishedChainStatus = getChainStatus(newStatus);
                if (wishedChainStatus != null) {
                    if (iexecClerkService.checkContributionStatusMultipleTimes(chainTaskId, walletAddress, wishedChainStatus)) {
                        handleReplicateWithOnChainStatus(chainTaskId, walletAddress, replicate, wishedChainStatus);
                    } else {
                        log.error("The replicate can't be updated to the new status (bad blockchain status) [chainTaskId:{}, walletAddress:{}, currentStatus:{}, newStatus:{}]",
                                chainTaskId, walletAddress, currentStatus, newStatus);
                        return;
                    }
                }

                replicate.updateStatus(newStatus);
                log.info("Status of replicate updated [chainTaskId:{}, walletAddress:{}, status:{}]", chainTaskId,
                        walletAddress, newStatus);
                taskRepository.save(task);

                // once the replicate status is updated, the task status has to be checked as well
                updateTaskStatus(task);

                return;

            }
        }

        log.warn("No replicate found for status update [chainTaskId:{}, walletAddress:{}, status:{}]", chainTaskId, walletAddress, newStatus);
    }

    public void handleReplicateWithOnChainStatus(String chainTaskId, String walletAddress, Replicate replicate, ChainContributionStatus wishedChainStatus) {
        ChainContribution onChainContribution = iexecClerkService.getContribution(chainTaskId, walletAddress);
        switch (wishedChainStatus) {
            case CONTRIBUTED:
                replicate.setResultHash(onChainContribution.getResultHash());
                replicate.setCredibility(scoreToCredibility(onChainContribution.getScore()));
                break;
            case REVEALED:
                break;
            default:
                break;
        }
    }

    public List<Task> findByCurrentStatus(TaskStatus status) {
        return taskRepository.findByCurrentStatus(status);
    }

    // in case the task has been modified between reading and writing it, it is retried up to 5 times
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 5)
    public Optional<Replicate> getAvailableReplicate(String walletAddress) {
        // return empty if the worker is not registered
        Optional<Worker> optional = workerService.getWorker(walletAddress);
        if (!optional.isPresent()) {
            return Optional.empty();
        }
        Worker worker = optional.get();

        // return empty if there is no task to contribute
        List<Task> runningTasks = getAllRunningTasks();
        if (runningTasks.isEmpty()) {
            return Optional.empty();
        }

        // return empty if the worker already has enough running tasks
        int workerRunningReplicateNb = getRunningReplicatesOfWorker(runningTasks, walletAddress).size();
        int workerCpuNb = worker.getCpuNb();
        if (workerRunningReplicateNb >= workerCpuNb) {
            log.info("Worker asking for too many replicates [walletAddress: {}, workerRunningReplicateNb:{}, workerCpuNb:{}]",
                    walletAddress, workerRunningReplicateNb, workerCpuNb);
            return Optional.empty();
        }

        for (Task task : runningTasks) {
            if (!task.hasWorkerAlreadyContributed(walletAddress) &&
                    task.needMoreReplicates()) {
                task.createNewReplicate(walletAddress);
                Task savedTask = taskRepository.save(task);
                workerService.addTaskIdToWorker(savedTask.getId(), walletAddress);
                return savedTask.getReplicate(walletAddress);
            }
        }

        return Optional.empty();
    }

    private List<Replicate> getRunningReplicatesOfWorker(List<Task> runningTasks, String walletAddress) {
        List<Replicate> workerActiveReplicates = new ArrayList<>();
        for (Task task : runningTasks) {
            List<Replicate> replicates = task.getReplicates();
            for (Replicate replicate : replicates) {

                boolean isReplicateFromWorker = replicate.getWalletAddress().equals(walletAddress);
                boolean isReplicateInCorrectStatus = (replicate.getCurrentStatus().equals(ReplicateStatus.CREATED) ||
                        replicate.getCurrentStatus().equals(ReplicateStatus.RUNNING));

                if (isReplicateFromWorker && isReplicateInCorrectStatus) {
                    workerActiveReplicates.add(replicate);
                }
            }
        }
        return workerActiveReplicates;
    }

    private List<Task> getAllRunningTasks() {
        return taskRepository.findByCurrentStatus(Arrays.asList(CREATED, RUNNING));
    }

    // TODO: when the workflow becomes more complicated, a chain of responsability can be implemented here
    void updateTaskStatus(Task task) {
        TaskStatus currentStatus = task.getCurrentStatus();
        switch (currentStatus) {
            case CREATED:
                tryUpdateToRunning(task);
                break;
            case RUNNING:
                tryUpdateToContributed(task);
                break;
            case CONTRIBUTED:
                break;
            case UPLOAD_RESULT_REQUESTED:
                tryUpdateToUploadingResult(task);
                break;
            case UPLOADING_RESULT:
                tryUpdateToResultUploaded(task);
                break;
            case RESULT_UPLOADED:
                break;
            case COMPLETED:
                break;
            case ERROR:
                break;
        }
    }

    void tryUpdateToRunning(Task task) {
        boolean condition1 = task.getNbReplicatesStatusEqualTo(ReplicateStatus.RUNNING, ReplicateStatus.COMPUTED) > 0;
        boolean condition2 = task.getNbReplicatesWithStatus(ReplicateStatus.COMPUTED) < task.getTrust();
        boolean condition3 = task.getCurrentStatus().equals(CREATED);

        if (condition1 && condition2 && condition3) {
            task.changeStatus(RUNNING);
            taskRepository.save(task);
            log.info("Status of task updated [taskId:{}, status:{}]", task.getId(), RUNNING);
        }
    }

    void tryUpdateToContributed(Task task) {
        boolean condition1 = task.getCurrentStatus().equals(RUNNING);

        Map<String, Integer> sortedClusters = getHash2CredibilityClusters(task);
        sortedClusters = sortClustersByCredibility(sortedClusters);
        Map.Entry<String, Integer> bestCluster = sortedClusters.entrySet().iterator().next();
        Integer bestCredibility = bestCluster.getValue();
        String consensus = bestCluster.getKey();
        boolean condition2 = bestCredibility >= trustToCredibility(task.getTrust());

        ChainTask chainTask = iexecClerkService.getChainTask(task.getChainTaskId());
        boolean condition3 = chainTask.getStatus().equals(ChainTaskStatus.ACTIVE);
        boolean condition4 = now() < chainTask.getConsensusDeadline();

        if (condition1 && condition2 && condition3 && condition4) {
            task.changeStatus(CONTRIBUTED);
            task.setConsensus(consensus);
            taskRepository.save(task);
            log.info("Status of task updated [taskId:{}, status:{}]", task.getId(), CONTRIBUTED);

            try {
                if (iexecClerkService.consensus(task.getChainTaskId(), task.getConsensus())) {
                    //TODO call only winners PLEASE_REVEAL & losers PLEASE_ABORT
                    notificationService.sendTaskNotification(TaskNotification.builder()
                            .taskNotificationType(TaskNotificationType.PLEASE_REVEAL)
                            .chainTaskId(task.getChainTaskId()).build()
                    );
                }
            } catch (Exception e) {
                log.error("Failed to consensus [taskId:{}, consensus:{}]", task.getId(), task.getConsensus());
            }
        } else {
            log.info("Unsatisfied check(s) for consensus [condition1:{}, condition2:{}, condition3:{}, condition4:{}, ] ",
                    condition1, condition2, condition3, condition4);
        }
    }

    void tryUpdateToUploadingResult(Task task) {
        boolean condition1 = task.getCurrentStatus().equals(TaskStatus.UPLOAD_RESULT_REQUESTED);
        boolean condition2 = task.getNbReplicatesWithStatus(ReplicateStatus.UPLOADING_RESULT) > 0;

        if (condition1 && condition2) {
            task.changeStatus(TaskStatus.UPLOADING_RESULT);
            taskRepository.save(task);
            log.info("Status of task updated [taskId:{}, status:{}]", task.getId(), TaskStatus.UPLOADING_RESULT);
        }
    }

    void tryUpdateToResultUploaded(Task task) {
        boolean condition1 = task.getCurrentStatus().equals(TaskStatus.UPLOADING_RESULT);
        boolean condition2 = task.getNbReplicatesWithStatus(ReplicateStatus.RESULT_UPLOADED) > 0;

        if (condition1 && condition2) {
            task.changeStatus(TaskStatus.RESULT_UPLOADED);
            taskRepository.save(task);
            log.info("Status of task updated [taskId:{}, status:{}]", task.getId(), TaskStatus.RESULT_UPLOADED);

            task.changeStatus(TaskStatus.COMPLETED);
            taskRepository.save(task);

            for (Replicate replicate : task.getReplicates()) {
                workerService.removeTaskIdFromWorker(task.getId(), replicate.getWalletAddress());
            }

            notificationService.sendTaskNotification(TaskNotification.builder()
                    .chainTaskId(task.getChainTaskId())
                    .workerAddress(task.getUploadingWorkerWalletAddress())
                    .taskNotificationType(TaskNotificationType.COMPLETED)
                    .build());
            log.info("Status of task updated [taskId:{}, status:{}]", task.getId(), TaskStatus.COMPLETED);
        } else if (task.getNbReplicatesWithStatus(ReplicateStatus.UPLOAD_RESULT_REQUEST_FAILED) > 0 &&
                task.getNbReplicatesWithStatus(ReplicateStatus.UPLOADING_RESULT) == 0) {
            // need to request upload again
            requestUpload(task);
        }
    }

    private void requestUpload(Task task) {
        if (task.getCurrentStatus().equals(COMPUTED)) {
            task.changeStatus(TaskStatus.UPLOAD_RESULT_REQUESTED);
            taskRepository.save(task);
            log.info("Status of task updated [taskId:{}, status:{}]", task.getId(), TaskStatus.UPLOAD_RESULT_REQUESTED);
        }

        if (task.getCurrentStatus().equals(TaskStatus.UPLOAD_RESULT_REQUESTED)) {
            for (Replicate replicate : task.getReplicates()) {
                if (replicate.getCurrentStatus().equals(ReplicateStatus.COMPUTED)) {
                    notificationService.sendTaskNotification(TaskNotification.builder()
                            .chainTaskId(task.getChainTaskId())
                            .workerAddress(replicate.getWalletAddress())
                            .taskNotificationType(TaskNotificationType.UPLOAD)
                            .build());
                    log.info("Notify uploading worker [uploadingWorkerWallet={}]", replicate.getWalletAddress());

                    // save in the task the workerWallet that is in charge of uploading the result
                    task.setUploadingWorkerWalletAddress(replicate.getWalletAddress());
                    taskRepository.save(task);
                    return;
                }
            }
        }
    }

}
