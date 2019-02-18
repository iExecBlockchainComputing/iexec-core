package com.iexec.core.chain;

import com.iexec.common.chain.ChainUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlock;

import java.io.IOException;
import java.math.BigInteger;

@Slf4j
@Service
public class Web3jService {

    private final Web3j web3j;

    public Web3jService(ChainConfig chainConfig) {
        this.web3j = ChainUtils.getWeb3j(chainConfig.getPrivateChainAddress());
    }

    Web3j getWeb3j() {
        return web3j;
    }

    EthBlock.Block getLatestBlock() throws IOException {
        return web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock();
    }

    long getLatestBlockNumber() throws IOException {
        return getLatestBlock().getNumber().longValue();
    }

    EthBlock.Block getBlock(long blockNumber) throws IOException {
        return web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(BigInteger.valueOf(blockNumber)),
                false).send().getBlock();
    }

    // check if the blockNumber is already available for the scheduler
    // blockNumber is different than 0 only for status the require a check on the blockchain, so the scheduler should
    // already have this block, otherwise it should wait for a maximum of 10 blocks.
    public boolean isBlockAvailable(long blockNumber) {
        try {
            long maxBlockNumber = blockNumber + 10;
            long currentBlockNumber = getLatestBlockNumber();
            while (currentBlockNumber <= maxBlockNumber) {
                if (blockNumber <= currentBlockNumber) {
                    return true;
                } else {
                    log.warn("Chain is NOT synchronized yet [blockNumber:{}, currentBlockNumber:{}]", blockNumber, currentBlockNumber);
                    Thread.sleep(500);
                }
                currentBlockNumber = getLatestBlockNumber();
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error in checking the latest block number [execption:{}]", e.getMessage());
        }
        return false;
    }
}