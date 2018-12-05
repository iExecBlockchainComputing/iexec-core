package com.iexec.core.chain;

import com.iexec.common.chain.*;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.common.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple4;

import java.math.BigInteger;
import java.util.Optional;

import static com.iexec.common.chain.ChainContributionStatus.*;
import static com.iexec.common.chain.ChainUtils.getWeb3j;
import static com.iexec.core.utils.DateTimeUtils.now;
import static com.iexec.core.utils.DateTimeUtils.sleep;

@Slf4j
@Service
public class IexecHubService {

    private final IexecHubABILegacy iexecHub;

    @Autowired
    public IexecHubService(CredentialsService credentialsService,
                           ChainConfig chainConfig) {
        Credentials credentials = credentialsService.getCredentials();
        Web3j web3j = getWeb3j(chainConfig.getPrivateChainAddress());
        this.iexecHub = ChainUtils.loadHubContract(credentials, web3j, chainConfig.getHubAddress());
    }

    public Optional<ChainContribution> getContribution(String chainTaskId, String workerWalletAddress) {
        return ChainUtils.getChainContribution(iexecHub, chainTaskId, workerWalletAddress);
    }

    public boolean checkContributionStatusMultipleTimes(String chainTaskId, String walletAddress, ChainContributionStatus statusToCheck) {
        return checkContributionStatusRecursivelyWithDelay(chainTaskId, walletAddress, statusToCheck);
    }

    private boolean checkContributionStatusRecursivelyWithDelay(String chainTaskId, String walletAddress, ChainContributionStatus statusToCheck) {
        return sleep(500) && checkContributionStatusRecursively(chainTaskId, walletAddress, statusToCheck, 0);
    }

    private boolean checkContributionStatusRecursively(String chainTaskId, String walletAddress, ChainContributionStatus statusToCheck, int tryIndex) {
        int MAX_RETRY = 3;

        if (tryIndex >= MAX_RETRY) {
            return false;
        }
        tryIndex++;

        Optional<ChainContribution> optional = getContribution(chainTaskId, walletAddress);
        if (!optional.isPresent()){
            return false;
        }

        ChainContribution chainContribution = optional.get();
        ChainContributionStatus chainStatus = chainContribution.getStatus();
        switch (statusToCheck) {
            case CONTRIBUTED:
                if (chainStatus.equals(UNSET)) { // should wait
                    return checkContributionStatusRecursively(chainTaskId, walletAddress, statusToCheck, tryIndex);
                } else {
                    // has at least contributed
                    return chainStatus.equals(CONTRIBUTED) || chainStatus.equals(REVEALED);
                }
            case REVEALED:
                if (chainStatus.equals(CONTRIBUTED)) { // should wait
                    return checkContributionStatusRecursively(chainTaskId, walletAddress, statusToCheck, tryIndex);
                } else {
                    // has at least revealed
                    return chainStatus.equals(REVEALED);
                }
            default:
                break;
        }
        return false;
    }

    public String initializeTask(String chainDealId, int taskIndex){
        log.info("Transaction initializeTask started [chainDealId:{}, taskIndex:{}]", chainDealId, taskIndex);
        try {
            TransactionReceipt initializeReceipt = iexecHub.initialize(BytesUtils.stringToBytes(chainDealId), BigInteger.valueOf(taskIndex)).send();
            if (!iexecHub.getTaskInitializeEvents(initializeReceipt).isEmpty()) {
                IexecHubABILegacy.TaskInitializeEventResponse taskInitializedEvent = iexecHub.getTaskInitializeEvents(initializeReceipt).get(0);
                String chainTaskId = BytesUtils.bytesToString(taskInitializedEvent.taskid);
                log.info("Transaction initializeTask completed [chainTaskId:{}, chainDealId:{}, taskIndex:{}]",
                        chainTaskId, chainDealId, taskIndex);
                return chainTaskId;
            }
        } catch (Exception e) {
            log.error("Transaction initializeTask failed [chainDealId:{}, taskIndex:{}]",
                    chainDealId, taskIndex);
        }
        return null;
    }

    public Optional<ChainTask> getChainTask(String chainTaskId) {
        return ChainUtils.getChainTask(iexecHub, chainTaskId);
    }

    public boolean canFinalize(String chainTaskId) {
        Optional<ChainTask> optional = getChainTask(chainTaskId);
        if (!optional.isPresent()){
            return false;
        }
        ChainTask chainTask = optional.get();

        boolean isChainTaskStatusRevealing = chainTask.getStatus().equals(ChainTaskStatus.REVEALING);
        boolean isConsensusDeadlineInFuture = now() < chainTask.getConsensusDeadline();
        boolean hasEnoughRevealors = (chainTask.getRevealCounter() == chainTask.getWinnerCounter())
                || (chainTask.getRevealCounter() > 0 && chainTask.getRevealDeadline() <= now());

        boolean ret = isChainTaskStatusRevealing && isConsensusDeadlineInFuture && hasEnoughRevealors;
        if (ret) {
            log.info("All the conditions are valid for the finalization to happen [chainTaskId:{}]", chainTaskId);
        } else {
            log.warn("One or more conditions are not met for the finalization to happen [chainTaskId:{}, " +
                            "isChainTaskStatusRevealing:{}, isConsensusDeadlineInFuture:{}, hasEnoughRevealors:{}]", chainTaskId,
                    isChainTaskStatusRevealing, isConsensusDeadlineInFuture, hasEnoughRevealors);
        }
        return ret;
    }

    public boolean finalizeTask(String chainTaskId, String result) {
        try {
            log.info("Trying Finalize task on-chain [chainTaskId:{}, result:{}]", chainTaskId, result);
            TransactionReceipt receipt = iexecHub.finalize(BytesUtils.stringToBytes(chainTaskId),
                    BytesUtils.stringToBytes(result)).send();
            if (!iexecHub.getTaskFinalizeEvents(receipt).isEmpty()) {
                log.info("Finalize on-chain succeeded [chainTaskId:{}, result:{}]", chainTaskId, result);
                return true;
            }
        } catch (Exception e) {
            log.error("Finalize on-chain failed [chainTaskId:{}, result:{}]", chainTaskId, result);
        }
        return false;
    }

    public IexecHubABILegacy getIexecHub() {
        return iexecHub;
    }
}
