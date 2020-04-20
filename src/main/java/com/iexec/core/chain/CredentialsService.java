package com.iexec.core.chain;

import com.iexec.common.chain.CredentialsAbstractService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CredentialsService extends CredentialsAbstractService {

    public CredentialsService(
            @Value("${wallet.password}") String walletPassword,
            @Value("${wallet.encryptedFilePath}") String walletPath
    ) throws Exception {
        super(walletPassword, walletPath);
    }
}
