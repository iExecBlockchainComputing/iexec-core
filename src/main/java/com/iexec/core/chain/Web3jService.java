package com.iexec.core.chain;

import com.iexec.common.chain.ChainUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;

import java.io.IOException;

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

    private long getLatestBlockNumber() throws IOException {
        return web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send().getBlock().getNumber().longValue();
    }

    // check that the blockNumber is already available for the scheduler
    // blockNumber is different than 0 only for status the require a check on the blockchain, so the scheduler should
    // already have this block, otherwise it should wait for it until maxWaitingTime is reached (2 minutes)
    public boolean isBlockNumberAvailable(long blockNumber) {
        if (blockNumber == 0) {
            return true;
        }

        long maxWaitingTime = 2 * 60 * 1000;
        final long startTime = System.currentTimeMillis();
        long duration = 0;
        boolean blockNumberReached = false;
        while (!blockNumberReached && duration < maxWaitingTime) {
            try {
                long currentBlock = getLatestBlockNumber();
                if (blockNumber < currentBlock) {
                    blockNumberReached = true;
                } else {
                    Thread.sleep(500);
                }
            } catch (IOException | InterruptedException e) {
                log.error("Error in checking the latest block number");
            }
            duration = System.currentTimeMillis() - startTime;
        }

        return blockNumberReached;
    }

}