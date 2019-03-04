package com.iexec.core.result.IPFS;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class IPFSConfig {

    @Value("${ipfs.host}")
    private String host;

    @Value("${ipfs.port}")
    private String port;
}
