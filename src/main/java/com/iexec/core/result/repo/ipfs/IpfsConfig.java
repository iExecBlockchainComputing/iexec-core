package com.iexec.core.result.repo.ipfs;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class IpfsConfig {

    @Value("${ipfs.host}")
    private String host;

    @Value("${ipfs.port}")
    private String port;
}
