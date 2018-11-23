package com.iexec.core.replicate;

import com.iexec.common.chain.ChainContribution;
import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.worker.Worker;
import com.iexec.core.worker.WorkerService;
import com.iexec.core.workflow.ReplicateWorkflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.iexec.common.replicate.ReplicateStatus.getChainStatus;
import static com.iexec.core.chain.ContributionUtils.scoreToCredibility;

@Slf4j
@Service
public class ReplicatesService {

    private ReplicatesRepository replicatesRepository;
    private IexecHubService iexecHubService;
    private WorkerService workerService;
    private TaskService taskService;

    public ReplicatesService(ReplicatesRepository replicatesRepository,
                             IexecHubService iexecHubService,
                             WorkerService workerService,
                             TaskService taskService) {
        this.replicatesRepository = replicatesRepository;
        this.iexecHubService = iexecHubService;
        this.workerService = workerService;
        this.taskService = taskService;
    }

    // in case the task has been modified between reading and writing it, it is retried up to 10 times
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 10)
    public void updateReplicateStatus(String chainTaskId, String walletAddress, ReplicateStatus newStatus) {

        Optional<ReplicatesList> optionalReplicates = replicatesRepository.findByChainTaskId(chainTaskId);
        if (!optionalReplicates.isPresent()) {
            log.warn("No replicate found for this chainTaskId for status update [chainTaskId:{}, walletAddress:{}, status:{}]",
                    chainTaskId, walletAddress, newStatus);
            return;
        }
        ReplicatesList replicatesList = optionalReplicates.get();

        for (Replicate replicate : replicatesList.getReplicates()) {
            if (replicate.getWalletAddress().equals(walletAddress)) {

                ReplicateStatus currentStatus = replicate.getCurrentStatus();

                // check valid transition
                if (!ReplicateWorkflow.getInstance().isValidTransition(currentStatus, newStatus)) {
                    log.error("UpdateReplicateStatus failed (bad workflow transition) [chainTaskId:{}, walletAddress:{}, " +
                                    "currentStatus:{}, newStatus:{}]",
                            chainTaskId, walletAddress, currentStatus, newStatus);
                    return;
                }

                // TODO: code to check here
                ChainContributionStatus wishedChainStatus = getChainStatus(newStatus);
                if (wishedChainStatus != null) {
                    if (iexecHubService.checkContributionStatusMultipleTimes(chainTaskId, walletAddress, wishedChainStatus)) {
                        handleReplicateWithOnChainStatus(chainTaskId, walletAddress, replicate, wishedChainStatus);
                    } else {
                        log.error("UpdateReplicateStatus failed (bad blockchain status) [chainTaskId:{}, walletAddress:{}, currentStatus:{}, newStatus:{}]",
                                chainTaskId, walletAddress, currentStatus, newStatus);
                        return;
                    }
                }

                replicate.updateStatus(newStatus);
                log.info("UpdateReplicateStatus succeeded [chainTaskId:{}, walletAddress:{}, currentStatus:{}, newStatus:{}]", chainTaskId,
                        walletAddress, currentStatus, newStatus);
                replicatesRepository.save(replicatesList);
            }
        }
        log.warn("No replicate found for status update [chainTaskId:{}, walletAddress:{}, status:{}]", chainTaskId, walletAddress, newStatus);
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
        List<Task> runningTasks = taskService.getAllRunningTasks();
        if (runningTasks.isEmpty()) {
            return Optional.empty();
        }

        // return empty if the worker already has enough running tasks
        int workerRunningReplicateNb = taskService.getRunningReplicatesOfWorker(runningTasks, walletAddress).size();
        int workerCpuNb = worker.getCpuNb();
        if (workerRunningReplicateNb >= workerCpuNb) {
            log.info("Worker asking for too many replicates [walletAddress: {}, workerRunningReplicateNb:{}, workerCpuNb:{}]",
                    walletAddress, workerRunningReplicateNb, workerCpuNb);
            return Optional.empty();
        }

        for (Task task : runningTasks) {
            String chainTaskId = task.getChainTaskId();

            if (!hasWorkerAlreadyContributed(chainTaskId, walletAddress) &&
                    moreReplicatesNeeded(chainTaskId)) {

                createNewReplicate(chainTaskId, walletAddress);
                workerService.addChainTaskIdToWorker(chainTaskId, walletAddress);
                return getReplicate(chainTaskId, walletAddress);
            }
        }

        return Optional.empty();
    }


    public void handleReplicateWithOnChainStatus(String chainTaskId, String walletAddress, Replicate replicate, ChainContributionStatus wishedChainStatus) {
        ChainContribution onChainContribution = iexecHubService.getContribution(chainTaskId, walletAddress);
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

    public void createNewReplicate(String chainTaskId, String walletAddress) {
        Optional<ReplicatesList> optional = getReplicatesList(chainTaskId);
        if (optional.isPresent()) {
            ReplicatesList list = optional.get();
            list.getReplicates().add(new Replicate(walletAddress, chainTaskId));
            replicatesRepository.save(list);
        }
    }

    public Optional<ReplicatesList> getReplicatesList(String chainTaskId) {
        return replicatesRepository.findByChainTaskId(chainTaskId);
    }

    public List<Replicate> getReplicates(String chainTaskId) {
        Optional<ReplicatesList> optionalList = replicatesRepository.findByChainTaskId(chainTaskId);
        if (!optionalList.isPresent()) {
            return Collections.EMPTY_LIST;
        }
        return optionalList.get().getReplicates();
    }

    public boolean hasWorkerAlreadyContributed(String chainTaskId, String walletAddress) {
        for (Replicate replicate : getReplicates(chainTaskId)) {
            if (replicate.getWalletAddress().equals(walletAddress)) {
                return true;
            }
        }
        return false;
    }

    public boolean moreReplicatesNeeded(String chainTaskId) {
        Optional<Task> optionalTask = taskService.getTaskByChainTaskId(chainTaskId);
        if (!optionalTask.isPresent()) {
            return false;
        }
        Task task = optionalTask.get();


        int nbValidReplicates = 0;
        for (Replicate replicate : getReplicates(chainTaskId)) {
            if (!(replicate.getCurrentStatus().equals(ReplicateStatus.ERROR)
                    || replicate.getCurrentStatus().equals(ReplicateStatus.WORKER_LOST))) {
                nbValidReplicates++;
            }
        }
        return nbValidReplicates < task.getTrust();
    }

    public Optional<Replicate> getReplicate(String chainTaskId, String walletAddress) {
        Optional<ReplicatesList> optional = replicatesRepository.findByChainTaskId(chainTaskId);
        if (!optional.isPresent()) {
            return Optional.empty();
        }

        for (Replicate replicate : optional.get().getReplicates()) {
            if (replicate.getWalletAddress().equals(walletAddress)) {
                return Optional.of(replicate);
            }
        }

        return Optional.empty();
    }

    public int getNbReplicatesWithStatus(String chainTaskId, ReplicateStatus status) {
        int nbReplicates = 0;
        for (Replicate replicate : getReplicates(chainTaskId)) {
            if (replicate.getCurrentStatus().equals(status)) {
                nbReplicates++;
            }
        }
        return nbReplicates;
    }

    public int getNbReplicatesStatusEqualTo(String chainTaskId,  ReplicateStatus... listStatus) {
        int nbReplicates = 0;
        for (Replicate replicate : getReplicates(chainTaskId)) {
            for (ReplicateStatus status : listStatus) {
                if (replicate.getCurrentStatus().equals(status)) {
                    nbReplicates++;
                }
            }
        }
        return nbReplicates;
    }
}
