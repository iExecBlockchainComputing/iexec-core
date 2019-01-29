package com.iexec.core.replicate;

import com.iexec.common.chain.ChainContribution;
import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusChange;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.workflow.ReplicateWorkflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.iexec.common.replicate.ReplicateStatus.REVEALED;
import static com.iexec.common.replicate.ReplicateStatus.getChainStatus;

@Slf4j
@Service
public class ReplicatesService {

    private ReplicatesRepository replicatesRepository;
    private IexecHubService iexecHubService;
    private ApplicationEventPublisher applicationEventPublisher;
    private Web3jService web3jService;

    public ReplicatesService(ReplicatesRepository replicatesRepository,
                             IexecHubService iexecHubService,
                             ApplicationEventPublisher applicationEventPublisher,
                             Web3jService web3jService) {
        this.replicatesRepository = replicatesRepository;
        this.iexecHubService = iexecHubService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.web3jService = web3jService;
    }

    public void addNewReplicate(String chainTaskId, String walletAddress) {
        if (!getReplicate(chainTaskId, walletAddress).isPresent()) {
            Optional<ReplicatesList> optional = getReplicatesList(chainTaskId);
            if (optional.isPresent()) {
                ReplicatesList replicatesList = optional.get();
                replicatesList.getReplicates().add(new Replicate(walletAddress, chainTaskId));
                replicatesRepository.save(replicatesList);
                log.info("New replicate saved [chainTaskId:{}, walletAddress:{}]", chainTaskId, walletAddress);
            }
        } else {
            log.error("Replicate already saved [chainTaskId:{}, walletAddress:{}]", chainTaskId, walletAddress);
        }

    }

    public synchronized void createEmptyReplicateList(String chainTaskId) {
        replicatesRepository.save(new ReplicatesList(chainTaskId));
    }

    public Optional<ReplicatesList> getReplicatesList(String chainTaskId) {
        return replicatesRepository.findByChainTaskId(chainTaskId);
    }

    public List<Replicate> getReplicates(String chainTaskId) {
        Optional<ReplicatesList> optionalList = getReplicatesList(chainTaskId);
        if (!optionalList.isPresent()) {
            return Collections.emptyList();
        }
        return optionalList.get().getReplicates();
    }

    public Optional<Replicate> getReplicate(String chainTaskId, String walletAddress) {
        Optional<ReplicatesList> optional = getReplicatesList(chainTaskId);
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

    public boolean hasWorkerAlreadyParticipated(String chainTaskId, String walletAddress) {
        return getReplicate(chainTaskId, walletAddress).isPresent();
    }

    public int getNbReplicatesWithCurrentStatus(String chainTaskId, ReplicateStatus... listStatus) {
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

    public int getNbReplicatesContainingStatus(String chainTaskId, ReplicateStatus... listStatus) {
        Set<String> addressReplicates = new HashSet<>();
        for (Replicate replicate : getReplicates(chainTaskId)) {
            List<ReplicateStatus> listReplicateStatus = replicate.getStatusChangeList().stream()
                    .map(ReplicateStatusChange::getStatus)
                    .collect(Collectors.toList());
            for (ReplicateStatus status : listStatus) {
                if (listReplicateStatus.contains(status)) {
                    addressReplicates.add(replicate.getWalletAddress());
                }
            }
        }
        return addressReplicates.size();
    }

    public Optional<Replicate> getReplicateWithRevealStatus(String chainTaskId) {
        for (Replicate replicate : getReplicates(chainTaskId)) {
            if (replicate.getCurrentStatus().equals(REVEALED)) {
                return Optional.of(replicate);
            }
        }

        return Optional.empty();
    }

    public boolean moreReplicatesNeeded(String chainTaskId, int nbWorkersNeeded, Date timeRef) {
        int nbValidReplicates = 0;
        for (Replicate replicate : getReplicates(chainTaskId)) {
            //TODO think: When do we really need more replicates?
            boolean isReplicateSuccessfullSoFar = ReplicateStatus.getSuccessStatuses().contains(replicate.getCurrentStatus());
            boolean doesContributionTakesTooLong = !replicate.containsContributedStatus() &&
                    replicate.isCreatedMoreThanNPeriodsAgo(2, timeRef);

            if (isReplicateSuccessfullSoFar && !doesContributionTakesTooLong) {
                nbValidReplicates++;
            }
        }
        return nbValidReplicates < nbWorkersNeeded;
    }

    public void updateReplicateStatus(String chainTaskId,
                                      String walletAddress,
                                      ReplicateStatus newStatus,
                                      ReplicateStatusModifier modifier) {
        updateReplicateStatus(chainTaskId, walletAddress, newStatus, 0, modifier);
    }


    // in case the task has been modified between reading and writing it, it is retried up to 100 times
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 100)
    public void updateReplicateStatus(String chainTaskId,
                                      String walletAddress,
                                      ReplicateStatus newStatus,
                                      long blockNumber,
                                      ReplicateStatusModifier modifier) {

        Optional<ReplicatesList> optionalReplicates = getReplicatesList(chainTaskId);
        if (!optionalReplicates.isPresent()) {
            log.warn("No replicate found for this chainTaskId for status update [chainTaskId:{}, walletAddress:{}, status:{}]",
                    chainTaskId, walletAddress, newStatus);
            return;
        }

        Optional<Replicate> optionalReplicate = optionalReplicates.get().getReplicateOfWorker(walletAddress);
        if (!optionalReplicate.isPresent()) {
            log.warn("No replicate found for status update [chainTaskId:{}, walletAddress:{}, status:{}]", chainTaskId, walletAddress, newStatus);
            return;
        }

        Replicate replicate = optionalReplicate.get();
        ReplicateStatus currentStatus = replicate.getCurrentStatus();

        // check if it is a valid transition in case the modifier is the worker
        if (modifier.equals(ReplicateStatusModifier.WORKER) &&
                !ReplicateWorkflow.getInstance().isValidTransition(currentStatus, newStatus)) {
            log.error("UpdateReplicateStatus failed (bad workflow transition) [chainTaskId:{}, walletAddress:{}, " +
                            "currentStatus:{}, newStatus:{}]",
                    chainTaskId, walletAddress, currentStatus, newStatus);
            return;
        }

        if (isBlockchainStatus(newStatus)) {
            replicate = getOnChainRefreshedReplicate(replicate, getChainStatus(newStatus), blockNumber);

            if (modifier.equals(ReplicateStatusModifier.POOL_MANAGER)) {
                log.warn("Replicate status set by the pool manager [chainTaskId:{}, walletAddress:{}, newStatus:{}, blockNumber:{}]",
                        chainTaskId, walletAddress, newStatus, blockNumber);
            }
            
            if (replicate == null) {
                log.error("Failed to refresh replicate with onchain values [chainTaskId:{}, walletAddress:{}, " +
                                "currentStatus:{}, newStatus:{}]",
                        chainTaskId, walletAddress, currentStatus, newStatus);
                return;
            }
        }

        replicate.updateStatus(newStatus, modifier);
        replicatesRepository.save(optionalReplicates.get());

        // if replicate is not busy anymore, it can notify it
        if (!replicate.isBusyComputing()) {
            applicationEventPublisher.publishEvent(new ReplicateComputedEvent(replicate));
        }

        log.info("UpdateReplicateStatus succeeded [chainTaskId:{}, walletAddress:{}, currentStatus:{}, newStatus:{}, modifier:{}]", chainTaskId,
                walletAddress, currentStatus, newStatus, modifier);
        applicationEventPublisher.publishEvent(new ReplicateUpdatedEvent(replicate.getChainTaskId(), newStatus));
    }

    @Recover
    public void updateReplicateStatus(OptimisticLockingFailureException exception,
                                      String chainTaskId,
                                      String walletAddress,
                                      ReplicateStatus newStatus,
                                      long blockNumber,
                                      ReplicateStatusModifier modifier) {
        log.error("Maximum number of tries reached [exception:{}]", exception.getMessage());
        exception.printStackTrace();
    }

    private boolean isBlockchainStatus(ReplicateStatus newStatus) {
        return getChainStatus(newStatus) != null;
    }

    private Replicate getOnChainRefreshedReplicate(Replicate replicate, ChainContributionStatus wishedChainStatus, long blockNumber) {
        // check that the blockNumber is already available for the scheduler
        if (!web3jService.isBlockNumberAvailable(blockNumber)) {
            log.error("This block number is not available, even after waiting for some time [blockNumber:{}]", blockNumber);
            return null;
        }

        boolean isWishedStatusProvedOnChain = iexecHubService.checkContributionStatus(replicate.getChainTaskId(), replicate.getWalletAddress(), wishedChainStatus);
        if (isWishedStatusProvedOnChain) {
            return getReplicateWithBlockchainUpdates(replicate, wishedChainStatus);
        } else {
            log.error("Onchain status is different from wishedChainStatus (should wait?) [chainTaskId:{}, " +
                    "blockNumber:{}, wishedChainStatus:{}]", replicate.getChainTaskId(), blockNumber, wishedChainStatus);
        }

        return null;
    }

    private Replicate getReplicateWithBlockchainUpdates(Replicate replicate, ChainContributionStatus wishedChainStatus) {
        Optional<ChainContribution> optional = iexecHubService.getContribution(replicate.getChainTaskId(), replicate.getWalletAddress());
        if (!optional.isPresent()) {
            return null;
        }
        ChainContribution chainContribution = optional.get();
        if (wishedChainStatus.equals(ChainContributionStatus.CONTRIBUTED)){
            replicate.setContributionHash(chainContribution.getResultHash());
        }
        return replicate;
    }


}
