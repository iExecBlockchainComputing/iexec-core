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
public class TxManagerConfig {

    @Value("${txManager.maxRetry}")
    private int maxRetry;

    @Value("${txManager.retryDelay}")
    private int retryDelay;
}
