package com.iexec.core.chain;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.security.Signature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.core.configuration.SmsConfiguration;
import com.iexec.core.feign.SafeFeignClient;
import org.bouncycastle.util.Arrays;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import static com.iexec.common.utils.BytesUtils.EMPTY_ADDRESS;

import java.util.Optional;


@Service
public class SignatureService {

    private CredentialsService credentialsService;
    private SafeFeignClient safeFeignClient;
    private SmsConfiguration smsConfiguration;

    public SignatureService(CredentialsService credentialsService,
                            SafeFeignClient safeFeignClient,
                            SmsConfiguration smsConfiguration) {
        this.credentialsService = credentialsService;
        this.safeFeignClient = safeFeignClient;
        this.smsConfiguration = smsConfiguration;
    }

    public Optional<ContributionAuthorization> createAuthorization(String workerWallet, String chainTaskId, boolean isTrustedExecution) {
        String enclaveChallenge = EMPTY_ADDRESS;
        String smsIp = "";

        if (isTrustedExecution) {
            String smsEnclaveChallenge = this.safeFeignClient.generateSmsAttestation(chainTaskId);

            if (smsEnclaveChallenge.isEmpty()) {
                return Optional.empty();
            }

            enclaveChallenge = smsEnclaveChallenge;
            smsIp = smsConfiguration.getSmsIp();
        }

        String hash = computeAuthorizationHash(workerWallet, chainTaskId, enclaveChallenge);

        Sign.SignatureData sign = Sign.signPrefixedMessage(
                BytesUtils.stringToBytes(hash), credentialsService.getCredentials().getEcKeyPair());

        ContributionAuthorization contributionAuth = ContributionAuthorization.builder()
                .workerWallet(workerWallet)
                .chainTaskId(chainTaskId)
                .enclaveChallenge(enclaveChallenge)
                .signature(new Signature(sign))
                .smsIp(smsIp)
                .build();

        return Optional.of(contributionAuth);
    }

    String computeAuthorizationHash(String workerWallet, String chainTaskId, String enclaveAddress) {

        // concatenate 3 byte[] fields
        byte[] res = Arrays.concatenate(
                BytesUtils.stringToBytes(workerWallet),
                BytesUtils.stringToBytes(chainTaskId),
                BytesUtils.stringToBytes(enclaveAddress));

        // Hash the result and convert to String
        return Numeric.toHexString(Hash.sha3(res));
    }
}
