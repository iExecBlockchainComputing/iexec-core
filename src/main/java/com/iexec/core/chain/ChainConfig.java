package com.iexec.core.chain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ChainConfig {

    @Value("${chain.id}")
    private Integer chainId;

    @Value("${chain.privateAddress}")
    private String privateChainAddress;

    @Value("${chain.publicAddress}")
    private String publicChainAddress;

    @Value("${chain.hubAddress}")
    private String hubAddress;

    @Value("${chain.poolAddress}")
    private String poolAddress;

    @Value("${chain.startBlockNumber}")
    private long startBlockNumber;

    @Value("${chain.gasPriceMultiplier}")
    private float gasPriceMultiplier;

    @Value("${chain.gasPriceCap}")
    private long gasPriceCap;

}
