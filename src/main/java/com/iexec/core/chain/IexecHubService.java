/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.core.chain;

import com.iexec.common.chain.*;
import com.iexec.common.contract.generated.IexecHubContract;
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
        super(
                credentialsService.getCredentials(),
                web3jService,
                chainConfig.getHubAddress());
        this.credentialsService = credentialsService;
        this.web3jService = web3jService;
        this.executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(1);
        this.poolAddress = chainConfig.getPoolAddress();
        if (!hasEnoughGas()) {
            System.exit(0);
        }
    }

    /**
     * Check if a deal's task can be initialized.
     * An initializable task should have the status
     * UNSET onchain and have a contribution deadline
     * that is still in the future.
     * 
     * @param chainDealId
     * @param taskIndex
     * @return true if the task is initializable, false otherwise.
     */
    public boolean isInitializableOnchain(String chainDealId, int taskIndex) {
        boolean isTaskUnsetOnChain = isTaskInUnsetStatusOnChain(chainDealId, taskIndex);
        boolean isBeforeContributionDeadline = isBeforeContributionDeadline(chainDealId);
        return isBeforeContributionDeadline && isTaskUnsetOnChain;
    }

    /**
     * Check if the task is defined onchain and
     * has the status {@link ChainTaskStatus#UNSET}.
     * 
     * @param chainDealId
     * @param taskIndex
     * @return true if the task is found with the status UNSET, false otherwise.
     */
    //TODO Migrate to common
    public boolean isTaskInUnsetStatusOnChain(String chainDealId, int taskIndex) {
        String generatedChainTaskId = ChainUtils.generateChainTaskId(chainDealId, taskIndex);
        Optional<ChainTask> chainTask = getChainTask(generatedChainTaskId);
        return chainTask.isEmpty()
                || ChainTaskStatus.UNSET.equals(chainTask.get().getStatus());
    }

    /**
     * Check if a deal's contribution deadline
     * is still not reached.
     * 
     * @param chainDealId
     * @return true if deadline is not reached, false otherwise.
     */
    public boolean isBeforeContributionDeadline(String chainDealId) {
        return getChainDeal(chainDealId)
                .map(this::isBeforeContributionDeadline)
                .orElse(false);
    }

    /**
     * Check if a deal's contribution deadline
     * is still not reached.
     * 
     * @param chainDeal
     * @return true if deadline is not reached, false otherwise.
     */
    public boolean isBeforeContributionDeadline(ChainDeal chainDeal) {
        return getChainDealContributionDeadline(chainDeal)
                .after(new Date());
    }

    /**
     * <p> Get deal's contribution deadline date. The deadline
     * is calculated as follow:
     * start + maxCategoryTime * maxNbOfPeriods.
     * 
     * <ul>
     * <li> start: the start time of the deal.
     * <li> maxCategoryTime: duration of the deal's category.
     * <li> nbOfCategoryUnits: number of category units dedicated
     *      for the contribution phase.
     * 
     * @param chainDeal
     * @return
     */
    public Date getChainDealContributionDeadline(ChainDeal chainDeal) {
        long startTime = chainDeal.getStartTime().longValue() * 1000;
        long maxTime = chainDeal.getChainCategory().getMaxExecutionTime();
        long maxNbOfPeriods = getMaxNbOfPeriodsForConsensus();
        maxNbOfPeriods = (maxNbOfPeriods == -1) ? 10 : maxNbOfPeriods;
        return new Date(startTime + maxTime * maxNbOfPeriods);
    }

    /**
     * <p> Get deal's final deadline date. The deadline
     * is calculated as follow:
     * start + maxCategoryTime * 10.
     * 
     * <ul>
     * <li> start: the start time of the deal.
     * <li> maxCategoryTime: duration of the deal's category.
     * <li> 10: number of category units dedicated
     *      for the hole execution.
     * 
     * @param chainDeal
     * @return
     */
    public Date getChainDealFinalDeadline(ChainDeal chainDeal) {
        long startTime = chainDeal.getStartTime().longValue() * 1000;
        long maxTime = chainDeal.getChainCategory().getMaxExecutionTime();
        return new Date(startTime + maxTime * 10);
    }

    @Deprecated
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
        String computedChainTaskId = ChainUtils.generateChainTaskId(chainDealId, taskIndex);
        RemoteCall<TransactionReceipt> initializeCall =
                getHubContract(web3jService.getWritingContractGasProvider())
                .initialize(chainDealIdBytes, BigInteger.valueOf(taskIndex));

        TransactionReceipt initializeReceipt;
        try {
            initializeReceipt = initializeCall.send();
        } catch (Exception e) {
            log.error("Failed to send initialize [chainDealId:{}, taskIndex:{}, exception:{}]",
                    chainDealId, taskIndex, e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }

        List<IexecHubContract.TaskInitializeEventResponse> initializeEvents = getHubContract().getTaskInitializeEvents(initializeReceipt);

        IexecHubContract.TaskInitializeEventResponse initializeEvent = null;
        if (initializeEvents != null && !initializeEvents.isEmpty()) {
            initializeEvent = initializeEvents.get(0);
        }

        if (isSuccessTx(computedChainTaskId, initializeEvent, ACTIVE)) {
            String chainTaskId = BytesUtils.bytesToString(initializeEvent.taskid);
            ChainReceipt chainReceipt = buildChainReceipt(initializeReceipt);
            log.info("Initialized [chainTaskId:{}, chainDealId:{}, taskIndex:{}, " +
                            "gasUsed:{}, block:{}]",
                    computedChainTaskId, chainDealId, taskIndex,
                    initializeReceipt.getGasUsed(), chainReceipt.getBlockNumber());
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

    public Optional<ChainReceipt> finalizeTask(String chainTaskId, String resultLink, String callbackData) {
        log.info("Requested  finalize [chainTaskId:{}, waitingTxCount:{}]", chainTaskId, getWaitingTransactionCount());
        try {
            return CompletableFuture.supplyAsync(() -> sendFinalizeTransaction(chainTaskId, resultLink, callbackData), executor).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return Optional.empty();
    }

    private Optional<ChainReceipt> sendFinalizeTransaction(String chainTaskId, String resultLink, String callbackData) {
        byte[] chainTaskIdBytes = stringToBytes(chainTaskId);
        byte[] results = new byte[0];
        byte[] resultsCallback = new byte[0];

        boolean shouldSendCallback = callbackData != null && !callbackData.isEmpty();
        if (!shouldSendCallback) {
            results = resultLink.getBytes(StandardCharsets.UTF_8);
        } else {
            resultsCallback = stringToBytes(callbackData);
        }

        TransactionReceipt finalizeReceipt;

        RemoteCall<TransactionReceipt> finalizeCall = getHubContract(web3jService.getWritingContractGasProvider())
                .finalize(chainTaskIdBytes, results, resultsCallback);

        try {
            finalizeReceipt = finalizeCall.send();
        } catch (Exception e) {
            log.error("Failed to send finalize [chainTaskId:{}, resultLink:{}, callbackData:{}, shouldSendCallback:{}, error:{}]]",
                    chainTaskId, resultLink, callbackData, shouldSendCallback, e.getMessage());
            return Optional.empty();
        }

        List<IexecHubContract.TaskFinalizeEventResponse> finalizeEvents = getHubContract().getTaskFinalizeEvents(finalizeReceipt);

        IexecHubContract.TaskFinalizeEventResponse finalizeEvent = null;
        if (finalizeEvents != null && !finalizeEvents.isEmpty()) {
            finalizeEvent = finalizeEvents.get(0);
        }

        if (isSuccessTx(chainTaskId, finalizeEvent, COMPLETED)) {
            ChainReceipt chainReceipt = buildChainReceipt(finalizeReceipt);
            log.info("Finalized [chainTaskId:{}, resultLink:{}, callbackData:{}, " +
                            "shouldSendCallback:{}, gasUsed:{}, block:{}]",
                    chainTaskId, resultLink, callbackData, shouldSendCallback,
                    finalizeReceipt.getGasUsed(), chainReceipt.getBlockNumber());
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

        List<IexecHubContract.TaskReopenEventResponse> eventsList = getHubContract().getTaskReopenEvents(receipt);
        if (eventsList.isEmpty()) {
            log.error("Failed to get reopen event [chainTaskId:{}]", chainTaskId);
            return Optional.empty();
        }

        ChainReceipt chainReceipt = buildChainReceipt(receipt);
        log.info("Reopened [chainTaskId:{}, gasUsed:{}, block:{}]",
                chainTaskId, receipt.getGasUsed(), chainReceipt.getBlockNumber());
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
        return getHubContract().schedulerNoticeEventFlowable(fromBlock, toBlock).map(schedulerNotice -> {

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

    private ChainReceipt buildChainReceipt(TransactionReceipt receipt) {
        return ChainReceipt.builder()
                .txHash(receipt.getTransactionHash())
                .blockNumber(receipt.getBlockNumber() != null?
                        receipt.getBlockNumber().longValue() : 0)
                .build();
    }

}
