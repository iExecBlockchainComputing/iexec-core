package com.iexec.core.chain;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.security.Signature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.HashUtils;
import com.iexec.core.configuration.SmsConfiguration;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Sign;


@Service
public class SignatureService {

    private CredentialsService credentialsService;
    private SmsConfiguration smsConfiguration;

    public SignatureService(CredentialsService credentialsService,
                            SmsConfiguration smsConfiguration) {
        this.credentialsService = credentialsService;
        this.smsConfiguration = smsConfiguration;
    }

    public ContributionAuthorization createAuthorization(String workerWallet, String chainTaskId, String enclaveChallenge) {
        String hash = HashUtils.concatenateAndHash(workerWallet, chainTaskId, enclaveChallenge);

        Sign.SignatureData sign = Sign.signPrefixedMessage(
                BytesUtils.stringToBytes(hash), credentialsService.getCredentials().getEcKeyPair());

        String smsIp = smsConfiguration.getSmsIp();

        return ContributionAuthorization.builder()
                .workerWallet(workerWallet)
                .chainTaskId(chainTaskId)
                .enclaveChallenge(enclaveChallenge)
                .signature(new Signature(sign))
                .smsIp(smsIp)
                .build();
    }
}
