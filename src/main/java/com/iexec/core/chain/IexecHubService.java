/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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

import com.iexec.common.lifecycle.purge.Purgeable;
import com.iexec.commons.poco.chain.*;
import com.iexec.commons.poco.contract.generated.IexecHubContract;
import com.iexec.commons.poco.encoding.LogTopic;
import io.reactivex.Flowable;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;

import static com.iexec.commons.poco.chain.ChainContributionStatus.CONTRIBUTED;
import static com.iexec.commons.poco.chain.ChainContributionStatus.REVEALED;

@Slf4j
@Service
public class IexecHubService extends IexecHubAbstractService implements Purgeable {

    private final SignerService signerService;
    private final Web3jService web3jService;

    public IexecHubService(final SignerService signerService,
                           final Web3jService web3jService,
                           final ChainConfig chainConfig) {
        super(
                signerService.getCredentials(),
                web3jService,
                chainConfig.getHubAddress());
        this.signerService = signerService;
        this.web3jService = web3jService;
        if (!hasEnoughGas()) {
            System.exit(0);
        }
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
     * </ul>
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
     * </ul>
     *
     * @param chainDeal
     * @return
     */
    public Date getChainDealFinalDeadline(ChainDeal chainDeal) {
        long startTime = chainDeal.getStartTime().longValue() * 1000;
        long maxTime = chainDeal.getChainCategory().getMaxExecutionTime();
        return new Date(startTime + maxTime * 10);
    }

    Flowable<IexecHubContract.SchedulerNoticeEventResponse> getDealEventObservable(EthFilter filter) {
        return iexecHubContract.schedulerNoticeEventFlowable(filter);
    }

    public boolean hasEnoughGas() {
        final boolean hasEnoughGas = hasEnoughGas(signerService.getAddress());
        log.debug("Gas status [hasEnoughGas:{}]", hasEnoughGas);
        return hasEnoughGas;
    }

    // region check contribution status
    public boolean repeatIsContributedTrue(String chainTaskId, String walletAddress) {
        return web3jService.repeatCheck(NB_BLOCKS_TO_WAIT_PER_RETRY, MAX_RETRIES,
                "isContributedTrue", this::isContributed, chainTaskId, walletAddress);
    }

    public boolean repeatIsRevealedTrue(String chainTaskId, String walletAddress) {
        return web3jService.repeatCheck(NB_BLOCKS_TO_WAIT_PER_RETRY, MAX_RETRIES,
                "isRevealedTrue", this::isRevealed, chainTaskId, walletAddress);
    }

    public boolean isContributed(String... args) {
        return getChainContribution(args[0], args[1])
                .map(ChainContribution::getStatus)
                .filter(chainStatus -> chainStatus == CONTRIBUTED || chainStatus == REVEALED)
                .isPresent();
    }

    public boolean isRevealed(String... args) {
        return getChainContribution(args[0], args[1])
                .map(ChainContribution::getStatus)
                .filter(chainStatus -> chainStatus == REVEALED)
                .isPresent();
    }
    // endregion

    // region get event blocks
    public ChainReceipt getInitializeBlock(final String chainTaskId,
                                           final long fromBlock) {
        log.debug("getInitializeBlock [chainTaskId:{}]", chainTaskId);
        final long latestBlock = web3jService.getLatestBlockNumber();
        if (fromBlock > latestBlock) {
            return ChainReceipt.builder().build();
        }
        final EthFilter ethFilter = createEthFilter(
                fromBlock, latestBlock, LogTopic.TASK_INITIALIZE_EVENT, chainTaskId);
        return web3jService.getWeb3j().ethGetLogs(ethFilter).flowable()
                .map(this::createChainReceipt)
                .blockingFirst();
    }

    public ChainReceipt getContributionBlock(final String chainTaskId,
                                             final String workerWallet,
                                             final long fromBlock) {
        log.debug("getContributionBlock [chainTaskId:{}]", chainTaskId);
        long latestBlock = web3jService.getLatestBlockNumber();
        if (fromBlock > latestBlock) {
            return ChainReceipt.builder().build();
        }
        // wallet needs to be encoded on 32 bytes instead of 20 bytes
        final String filterEncodedWallet = Numeric.toHexStringWithPrefixZeroPadded(Numeric.toBigInt(workerWallet), 64);
        final EthFilter ethFilter = createEthFilter(
                fromBlock, latestBlock, LogTopic.TASK_CONTRIBUTE_EVENT, chainTaskId, filterEncodedWallet);
        return web3jService.getWeb3j().ethGetLogs(ethFilter).flowable()
                .map(this::createChainReceipt)
                .blockingFirst();
    }

    public ChainReceipt getConsensusBlock(final String chainTaskId, final long fromBlock) {
        log.debug("getConsensusBlock [chainTaskId:{}]", chainTaskId);
        long latestBlock = web3jService.getLatestBlockNumber();
        if (fromBlock > latestBlock) {
            return ChainReceipt.builder().build();
        }
        final EthFilter ethFilter = createEthFilter(
                fromBlock, latestBlock, LogTopic.TASK_CONSENSUS_EVENT, chainTaskId);
        return web3jService.getWeb3j().ethGetLogs(ethFilter).flowable()
                .map(this::createChainReceipt)
                .blockingFirst();
    }

    public ChainReceipt getRevealBlock(final String chainTaskId,
                                       final String workerWallet,
                                       final long fromBlock) {
        log.debug("getRevealBlock [chainTaskId:{}]", chainTaskId);
        long latestBlock = web3jService.getLatestBlockNumber();
        if (fromBlock > latestBlock) {
            return ChainReceipt.builder().build();
        }
        // wallet needs to be encoded on 32 bytes instead of 20 bytes
        final String filterEncodedWallet = Numeric.toHexStringWithPrefixZeroPadded(Numeric.toBigInt(workerWallet), 64);
        final EthFilter ethFilter = createEthFilter(
                fromBlock, latestBlock, LogTopic.TASK_REVEAL_EVENT, chainTaskId, filterEncodedWallet);
        return web3jService.getWeb3j().ethGetLogs(ethFilter).flowable()
                .map(this::createChainReceipt)
                .blockingFirst();
    }

    public ChainReceipt getFinalizeBlock(final String chainTaskId,
                                         final long fromBlock) {
        log.debug("getFinalizeBlock [chainTaskId:{}]", chainTaskId);
        long latestBlock = web3jService.getLatestBlockNumber();
        if (fromBlock > latestBlock) {
            return ChainReceipt.builder().build();
        }
        final EthFilter ethFilter = createEthFilter(
                fromBlock, latestBlock, LogTopic.TASK_FINALIZE_EVENT, chainTaskId);
        return web3jService.getWeb3j().ethGetLogs(ethFilter).flowable()
                .map(this::createChainReceipt)
                .blockingFirst();
    }

    private ChainReceipt createChainReceipt(final EthLog ethLog) {
        final Log logEvent = (Log) ethLog.getLogs().get(0);
        return ChainReceipt.builder()
                .blockNumber(logEvent.getBlockNumber().longValue())
                .txHash(logEvent.getTransactionHash())
                .build();
    }

    private EthFilter createEthFilter(final long fromBlock,
                                      final long toBlock,
                                      final String... topics) {
        log.debug("createEthFilter [from:{}, to:{}]", fromBlock, toBlock);
        final EthFilter ethFilter = new EthFilter(
                DefaultBlockParameter.valueOf(BigInteger.valueOf(fromBlock)),
                DefaultBlockParameter.valueOf(BigInteger.valueOf(toBlock)),
                iexecHubAddress
        );
        Arrays.stream(topics).forEach(ethFilter::addSingleTopic);

        return ethFilter;
    }
    // endregion

    @Override
    public boolean purgeTask(final String chainTaskId) {
        log.debug("purgeTask [chainTaskId: {}]", chainTaskId);
        return super.purgeTask(chainTaskId);
    }

    @Override
    @PreDestroy
    public void purgeAllTasksData() {
        log.info("Method purgeAllTasksData() called to perform task data cleanup.");
        super.purgeAllTasksData();
    }
}
