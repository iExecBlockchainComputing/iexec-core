/*
 * Copyright 2020-2023 IEXEC BLOCKCHAIN TECH
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

import com.iexec.commons.poco.chain.*;
import com.iexec.commons.poco.contract.generated.IexecHubContract;
import io.reactivex.Flowable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import static com.iexec.common.utils.DateTimeUtils.now;
import static com.iexec.commons.poco.utils.BytesUtils.stringToBytes;

@Slf4j
@Service
public class IexecHubService extends IexecHubAbstractService {

    private final ThreadPoolExecutor executor;
    private final CredentialsService credentialsService;
    private final Web3jService web3jService;

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
        if (!hasEnoughGas()) {
            System.exit(0);
        }
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


    public boolean isTaskInCompletedStatusOnChain(String chainTaskId) {
        return getChainTask(chainTaskId)
                .filter(chainTask -> ChainTaskStatus.COMPLETED == chainTask.getStatus())
                .isPresent();
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

    public boolean canFinalize(String chainTaskId) {
        Optional<ChainTask> optional = getChainTask(chainTaskId);
        if (optional.isEmpty()) {
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

    public boolean canReopen(String chainTaskId) {
        Optional<ChainTask> optional = getChainTask(chainTaskId);
        if (optional.isEmpty()) {
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
        log.info("Requested reopen [chainTaskId:{}, waitingTxCount:{}]", chainTaskId, getWaitingTransactionCount());
        try {
            return CompletableFuture.supplyAsync(() -> sendReopenTransaction(chainTaskId), executor).get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("reOpen asynchronous execution did not complete", e);
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

    Flowable<IexecHubContract.SchedulerNoticeEventResponse> getDealEventObservable(EthFilter filter) {
        return getHubContract().schedulerNoticeEventFlowable(filter);
    }

    public boolean hasEnoughGas() {
        return hasEnoughGas(credentialsService.getCredentials().getAddress());
    }

    private ChainReceipt buildChainReceipt(TransactionReceipt receipt) {
        return ChainReceipt.builder()
                .txHash(receipt.getTransactionHash())
                .blockNumber(receipt.getBlockNumber() != null ?
                        receipt.getBlockNumber().longValue() : 0)
                .build();
    }

}
