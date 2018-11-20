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

    @Value("${chain.privateAddress}")
    private String privateChainAddress;

    @Value("${chain.publicAddress}")
    private String publicChainAddress;

    @Value("${chain.hubAddress}")
    private String hubAddress;

    @Value("${chain.poolAddress}")
    private String poolAddress;

}
