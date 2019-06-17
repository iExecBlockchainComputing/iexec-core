package com.iexec.core.replicate;

import com.iexec.common.chain.ChainContribution;
import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.chain.ChainReceipt;
import com.iexec.common.replicate.ReplicateDetails;
import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusChange;
import com.iexec.common.replicate.ReplicateStatusModifier;
import com.iexec.common.result.eip712.Eip712Challenge;
import com.iexec.common.result.eip712.Eip712ChallengeUtils;
import com.iexec.core.chain.CredentialsService;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.chain.Web3jService;
import com.iexec.core.result.core.ResultRepoService;
import com.iexec.core.workflow.ReplicateWorkflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.web3j.crypto.ECKeyPair;

import java.util.*;
import java.util.stream.Collectors;

import static com.iexec.common.replicate.ReplicateStatus.*;

@Slf4j
@Service
public class ReplicatesService {

    private ReplicatesRepository replicatesRepository;
    private IexecHubService iexecHubService;
    private ApplicationEventPublisher applicationEventPublisher;
    private Web3jService web3jService;
    private CredentialsService credentialsService;
    private ResultRepoService resultRepoService;

    public ReplicatesService(ReplicatesRepository replicatesRepository,
                             IexecHubService iexecHubService,
                             ApplicationEventPublisher applicationEventPublisher,
                             Web3jService web3jService,
                             CredentialsService credentialsService,
                             ResultRepoService resultRepoService) {
        this.replicatesRepository = replicatesRepository;
        this.iexecHubService = iexecHubService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.web3jService = web3jService;
        this.credentialsService = credentialsService;
        this.resultRepoService = resultRepoService;
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

    public int getNbValidContributedWinners(String chainTaskId) {
        int nbValidWinners = 0;
        for(Replicate replicate:getReplicates(chainTaskId)) {
            Optional<ReplicateStatus> oStatus = replicate.getLastRelevantStatus();
            if( oStatus.isPresent() && oStatus.get().equals(CONTRIBUTED)) {
                nbValidWinners++;
            }
        }
        return nbValidWinners;
    }

    public int getNbOffChainReplicatesWithStatus(String chainTaskId, ReplicateStatus status) {
        return getNbReplicatesWithCurrentStatus(chainTaskId, status) +
                getNbReplicatesWithGivenStatusJustBeforeWorkerLost(chainTaskId, status);
    }

    /*
     * For status = CONTRIBUTED, a replicate will be counted when statuses = { CREATED, ..., CONTRIBUTED, WORKER_LOST }
     * For status = REVEALED, a replicate will be counted when statuses = { CREATED, ..., REVEALED, WORKER_LOST }
     */
    private int getNbReplicatesWithGivenStatusJustBeforeWorkerLost(String chainTaskId, ReplicateStatus status) {
        int nbReplicates = 0;
        for (Replicate replicate : getReplicates(chainTaskId)) {
            if (isStatusBeforeWorkerLostEqualsTo(replicate, status)) {
                nbReplicates++;
            }
        }
        return nbReplicates;
    }

    public Optional<Replicate> getRandomReplicateWithRevealStatus(String chainTaskId) {
        List<Replicate> revealReplicates = getReplicates(chainTaskId);
        Collections.shuffle(revealReplicates);

        for (Replicate replicate : revealReplicates) {
            if (replicate.getCurrentStatus().equals(REVEALED)) {
                return Optional.of(replicate);
            }
        }

        return Optional.empty();
    }

    public Optional<Replicate> getReplicateWithResultUploadedStatus(String chainTaskId) {
        for (Replicate replicate : getReplicates(chainTaskId)) {

            boolean isStatusResultUploaded = replicate.getCurrentStatus().equals(RESULT_UPLOADED);
            boolean isStatusResultUploadedBeforeWorkerLost = isStatusBeforeWorkerLostEqualsTo(replicate, RESULT_UPLOADED);

            if (isStatusResultUploaded || isStatusResultUploadedBeforeWorkerLost) {
                return Optional.of(replicate);
            }
        }

        return Optional.empty();
    }

    private boolean isStatusBeforeWorkerLostEqualsTo(Replicate replicate, ReplicateStatus status) {
        int size = replicate.getStatusChangeList().size();
        return size >= 2
                && replicate.getStatusChangeList().get(size - 1).getStatus().equals(WORKER_LOST)
                && replicate.getStatusChangeList().get(size - 2).getStatus().equals(status);
    }

    public boolean moreReplicatesNeeded(String chainTaskId, int nbWorkersNeeded, long maxExecutionTime) {
        int nbValidReplicates = 0;
        for (Replicate replicate : getReplicates(chainTaskId)) {
            //TODO think: When do we really need more replicates?
            boolean isReplicateSuccessfullSoFar = ReplicateStatus.getSuccessStatuses().contains(replicate.getCurrentStatus());
            boolean doesContributionTakesTooLong = !replicate.containsContributedStatus() &&
                    replicate.isCreatedMoreThanNPeriodsAgo(2, maxExecutionTime);

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
        updateReplicateStatus(chainTaskId, walletAddress, newStatus, modifier, ReplicateDetails.builder().build());
    }

    // TODO: this method needs to be refactored !
    // in case the task has been modified between reading and writing it, it is retried up to 100 times
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 100)
    public void updateReplicateStatus(String chainTaskId,
                                      String walletAddress,
                                      ReplicateStatus newStatus,
                                      ReplicateStatusModifier modifier,
                                      ReplicateDetails details) {
        ChainReceipt chainReceipt = details.getChainReceipt();

        if (newStatus == ReplicateStatus.RESULT_UPLOADED && !isResultUploaded(chainTaskId)) {
            log.error("requested updateResplicateStatus to RESULT_UPLOADED when result has not been"
                            + " uploaded to result repository yet [chainTaskId:{}, ReplicateAddress:{}]",
                    chainTaskId, walletAddress);
            return;
        }

        long receiptBlockNumber = chainReceipt != null ? chainReceipt.getBlockNumber() : 0;

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

        if (isSuccessBlockchainStatus(newStatus)) {
            replicate = getOnChainRefreshedReplicate(replicate, getChainStatus(newStatus), receiptBlockNumber);

            if (modifier.equals(ReplicateStatusModifier.POOL_MANAGER)) {
                log.warn("Replicate status set by the pool manager [chainTaskId:{}, walletAddress:{}, newStatus:{}, blockNumber:{}]",
                        chainTaskId, walletAddress, newStatus, receiptBlockNumber);
            }

            if (replicate == null) {
                log.error("Failed to refresh replicate with onchain values [chainTaskId:{}, walletAddress:{}, " +
                                "currentStatus:{}, newStatus:{}]",
                        chainTaskId, walletAddress, currentStatus, newStatus);
                return;
            }
        }

        // check that CONTRIBUTE_FAIL and REVEAL_FAIL are correct on-chain
        if (isFailedBlockchainStatus(newStatus) &&
                !isTaskStatusFailOnChain(replicate.getChainTaskId(), replicate.getWalletAddress(), receiptBlockNumber)) {
            log.warn("Replicate blockchain status sent by replicate is not valid, the replicate status will not be updated " +
                    "[chainTaskId:{}, walletAddress:{}, currentStatus:{}, newStatus:{}]", chainTaskId, walletAddress, currentStatus, newStatus);
            return;
        }

        // don't save receipt to db if no relevant info
        if (chainReceipt != null && chainReceipt.getBlockNumber() == 0 && chainReceipt.getTxHash() == null) {
            chainReceipt = null;
        }

        if (newStatus.equals(RESULT_UPLOADED)) {
            replicate.setResultLink(details.getResultLink());
            replicate.setChainCallbackData(details.getChainCallbackData());
            if (replicate.getResultLink() == null || replicate.getResultLink().isEmpty()) {
                log.error("UpdateReplicateStatus failed (empty resultLink) [chainTaskId:{}, walletAddress:{}, " +
                                "currentStatus:{}, newStatus:{}, resultLink:{}]",
                        chainTaskId, walletAddress, currentStatus, newStatus, replicate.getResultLink());
                return;
            }
        }
        replicate.updateStatus(newStatus, modifier, chainReceipt);
        replicatesRepository.save(optionalReplicates.get());

        // if replicate is not busy anymore, it can notify it
        if (!replicate.isBusyComputing()) {
            applicationEventPublisher.publishEvent(new ReplicateComputedEvent(replicate));
        }

        log.info("UpdateReplicateStatus succeeded [chainTaskId:{}, walletAddress:{}, currentStatus:{}, newStatus:{}, modifier:{}]", chainTaskId,
                walletAddress, currentStatus, newStatus, modifier);
        applicationEventPublisher.publishEvent(new ReplicateUpdatedEvent(replicate.getChainTaskId(), replicate.getWalletAddress(), newStatus));
    }

    @Recover
    public void updateReplicateStatus(OptimisticLockingFailureException exception,
                                      String chainTaskId,
                                      String walletAddress,
                                      ReplicateStatus newStatus,
                                      ReplicateStatusModifier modifier,
                                      ReplicateDetails details) {
        log.error("Maximum number of tries reached [exception:{}]", exception.getMessage());
        exception.printStackTrace();
    }

    private boolean isSuccessBlockchainStatus(ReplicateStatus newStatus) {
        return getChainStatus(newStatus) != null;
    }

    private boolean isFailedBlockchainStatus(ReplicateStatus status) {
        return Arrays.asList(CONTRIBUTE_FAILED, REVEAL_FAILED).contains(status);
    }

    private Replicate getOnChainRefreshedReplicate(Replicate replicate, ChainContributionStatus wishedChainStatus, long blockNumber) {
        // check that the blockNumber is already available for the scheduler
        if (!web3jService.isBlockAvailable(blockNumber)) {
            log.error("This block number is not available, even after waiting for some time [blockNumber:{}]", blockNumber);
            return null;
        }

        boolean isWishedStatusProvedOnChain = iexecHubService.doesWishedStatusMatchesOnChainStatus(replicate.getChainTaskId(), replicate.getWalletAddress(), wishedChainStatus);
        if (isWishedStatusProvedOnChain) {
            return getReplicateWithBlockchainUpdates(replicate, wishedChainStatus);
        } else {
            log.error("Onchain status is different from wishedChainStatus (should wait?) [chainTaskId:{}, worker:{}, " +
                    "blockNumber:{}, wishedChainStatus:{}]", replicate.getChainTaskId(), replicate.getWalletAddress(), blockNumber, wishedChainStatus);
        }

        return null;
    }

    private Replicate getReplicateWithBlockchainUpdates(Replicate replicate, ChainContributionStatus wishedChainStatus) {
        Optional<ChainContribution> optional = iexecHubService.getChainContribution(replicate.getChainTaskId(), replicate.getWalletAddress());
        if (!optional.isPresent()) {
            return null;
        }

        ChainContribution chainContribution = optional.get();
        if (wishedChainStatus.equals(ChainContributionStatus.CONTRIBUTED)) {
            replicate.setContributionHash(chainContribution.getResultHash());
        }
        return replicate;
    }

    private boolean isTaskStatusFailOnChain(String chainTaskId, String walletAddress, long blockNumber) {
        if (!web3jService.isBlockAvailable(blockNumber)) {
            log.error("This block number is not available, even after waiting for some time [blockNumber:{}]", blockNumber);
            return false;
        }

        Optional<ChainContribution> optional = iexecHubService.getChainContribution(chainTaskId, walletAddress);
        if (!optional.isPresent()) {
            return false;
        }

        ChainContribution contribution = optional.get();
        ChainContributionStatus chainStatus = contribution.getStatus();
        if (!chainStatus.equals(ChainContributionStatus.CONTRIBUTED) && !chainStatus.equals(ChainContributionStatus.REVEALED)) {
            return true;
        } else {
            log.warn("The onchain status of the contribution is not a failed one " +
                    "[chainTaskId:{}, wallet:{}, blockNumber:{}, onChainStatus:{}]", chainStatus, walletAddress, blockNumber, chainStatus);
            return false;
        }
    }

    public boolean isResultUploaded(String chainTaskId) {
        // currently no check in case of IPFS
        if (iexecHubService.isPublicResult(chainTaskId, 0)) {
            return true;
        }

        Optional<Eip712Challenge> oEip712Challenge = resultRepoService.getChallenge();
        if (!oEip712Challenge.isPresent()) return false;

        Eip712Challenge eip712Challenge = oEip712Challenge.get();
        ECKeyPair ecKeyPair = credentialsService.getCredentials().getEcKeyPair();
        String walletAddress = credentialsService.getCredentials().getAddress();

        // sign the eip712 challenge and build authorization token
        String authorizationToken = Eip712ChallengeUtils.buildAuthorizationToken(eip712Challenge,
                walletAddress, ecKeyPair);

        if (authorizationToken.isEmpty()) {
            return false;
        }

        return resultRepoService.isResultUploaded(authorizationToken, chainTaskId);
    }

    public boolean didReplicateContributeOnchain(String chainTaskId, String walletAddress) {
        return iexecHubService.doesWishedStatusMatchesOnChainStatus(
                chainTaskId, walletAddress, getChainStatus(ReplicateStatus.CONTRIBUTED));
    }

    public boolean didReplicateRevealOnchain(String chainTaskId, String walletAddress) {
        return iexecHubService.doesWishedStatusMatchesOnChainStatus(
                chainTaskId, walletAddress, getChainStatus(ReplicateStatus.REVEALED));
    }

    public void setRevealTimeoutStatusIfNeeded(String chainTaskId, Replicate replicate) {
        if (replicate.getCurrentStatus().equals(REVEALING) ||
                replicate.getCurrentStatus().equals(CONTRIBUTED) ||
                replicate.isLostAfterStatus(REVEALING) ||
                replicate.isLostAfterStatus(CONTRIBUTED)) {
            updateReplicateStatus(chainTaskId, replicate.getWalletAddress(),
                    REVEAL_TIMEOUT, ReplicateStatusModifier.POOL_MANAGER);
        }
    }
}