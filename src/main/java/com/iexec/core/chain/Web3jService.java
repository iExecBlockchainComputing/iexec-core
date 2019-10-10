package com.iexec.core.chain;

import com.iexec.common.chain.Web3jAbstractService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class Web3jService extends Web3jAbstractService {

    public Web3jService(ChainConfig chainConfig) {
        super(chainConfig.getPrivateChainAddress(), chainConfig.getGasPriceMultiplier(), chainConfig.getGasPriceCap(),
                chainConfig.isSidechain());
    }

}