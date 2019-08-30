package com.iexec.core.replicate;

import com.iexec.common.chain.ChainContribution;
import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.notification.TaskNotificationType;
import com.iexec.common.replicate.*;
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
import static com.iexec.common.replicate.ReplicateStatusCause.*;

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
                Replicate replicate = new Replicate(walletAddress, chainTaskId);
                replicate.setWorkerWeight(iexecHubService.getWorkerWeight(walletAddress));// workerWeight value for pendingWeight estimate
                replicatesList.getReplicates().add(replicate);

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
            List<ReplicateStatus> listReplicateStatus = replicate.getStatusUpdateList().stream()
                    .map(ReplicateStatusUpdate::getStatus)
                    .collect(Collectors.toList());
            for (ReplicateStatus status : listStatus) {
                if (listReplicateStatus.contains(status)) {
                    addressReplicates.add(replicate.getWalletAddress());
                }
            }
        }
        return addressReplicates.size();
    }

    public int getNbValidContributedWinners(String chainTaskId, String contributionHash) {
        int nbValidWinners = 0;
        for (Replicate replicate : getReplicates(chainTaskId)) {
            Optional<ReplicateStatus> oStatus = replicate.getLastRelevantStatus();
            if (oStatus.isPresent() && oStatus.get().equals(CONTRIBUTED)
                    && contributionHash.equals(replicate.getContributionHash())) {
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
        int size = replicate.getStatusUpdateList().size();
        return size >= 2
                && replicate.getStatusUpdateList().get(size - 1).getStatus().equals(WORKER_LOST)
                && replicate.getStatusUpdateList().get(size - 2).getStatus().equals(status);
    }

    /*
     * This implicitly sets the modifier to POOL_MANAGER
     */
    public void updateReplicateStatus(String chainTaskId,
                                      String walletAddress,
                                      ReplicateStatus newStatus) {
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.poolManagerRequest(newStatus);
        updateReplicateStatus(chainTaskId, walletAddress, statusUpdate);
    }

    /*
     * This implicitly sets the modifier to POOL_MANAGER
     */
    public void updateReplicateStatus(String chainTaskId,
                                      String walletAddress,
                                      ReplicateStatus newStatus,
                                      ReplicateStatusDetails details) {
        ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.poolManagerRequest(newStatus, details);
        updateReplicateStatus(chainTaskId, walletAddress, statusUpdate);
    }

    // TODO: this method needs to be refactored !
    // in case the task has been modified between reading and writing it, it is retried up to 100 times
    @Retryable(value = {OptimisticLockingFailureException.class}, maxAttempts = 100)
    public Optional<TaskNotificationType> updateReplicateStatus(String chainTaskId,
                                                                String walletAddress,
                                                                ReplicateStatusUpdate statusUpdate) {

        ReplicateStatus newStatus = statusUpdate.getStatus();
        ReplicateStatusModifier modifier = statusUpdate.getModifier();

        ReplicateStatus currentStatus = null;
        Replicate replicate;
        Optional<ReplicatesList> replicates = getReplicatesList(chainTaskId);
        if (replicates.isEmpty()) {
            log.error(getReplicateStatusErrorLog("replicateList missing",chainTaskId, walletAddress, currentStatus, newStatus, modifier));
            return Optional.empty();
        }

        Optional<Replicate> optionalReplicate = replicates.get().getReplicateOfWorker(walletAddress);
        if (optionalReplicate.isEmpty()) {
            log.error(getReplicateStatusErrorLog("replicate missing", chainTaskId, walletAddress, currentStatus, newStatus, modifier));
            return Optional.empty();
        }
        replicate = optionalReplicate.get();
        currentStatus = replicate.getCurrentStatus();

        if (modifier.equals(ReplicateStatusModifier.WORKER) &&
                !ReplicateWorkflow.getInstance().isValidTransition(currentStatus, newStatus)) {
            log.error(getReplicateStatusErrorLog("bad workflow transition",chainTaskId, walletAddress, currentStatus, newStatus, modifier));
            return Optional.empty();
        }

        replicate = updateReplicateFields(chainTaskId, replicate, statusUpdate);

        if (replicate == null){
            log.error(getReplicateStatusErrorLog("updateReplicateFields failed",chainTaskId, walletAddress, currentStatus, newStatus, modifier));
            return Optional.empty();
        }

        replicatesRepository.save(replicates.get());
        log.info(getReplicateStatusSuccessLog(chainTaskId, walletAddress, currentStatus, newStatus, modifier));
        applicationEventPublisher.publishEvent(new ReplicateUpdatedEvent(replicate.getChainTaskId(),
                replicate.getWalletAddress(), statusUpdate));
        TaskNotificationType nextAction = ReplicateWorkflow.getInstance().getNextAction(newStatus);
        if (nextAction != null) {
            return Optional.of(nextAction);
        }

        log.error(getReplicateStatusWarningLog("no nextAction found", chainTaskId, walletAddress, currentStatus,
                newStatus, modifier));
        return Optional.empty();
    }

    private Replicate updateReplicateFields(String chainTaskId, Replicate replicate, ReplicateStatusUpdate statusUpdate) {
        String walletAddress = replicate.getWalletAddress();
        ReplicateStatus newStatus = statusUpdate.getStatus();
        ReplicateStatusDetails details = statusUpdate.getDetails();

        ChainContributionStatus onChainStatus = getChainStatus(newStatus);
        if (isSuccessBlockchainStatus(newStatus)) {
            if (!iexecHubService.isStatusTrueOnChain(chainTaskId, walletAddress, onChainStatus)){
                log.error(getReplicateFieldsErrorLog("wrong onchain status", chainTaskId, walletAddress));
                return null;
            }
            long receiptBlockNumber = details.getChainReceipt() != null ? details.getChainReceipt().getBlockNumber() : 0;
            if (!web3jService.isBlockAvailable(receiptBlockNumber)) {
                log.error(getReplicateFieldsErrorLog("core block not available", chainTaskId, walletAddress));
                return null;
            }

            if (newStatus.equals(CONTRIBUTED)) {
                Optional<ChainContribution> chainContribution = iexecHubService.getChainContribution(replicate.getChainTaskId(), replicate.getWalletAddress());
                if (!chainContribution.isPresent()) {
                    log.error(getReplicateFieldsErrorLog("get chainContribution failed", chainTaskId, walletAddress));
                    return null;
                }
                int workerWeight = iexecHubService.getWorkerWeight(replicate.getWalletAddress());
                if (workerWeight == 0) {
                    log.error(getReplicateFieldsErrorLog("get workerWeight failed", chainTaskId, walletAddress));
                    return null;
                }
                replicate.setContributionHash(chainContribution.get().getResultHash());
                replicate.setWorkerWeight(workerWeight);//Should update weight on contributed
            }

        } else if (newStatus.equals(RESULT_UPLOADED)) {
            if (!isResultUploaded(chainTaskId)){
                log.error(getReplicateFieldsErrorLog("result not uploaded", chainTaskId, walletAddress));
                return null;
            }
            if (details.getResultLink() == null || (details.getResultLink() != null && details.getResultLink().isEmpty())) {
                log.error(getReplicateFieldsErrorLog("resultLink missing", chainTaskId, walletAddress));
                return null;
            }

            replicate.setResultLink(details.getResultLink());
            replicate.setChainCallbackData(details.getChainCallbackData());
        }

        replicate.updateStatus(statusUpdate);
        return replicate;
    }

    private String getReplicateStatusErrorLog(String error, String chainTaskId, String walletAddress, ReplicateStatus currentStatus, ReplicateStatus newStatus, ReplicateStatusModifier modifier) {
        return getUpdateReplicateStatusLogMessage("Failed to updateReplicateStatus", error, chainTaskId, walletAddress, currentStatus, newStatus, modifier);
    }

    private String getReplicateStatusWarningLog(String warning, String chainTaskId, String walletAddress,
                                                ReplicateStatus currentStatus, ReplicateStatus newStatus,
                                                ReplicateStatusModifier modifier) {
        return getUpdateReplicateStatusLogMessage("Replicate status updated", warning, chainTaskId, walletAddress, currentStatus, newStatus, modifier);
    }

    private String getReplicateStatusSuccessLog(String chainTaskId, String walletAddress, ReplicateStatus currentStatus, ReplicateStatus newStatus, ReplicateStatusModifier modifier) {
        return getUpdateReplicateStatusLogMessage("UpdateReplicateStatus succeeded", "", chainTaskId, walletAddress, currentStatus, newStatus, modifier);
    }

    private String getUpdateReplicateStatusLogMessage(String messageSubject, String error, String chainTaskId, String walletAddress, ReplicateStatus currentStatus, ReplicateStatus newStatus, ReplicateStatusModifier modifier) {
        if (error != null && !error.isEmpty()){
            error = "(" + error + ")";
        }
        return String.format("%s %s [chainTaskId:%s, walletAddress:%s, currentStatus:%s, newStatus:%s, modifier:%s]",
                messageSubject, error, chainTaskId, walletAddress, currentStatus, newStatus, modifier);
    }

    private String getReplicateFieldsErrorLog(String error, String chainTaskId, String walletAddress) {
        return String.format("Failed to updateReplicateFields (%s) [chainTaskId:%s, walletAddress:%s]",
                error, chainTaskId, walletAddress);
    }

    @Recover
    public void updateReplicateStatus(OptimisticLockingFailureException exception,
                                      String chainTaskId,
                                      String walletAddress,
                                      ReplicateStatusUpdate statusUpdate) {
        log.error("Maximum number of tries reached [exception:{}]", exception.getMessage());
        exception.printStackTrace();
    }

    private boolean isSuccessBlockchainStatus(ReplicateStatus newStatus) {
        return getChainStatus(newStatus) != null;
    }

    private boolean isFailedBlockchainStatus(ReplicateStatus status) {
        return Arrays.asList(CONTRIBUTE_FAILED, REVEAL_FAILED).contains(status);
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
        return iexecHubService.isStatusTrueOnChain(
                chainTaskId, walletAddress, getChainStatus(ReplicateStatus.CONTRIBUTED));
    }

    public boolean didReplicateRevealOnchain(String chainTaskId, String walletAddress) {
        return iexecHubService.isStatusTrueOnChain(
                chainTaskId, walletAddress, getChainStatus(ReplicateStatus.REVEALED));
    }

    public void setRevealTimeoutStatusIfNeeded(String chainTaskId, Replicate replicate) {
        Optional<ReplicateStatus> oStatus = replicate.getLastRelevantStatus();
        if(!oStatus.isPresent()){
            return;
        }
        ReplicateStatus status = oStatus.get();
        if (status.equals(REVEALING) || status.equals(CONTRIBUTED)) {
            ReplicateStatusUpdate statusUpdate = ReplicateStatusUpdate.poolManagerRequest(FAILED, REVEAL_TIMEOUT);
            updateReplicateStatus(chainTaskId, replicate.getWalletAddress(), statusUpdate);
        }
    }
}