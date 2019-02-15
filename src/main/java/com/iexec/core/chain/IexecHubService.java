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
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import rx.Observable;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
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

    private final ThreadPoolExecutor executor;
    private final Credentials credentials;
    private final Web3jService web3jService;
    private final Web3j web3j;
    private ChainConfig chainConfig;

    private static final String INITIALIZING_PENDING_STATUS = "pending";

    @Autowired
    public IexecHubService(CredentialsService credentialsService,
                           Web3jService web3jService,
                           ChainConfig chainConfig
                           ) {
        this.chainConfig = chainConfig;
        this.credentials = credentialsService.getCredentials();
        this.web3jService = web3jService;
        this.web3j = web3jService.getWeb3j();
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
    }

    private IexecHubABILegacy getHubContract() {
        return ChainUtils.getHubContract(credentials,
                this.web3j,
                chainConfig.getHubAddress(),
                ChainUtils.getWritingContractGasProvider(web3j,
                        chainConfig.getGasPriceMultiplier(),
                        chainConfig.getGasPriceCap()));
    }

    private IexecClerkABILegacy getClerkContract() {
        return ChainUtils.getClerkContract(credentials,
                this.web3j,
                chainConfig.getHubAddress(),
                ChainUtils.getWritingContractGasProvider(web3j,
                        chainConfig.getGasPriceMultiplier(),
                        chainConfig.getGasPriceCap()));
    }


    public Optional<ChainContribution> getContribution(String chainTaskId, String workerWalletAddress) {
        return ChainUtils.getChainContribution(getHubContract(), chainTaskId, workerWalletAddress);
    }

    public boolean doesWishedStatusMatchesOnChainStatus(String chainTaskId, String walletAddress, ChainContributionStatus wishedStatus) {

        Optional<ChainContribution> optional = getContribution(chainTaskId, walletAddress);
        if (!optional.isPresent()) {
            return false;
        }

        ChainContribution chainContribution = optional.get();
        ChainContributionStatus chainStatus = chainContribution.getStatus();
        switch (wishedStatus) {
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
        boolean isTaskUnsetOnChain = isTaskUnsetOnChain(chainDealId, taskIndex);
        boolean isBeforeContributionDeadline = isDateBeforeContributionDeadline(new Date(), chainDealId);
        return isBeforeContributionDeadline && isTaskUnsetOnChain;
    }

    private boolean isTaskUnsetOnChain(String chainDealId, int taskIndex) {
        String generatedChainTaskId = ChainUtils.generateChainTaskId(chainDealId, BigInteger.valueOf(taskIndex));
        Optional<ChainTask> optional = getChainTask(generatedChainTaskId);
        return optional.map(chainTask -> chainTask.getStatus().equals(ChainTaskStatus.UNSET)).orElse(false);
    }

    private boolean isDateBeforeContributionDeadline(Date date, String chainDealId) {
        Optional<ChainDeal> chainDeal = getChainDeal(chainDealId);
        if (!chainDeal.isPresent()) {
            return false;
        }

        long startTime = chainDeal.get().getStartTime().longValue() * 1000;
        long maxExecutionTime = chainDeal.get().getChainCategory().getMaxExecutionTime();
        long maxNbOfPeriods = getMaxNbOfPeriodsForConsensus();

        return date.getTime() < startTime + maxExecutionTime * maxNbOfPeriods;
    }

    private long getMaxNbOfPeriodsForConsensus() {
        try {
            return getHubContract().CONSENSUS_DURATION_RATIO().send().longValue();
        } catch (Exception e) {
            log.error("Failed to getMaxNbOfPeriodsForConsensus");
        }
        return 0;
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
            receipt = getHubContract().initialize(chainDealIdBytes, taskIndexBigInteger).send();
        } catch (Exception e) {
            log.error("Failed initialize [chainDealId:{}, taskIndex:{}, error:{}]",
                    chainDealId, taskIndex, e.getMessage());
            return Optional.empty();
        }

        List<IexecHubABILegacy.TaskInitializeEventResponse> eventsList = getHubContract().getTaskInitializeEvents(receipt);
        if (eventsList.isEmpty()) {
            log.error("Failed to get initialise event [chainDealId:{}, taskIndex:{}]", chainDealId, taskIndex);
            return Optional.empty();
        }

        // if the status is still pending, check regularly for a status update
        if (eventsList.get(0).log != null &&
                eventsList.get(0).log.getType().equals(INITIALIZING_PENDING_STATUS) &&
                !checkPendingInitialization(chainDealId, taskIndexBigInteger)) {
            log.error("Failed to get initialise event [chainDealId:{}, taskIndex:{}]", chainDealId, taskIndex);
            return Optional.empty();
        }

        String chainTaskId = BytesUtils.bytesToString(eventsList.get(0).taskid);
        ChainReceipt chainReceipt = ChainUtils.buildChainReceipt(eventsList.get(0).log, chainTaskId);

        log.info("Initialized [chainTaskId:{}, chainDealId:{}, taskIndex:{}, gasUsed:{}]",
                chainTaskId, chainDealId, taskIndex, receipt.getGasUsed());

        return Optional.of(Pair.of(chainTaskId, chainReceipt));
    }

    private boolean checkPendingInitialization(String chainDealId, BigInteger taskIndexBigInteger) {

        String computedChainTaskId = ChainUtils.generateChainTaskId(chainDealId, taskIndexBigInteger);
        long maxWaitingTime = 2 * 60 * 1000L; // 2min

        // max waiting Time should be roughly the time of 10 blocks
        try {
            long latestBlockNumber = web3jService.getLatestBlockNumber();
            BigInteger latestBlockTimestamp = web3jService.getLatestBlock().getTimestamp();
            BigInteger tenBlocksAgoTimestamp = web3jService.getBlock(latestBlockNumber - 10).getTimestamp();

            maxWaitingTime = latestBlockTimestamp.longValue() - tenBlocksAgoTimestamp.longValue();

        } catch (IOException e) {
            e.printStackTrace();
        }

        final long startTime = System.currentTimeMillis();
        long duration = 0;
        while (duration < maxWaitingTime) {
            try {
                Optional<ChainTask> optionalChainTask = ChainUtils.getChainTask(iexecHub, computedChainTaskId);
                if (optionalChainTask.isPresent() && !optionalChainTask.get().getStatus().equals(ChainTaskStatus.UNSET)) {
                    return true;
                }
                Thread.sleep(500);
            } catch (InterruptedException e) {
                log.error("Error in checking the latest block number");
            }
            duration = System.currentTimeMillis() - startTime;
        }

        return false;
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

    public Optional<ChainReceipt> finalizeTask(String chainTaskId, String resultUri) {
        log.info("Requested  finalize [chainTaskId:{}, waitingTxCount:{}]", chainTaskId, getWaitingTransactionCount());
        try {
            return CompletableFuture.supplyAsync(() -> sendFinalizeTransaction(chainTaskId, resultUri), executor).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    private Optional<ChainReceipt> sendFinalizeTransaction(String chainTaskId, String resultUri) {
        byte[] chainTaskIdBytes = BytesUtils.stringToBytes(chainTaskId);
        byte[] resultUriBytes = resultUri.getBytes(StandardCharsets.UTF_8);

        TransactionReceipt receipt;
        try {
            receipt = getHubContract().finalize(chainTaskIdBytes, resultUriBytes).send();
        } catch (Exception e) {
            log.error("Failed finalize [chainTaskId:{}, resultUri:{}, error:{}]]", chainTaskId, resultUri, e.getMessage());
            return Optional.empty();
        }

        List<IexecHubABILegacy.TaskFinalizeEventResponse> eventsList = getHubContract().getTaskFinalizeEvents(receipt);
        if (eventsList.isEmpty()) {
            log.error("Failed to get finalize event [chainTaskId:{}]", chainTaskId);
            return Optional.empty();
        }

        log.info("Finalized [chainTaskId:{}, resultUri:{}, gasUsed:{}]", chainTaskId, resultUri, receipt.getGasUsed());
        ChainReceipt chainReceipt = ChainUtils.buildChainReceipt(eventsList.get(0).log, chainTaskId);

        return Optional.of(chainReceipt);
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

    public Optional<ChainReceipt> reOpen(String chainTaskId) {
        log.info("Requested  reopen [chainTaskId:{}, waitingTxCount:{}]", chainTaskId, getWaitingTransactionCount());
        try {
            return CompletableFuture.supplyAsync(() -> sendReopenTransaction(chainTaskId), executor).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    private Optional<ChainReceipt> sendReopenTransaction(String chainTaskId) {
        TransactionReceipt receipt;
        try {
            receipt = getHubContract().reopen(BytesUtils.stringToBytes(chainTaskId)).send();
        } catch (Exception e) {
            log.error("Failed reopen [chainTaskId:{}, error:{}]", chainTaskId, e.getMessage());
            return Optional.empty();
        }

        List<IexecHubABILegacy.TaskReopenEventResponse> eventsList = getHubContract().getTaskReopenEvents(receipt);
        if (eventsList.isEmpty()) {
            log.error("Failed to get reopen event [chainTaskId:{}]", chainTaskId);
            return Optional.empty();
        }

        log.info("Reopened [chainTaskId:{}, gasUsed:{}]", chainTaskId, receipt.getGasUsed());
        ChainReceipt chainReceipt = ChainUtils.buildChainReceipt(eventsList.get(0).log, chainTaskId);

        return Optional.of(chainReceipt);
    }

    private long getWaitingTransactionCount() {
        return executor.getTaskCount() - executor.getCompletedTaskCount();
    }

    public Optional<ChainDeal> getChainDeal(String chainDealId) {
        return ChainUtils.getChainDeal(credentials, web3j, getHubContract().getContractAddress(), chainDealId);
    }

    public Optional<ChainTask> getChainTask(String chainTaskId) {
        return ChainUtils.getChainTask(getHubContract(), chainTaskId);
    }

    Optional<ChainApp> getChainApp(String address) {
        App app = ChainUtils.getAppContract(credentials, web3j, address);
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
        return getClerkContract().schedulerNoticeEventObservable(fromBlock, toBlock).map(schedulerNotice -> {

            if (schedulerNotice.workerpool.equalsIgnoreCase(chainConfig.getPoolAddress())) {
                return Optional.of(new DealEvent(schedulerNotice));
            }
            return Optional.empty();
        });
    }

    public boolean hasEnoughGas() {
        return ChainUtils.hasEnoughGas(web3j, credentials.getAddress(), chainConfig.getGasPriceCap());
    }
}
