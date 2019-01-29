package com.iexec.core.chain;

import com.iexec.common.chain.*;
import com.iexec.common.contract.generated.App;
import com.iexec.common.contract.generated.IexecClerkABILegacy;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.common.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import rx.Observable;

import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static com.iexec.common.chain.ChainContributionStatus.*;
import static com.iexec.core.utils.DateTimeUtils.now;

@Slf4j
@Service
public class IexecHubService {

    private final IexecHubABILegacy iexecHub;
    private final IexecClerkABILegacy iexecClerk;
    private final ThreadPoolExecutor executor;
    private final Credentials credentials;
    private final Web3j web3j;
    private ChainConfig chainConfig;

    @Autowired
    public IexecHubService(CredentialsService credentialsService,
                           Web3jService web3jService,
                           ChainConfig chainConfig) {
        this.chainConfig = chainConfig;
        this.credentials = credentialsService.getCredentials();
        this.web3j = web3jService.getWeb3j();
        this.iexecHub = ChainUtils.loadHubContract(credentials, web3j, chainConfig.getHubAddress());
        this.iexecClerk = ChainUtils.loadClerkContract(credentials, web3j, chainConfig.getHubAddress());
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
    }

    public Optional<ChainContribution> getContribution(String chainTaskId, String workerWalletAddress) {
        return ChainUtils.getChainContribution(iexecHub, chainTaskId, workerWalletAddress);
    }

    public boolean checkContributionStatus(String chainTaskId, String walletAddress, ChainContributionStatus statusToCheck) {

        Optional<ChainContribution> optional = getContribution(chainTaskId, walletAddress);
        if (!optional.isPresent()) {
            return false;
        }

        ChainContribution chainContribution = optional.get();
        ChainContributionStatus chainStatus = chainContribution.getStatus();
        switch (statusToCheck) {
            case CONTRIBUTED:
                if (chainStatus.equals(UNSET)) {
                    return false;
                } else {
                    // has at least contributed
                    return chainStatus.equals(CONTRIBUTED) || chainStatus.equals(REVEALED);
                }
            case REVEALED:
                if (chainStatus.equals(CONTRIBUTED)) {
                    return false;
                } else {
                    // has at least revealed
                    return chainStatus.equals(REVEALED);
                }
            default:
                break;
        }
        return false;
    }

    public boolean canInitialize(String chainDealId, int taskIndex) {
        String generatedChainTaskId = ChainUtils.generateChainTaskId(chainDealId, BigInteger.valueOf(taskIndex));
        Optional<ChainTask> optional = getChainTask(generatedChainTaskId);
        return optional.map(chainTask -> chainTask.getStatus().equals(ChainTaskStatus.UNSET)).orElse(false);
    }

    public Optional<Pair<String, ChainReceipt>> initialize(String chainDealId, int taskIndex) {
        log.info("Requested  initialize [chainDealId:{}, taskIndex:{}, waitingTxCount:{}]", chainDealId, taskIndex, getWaitingTransactionCount());
        try {
            return CompletableFuture.supplyAsync(() -> sendInitializeTransaction(chainDealId, taskIndex), executor).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    private Optional<Pair<String, ChainReceipt>> sendInitializeTransaction(String chainDealId, int taskIndex) {
        byte[] chainDealIdBytes = BytesUtils.stringToBytes(chainDealId);
        BigInteger taskIndexBigInteger = BigInteger.valueOf(taskIndex);

        TransactionReceipt receipt;
        try {
            receipt = iexecHub.initialize(chainDealIdBytes, taskIndexBigInteger).send();
        } catch (Exception e) {
            log.error("Failed initialize [chainDealId:{}, taskIndex:{}, error:{}]",
                    chainDealId, taskIndex, e.getMessage());
            return Optional.empty();
        }

        if (iexecHub.getTaskInitializeEvents(receipt).isEmpty()) {
            log.error("Failed to get initialise event [chainDealId:{}, taskIndex:{}]", chainDealId, taskIndex);
            return Optional.empty();
        }

        IexecHubABILegacy.TaskInitializeEventResponse taskInitializedEventResponse = iexecHub.getTaskInitializeEvents(receipt).get(0);
        String chainTaskId = BytesUtils.bytesToString(taskInitializedEventResponse.taskid);

        log.info("Initialized [chainTaskId:{}, chainDealId:{}, taskIndex:{}, gasUsed:{}]",
                chainTaskId, chainDealId, taskIndex, receipt.getGasUsed());
        
        ChainReceipt chainReceipt = ChainUtils.buildChainReceipt(taskInitializedEventResponse.log, chainTaskId);
        return Optional.of(Pair.of(chainTaskId, chainReceipt));
    }

    public boolean canFinalize(String chainTaskId) {
        Optional<ChainTask> optional = getChainTask(chainTaskId);
        if (!optional.isPresent()) {
            return false;
        }
        ChainTask chainTask = optional.get();

        boolean isChainTaskStatusRevealing = chainTask.getStatus().equals(ChainTaskStatus.REVEALING);
        boolean isFinalDeadlineInFuture = now() < chainTask.getFinalDeadline();
        boolean hasEnoughRevealors = (chainTask.getRevealCounter() == chainTask.getWinnerCounter())
                || (chainTask.getRevealCounter() > 0 && chainTask.getRevealDeadline() <= now());

        boolean ret = isChainTaskStatusRevealing && isFinalDeadlineInFuture && hasEnoughRevealors;
        if (ret) {
            log.info("Finalizable onchain [chainTaskId:{}]", chainTaskId);
        } else {
            log.warn("Can't finalize [chainTaskId:{}, " +
                            "isChainTaskStatusRevealing:{}, isFinalDeadlineInFuture:{}, hasEnoughRevealors:{}]", chainTaskId,
                    isChainTaskStatusRevealing, isFinalDeadlineInFuture, hasEnoughRevealors);
        }
        return ret;
    }

    public boolean finalizeTask(String chainTaskId, String result) {
        log.info("Requested  finalize [chainTaskId:{}, waitingTxCount:{}]", chainTaskId, getWaitingTransactionCount());
        try {
            return CompletableFuture.supplyAsync(() -> sendFinalizeTransaction(chainTaskId, result), executor).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean sendFinalizeTransaction(String chainTaskId, String result) {
        try {
            RemoteCall<TransactionReceipt> finalizeCall = iexecHub.finalize(BytesUtils.stringToBytes(chainTaskId),
                    BytesUtils.stringToBytes(result));
            log.info("Sent finalize [chainTaskId:{}, result:{}]", chainTaskId, result);
            TransactionReceipt finalizeReceipt = finalizeCall.send();
            if (!iexecHub.getTaskFinalizeEvents(finalizeReceipt).isEmpty()) {
                log.info("Finalized [chainTaskId:{}, result:{}, gasUsed:{}]", chainTaskId, result, finalizeReceipt.getGasUsed());
                return true;
            }
        } catch (Exception e) {
            log.error("Failed finalize [chainTaskId:{}, result:{}]", chainTaskId, result);
        }
        return false;
    }

    public boolean canReopen(String chainTaskId) {
        Optional<ChainTask> optional = getChainTask(chainTaskId);
        if (!optional.isPresent()) {
            return false;
        }
        ChainTask chainTask = optional.get();

        boolean isChainTaskStatusRevealing = chainTask.getStatus().equals(ChainTaskStatus.REVEALING);
        boolean isBeforeFinalDeadline = now() < chainTask.getFinalDeadline();
        boolean isAfterRevealDeadline = chainTask.getRevealDeadline() <= now();
        boolean revealCounterEqualsZero = chainTask.getRevealCounter() == 0;

        boolean check = isChainTaskStatusRevealing && isBeforeFinalDeadline && isAfterRevealDeadline
                && revealCounterEqualsZero;
        if (check) {
            log.info("Reopenable onchain [chainTaskId:{}]", chainTaskId);
        } else {
            log.warn("Can't reopen [chainTaskId:{}, " +
                            "isChainTaskStatusRevealing:{}, isBeforeFinalDeadline:{}, " +
                            "isAfterRevealDeadline:{}, revealCounterEqualsZero:{}]", chainTaskId,
                    isChainTaskStatusRevealing, isBeforeFinalDeadline, isAfterRevealDeadline, revealCounterEqualsZero);
        }
        return check;
    }

    public boolean reOpen(String chainTaskId) {
        log.info("Requested  reopen [chainTaskId:{}, waitingTxCount:{}]", chainTaskId, getWaitingTransactionCount());
        try {
            return CompletableFuture.supplyAsync(() -> sendReopenTransaction(chainTaskId), executor).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean sendReopenTransaction(String chainTaskId) {
        try {
            TransactionReceipt receipt = iexecHub.reopen(BytesUtils.stringToBytes(chainTaskId)).send();
            if (!iexecHub.getTaskReopenEvents(receipt).isEmpty()) {
                log.info("Reopened [chainTaskId:{}, gasUsed:{}]", chainTaskId, receipt.getGasUsed());
                return true;
            }
        } catch (Exception e) {
            log.error("Failed reopen [chainTaskId:{}, error:{}]", chainTaskId, e.getMessage());
        }
        return false;
    }

    private long getWaitingTransactionCount() {
        return executor.getTaskCount() - executor.getCompletedTaskCount();
    }

    public Optional<ChainDeal> getChainDeal(String chainDealId) {
        return ChainUtils.getChainDeal(credentials, web3j, iexecHub.getContractAddress(), chainDealId);
    }

    public Optional<ChainTask> getChainTask(String chainTaskId) {
        return ChainUtils.getChainTask(iexecHub, chainTaskId);
    }

    Optional<ChainApp> getChainApp(String address) {
        App app = ChainUtils.loadDappContract(credentials, web3j, address);
        return ChainUtils.getChainApp(app);
    }

    Observable<Optional<DealEvent>> getDealEventObservableToLatest(BigInteger from) {
        return getDealEventObservable(from, null);
    }

    Observable<Optional<DealEvent>> getDealEventObservable(BigInteger from, BigInteger to) {
        DefaultBlockParameter fromBlock = DefaultBlockParameter.valueOf(from);
        DefaultBlockParameter toBlock = DefaultBlockParameterName.LATEST;
        if (to != null) {
            toBlock = DefaultBlockParameter.valueOf(to);
        }
        return iexecClerk.schedulerNoticeEventObservable(fromBlock, toBlock).map(schedulerNotice -> {

            if (schedulerNotice.workerpool.equalsIgnoreCase(chainConfig.getPoolAddress())) {
                return Optional.of(new DealEvent(schedulerNotice));
            }
            return Optional.empty();
        });
    }

    public boolean hasEnoughGas() {
        return ChainUtils.hasEnoughGas(web3j, credentials.getAddress());
    }
}
