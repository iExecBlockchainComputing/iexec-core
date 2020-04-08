package com.iexec.core.chain;

import com.iexec.common.chain.*;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.common.utils.BytesUtils;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static com.iexec.common.chain.ChainTaskStatus.ACTIVE;
import static com.iexec.common.chain.ChainTaskStatus.COMPLETED;
import static com.iexec.common.utils.BytesUtils.stringToBytes;
import static com.iexec.common.utils.DateTimeUtils.now;

@Slf4j
@Service
public class IexecHubService extends IexecHubAbstractService {

    private final ThreadPoolExecutor executor;
    private final CredentialsService credentialsService;
    private final Web3jService web3jService;
    private final String poolAddress;

    @Autowired
    public IexecHubService(CredentialsService credentialsService,
                           Web3jService web3jService,
                           ChainConfig chainConfig) {
        super(credentialsService.getCredentials(), web3jService, chainConfig.getHubAddress());
        this.credentialsService = credentialsService;
        this.web3jService = web3jService;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        this.poolAddress = chainConfig.getPoolAddress();
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
        byte[] chainDealIdBytes = stringToBytes(chainDealId);
        BigInteger taskIndexBigInteger = BigInteger.valueOf(taskIndex);
        String computedChainTaskId = ChainUtils.generateChainTaskId(chainDealId, taskIndexBigInteger);


        RemoteCall<TransactionReceipt> initializeCall = getHubContract(web3jService.getWritingContractGasProvider())
                .initialize(chainDealIdBytes, taskIndexBigInteger);


        TransactionReceipt initializeReceipt;
        try {
            initializeReceipt = initializeCall.send();
        } catch (Exception e) {
            log.error("Failed to send initialize [chainDealId:{}, taskIndex:{}, exception:{}]",
                    chainDealId, taskIndex, e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }

        List<IexecHubABILegacy.TaskInitializeEventResponse> initializeEvents = getHubContract().getTaskInitializeEvents(initializeReceipt);

        IexecHubABILegacy.TaskInitializeEventResponse initializeEvent = null;
        if (initializeEvents != null && !initializeEvents.isEmpty()) {
            initializeEvent = initializeEvents.get(0);
        }

        if (isSuccessTx(computedChainTaskId, initializeEvent, ACTIVE)) {
            String chainTaskId = BytesUtils.bytesToString(initializeEvent.taskid);

            ChainReceipt chainReceipt = ChainUtils.buildChainReceipt(initializeEvent.log, chainTaskId, web3jService.getLatestBlockNumber());

            log.info("Initialized [chainTaskId:{}, chainDealId:{}, taskIndex:{}, gasUsed:{}]",
                    computedChainTaskId, chainDealId, taskIndex, initializeReceipt.getGasUsed());
            return Optional.of(Pair.of(chainTaskId, chainReceipt));
        }

        log.error("Failed to initialize [chainDealId:{}, taskIndex:{}]", chainDealId, taskIndex);
        return Optional.empty();
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

    public Optional<ChainReceipt> finalizeTask(String chainTaskId, String resultUri, String callbackData) {
        log.info("Requested  finalize [chainTaskId:{}, waitingTxCount:{}]", chainTaskId, getWaitingTransactionCount());
        try {
            return CompletableFuture.supplyAsync(() -> sendFinalizeTransaction(chainTaskId, resultUri, callbackData), executor).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    private Optional<ChainReceipt> sendFinalizeTransaction(String chainTaskId, String resultUri, String callbackData) {
        byte[] chainTaskIdBytes = stringToBytes(chainTaskId);
        byte[] finalizePayload = resultUri.getBytes(StandardCharsets.UTF_8);
        boolean shouldSendCallback = callbackData != null && !callbackData.isEmpty();
        if (shouldSendCallback) {
            finalizePayload = stringToBytes(callbackData);
        }

        TransactionReceipt finalizeReceipt;
        //TODO: Upgrade smart-contracts for sending both resultUri and callbackData
        RemoteCall<TransactionReceipt> finalizeCall = getHubContract(web3jService.getWritingContractGasProvider()).finalize(chainTaskIdBytes, finalizePayload);

        try {
            finalizeReceipt = finalizeCall.send();
        } catch (Exception e) {
            log.error("Failed to send finalize [chainTaskId:{}, resultLink:{}, callbackData:{}, shouldSendCallback:{}, error:{}]]",
                    chainTaskId, resultUri, callbackData, shouldSendCallback, e.getMessage());
            return Optional.empty();
        }

        List<IexecHubABILegacy.TaskFinalizeEventResponse> finalizeEvents = getHubContract().getTaskFinalizeEvents(finalizeReceipt);

        IexecHubABILegacy.TaskFinalizeEventResponse finalizeEvent = null;
        if (finalizeEvents != null && !finalizeEvents.isEmpty()) {
            finalizeEvent = finalizeEvents.get(0);
        }

        if (isSuccessTx(chainTaskId, finalizeEvent, COMPLETED)) {
            ChainReceipt chainReceipt = ChainUtils.buildChainReceipt(finalizeEvents.get(0).log, chainTaskId, web3jService.getLatestBlockNumber());

            log.info("Finalized [chainTaskId:{}, resultLink:{}, callbackData:{}, shouldSendCallback:{}, gasUsed:{}]", chainTaskId,
                    resultUri, callbackData, shouldSendCallback, finalizeReceipt.getGasUsed());
            return Optional.of(chainReceipt);
        }

        log.error("Failed to finalize [chainTaskId:{}]", chainTaskId);
        return Optional.empty();
    }

    private boolean isSuccessTx(String chainTaskId, BaseEventResponse txEvent, ChainTaskStatus pretendedStatus) {
        if (txEvent == null || txEvent.log == null) {
            return false;
        }

        if (txEvent.log.getType() == null || txEvent.log.getType().equals(PENDING_RECEIPT_STATUS)) {
            return isStatusValidOnChainAfterPendingReceipt(chainTaskId, pretendedStatus, this::isTaskStatusValidOnChain);
        }

        return true;
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
            receipt = getHubContract(web3jService.getWritingContractGasProvider()).reopen(stringToBytes(chainTaskId)).send();
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
        ChainReceipt chainReceipt = ChainUtils.buildChainReceipt(eventsList.get(0).log, chainTaskId, web3jService.getLatestBlockNumber());

        return Optional.of(chainReceipt);
    }

    private long getWaitingTransactionCount() {
        return executor.getTaskCount() - executor.getCompletedTaskCount();
    }


    Flowable<Optional<DealEvent>> getDealEventObservableToLatest(BigInteger from) {
        return getDealEventObservable(from, null);
    }

    Flowable<Optional<DealEvent>> getDealEventObservable(BigInteger from, BigInteger to) {
        DefaultBlockParameter fromBlock = DefaultBlockParameter.valueOf(from);
        DefaultBlockParameter toBlock = DefaultBlockParameterName.LATEST;
        if (to != null) {
            toBlock = DefaultBlockParameter.valueOf(to);
        }
        return getClerkContract().schedulerNoticeEventFlowable(fromBlock, toBlock).map(schedulerNotice -> {

            if (schedulerNotice.workerpool.equalsIgnoreCase(poolAddress)) {
                return Optional.of(new DealEvent(schedulerNotice));
            }
            return Optional.empty();
        });
    }

    public boolean hasEnoughGas() {
        return hasEnoughGas(credentialsService.getCredentials().getAddress());
    }

    private Boolean isTaskStatusValidOnChain(String chainTaskId, ChainStatus chainTaskStatus) {
        if (chainTaskStatus instanceof ChainTaskStatus) {
            Optional<ChainTask> optionalChainTask = getChainTask(chainTaskId);
            return optionalChainTask.isPresent() && optionalChainTask.get().getStatus().equals(chainTaskStatus);
        }
        return false;
    }


}
