package com.iexec.core.chain;

import com.iexec.common.chain.*;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.common.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple4;

import java.math.BigInteger;

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

    public ChainContribution getContribution(String chainTaskId, String workerWalletAddress) {
        Tuple4<BigInteger, byte[], byte[], String> contributionTuple = null;
        try {
            contributionTuple = iexecHub.viewContributionABILegacy(BytesUtils.stringToBytes(chainTaskId), workerWalletAddress).send();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ChainContribution.tuple2Contribution(contributionTuple);
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

        ChainContribution onChainContribution = getContribution(chainTaskId, walletAddress);
        if (onChainContribution != null) {
            ChainContributionStatus onChainStatus = onChainContribution.getStatus();
            switch (statusToCheck) {
                case CONTRIBUTED:
                    if (onChainStatus.equals(UNSET)) { // should wait
                        return checkContributionStatusRecursively(chainTaskId, walletAddress, statusToCheck, tryIndex);
                    } else {
                        // has at least contributed
                        return onChainStatus.equals(CONTRIBUTED) || onChainStatus.equals(REVEALED);
                    }
                case REVEALED:
                    if (onChainStatus.equals(CONTRIBUTED)) { // should wait
                        return checkContributionStatusRecursively(chainTaskId, walletAddress, statusToCheck, tryIndex);
                    } else {
                        // has at least revealed
                        return onChainStatus.equals(REVEALED);
                    }
                default:
                    break;
            }
        }
        return false;
    }


    public String initializeTask(byte[] dealId, int numTask) throws Exception {
        log.info("Trying to Initialize task on-chain [dealId:{}, numTask:{}]", BytesUtils.bytesToString(dealId), numTask);
        TransactionReceipt res = iexecHub.initialize(dealId, BigInteger.valueOf(numTask)).send();
        if (!iexecHub.getTaskInitializeEvents(res).isEmpty()) {
            log.info("Initialize task on-chain succeeded[dealId:{}, numTask:{}]", BytesUtils.bytesToString(dealId), numTask);
            return BytesUtils.bytesToString(iexecHub.getTaskInitializeEvents(res).get(0).taskid);
        }
        return null;
    }

    public ChainTask getChainTask(String chainTaskId) {
        try {
            return ChainTask.tuple2ChainTask(iexecHub.viewTaskABILegacy(BytesUtils.stringToBytes(chainTaskId)).send());
        } catch (Exception e) {
            log.error("Failed to view chainTask [chainTaskId:{}, error:{}]", chainTaskId, e.getMessage());
        }
        return null;
    }

    public boolean canFinalize(String chainTaskId) {
        ChainTask chainTask = getChainTask(chainTaskId);

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
