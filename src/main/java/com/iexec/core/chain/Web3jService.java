package com.iexec.core.chain;

import com.iexec.common.chain.ChainUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;

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

}