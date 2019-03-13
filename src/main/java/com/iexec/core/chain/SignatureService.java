package com.iexec.core.chain;

import com.iexec.common.chain.ContributionAuthorization;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.SignatureUtils;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import static com.iexec.common.utils.BytesUtils.EMPTY_ADDRESS;

@Slf4j
@Service
public class SignatureService {

    @Value("${tee.enclaveChallenge}")
    private String enclaveChallenge;

    private CredentialsService credentialsService;

    public SignatureService(CredentialsService credentialsService) {
        this.credentialsService = credentialsService;
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

    public ContributionAuthorization createAuthorization(String workerWallet, String chainTaskId, boolean isTrustedExecution) {
        String enclaveAddress = getEnclaveAddress(isTrustedExecution);

        String hash = computeAuthorizationHash(workerWallet, chainTaskId, enclaveAddress);

        Sign.SignatureData sign = Sign.signPrefixedMessage(
                BytesUtils.stringToBytes(hash), credentialsService.getCredentials().getEcKeyPair());

        return ContributionAuthorization.builder()
                .workerWallet(workerWallet)
                .chainTaskId(chainTaskId)
                .enclave(enclaveAddress)
                .signR(sign.getR())
                .signS(sign.getS())
                .signV(sign.getV())
                .build();
    }

    private String getEnclaveAddress(boolean isTrustedExecution) {
        String enclaveAddress = EMPTY_ADDRESS;

        if (isTrustedExecution){
            enclaveAddress = this.enclaveChallenge;
        }
        return enclaveAddress;
    }
}
