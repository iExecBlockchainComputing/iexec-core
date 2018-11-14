package com.iexec.core.chain;

import com.iexec.common.utils.BytesUtils;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.Arrays;
import org.springframework.stereotype.Service;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

@Slf4j
@Service
public class SignatureService {

    private CredentialsService credentialsService;

    public SignatureService(CredentialsService credentialsService) {
        this.credentialsService = credentialsService;
    }

    public String computeAuthorizationHash(String workerWallet, byte[] taskId, String enclaveAddress) {

        // concatenate 3 byte[] fields
        byte[] res = Arrays.concatenate(
                BytesUtils.stringToBytes(workerWallet),
                taskId,
                BytesUtils.stringToBytes(enclaveAddress));

        // Hash the result and convert to String
        return Numeric.toHexString(Hash.sha3(res));
    }

    public Sign.SignatureData signMessage(byte[] data) {
        return signPrefixedMessage(data, credentialsService.getCredentials().getEcKeyPair());
    }

    //Note to dev: The following block is copied/pasted from web3j 4.0-beta at this commit:
    // https://github.com/web3j/web3j/commit/4997746e566faaf9c88defad78af54ede24db65b
    // Once we update to web3j 4.0, the signPrefixedMessage method should be directly available and this block should
    // be deleted

    //!!!!!!!!!!! Beginning block web3j 4.0-beta !!!!!!!!!!!
    private String messagePrefix = "\u0019Ethereum Signed Message:\n";

    private byte[] getEthereumMessagePrefix(int messageLength) {
        return messagePrefix.concat(String.valueOf(messageLength)).getBytes();
    }

    private byte[] getEthereumMessageHash(byte[] message) {
        byte[] prefix = getEthereumMessagePrefix(message.length);
        byte[] result = new byte[prefix.length + message.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(message, 0, result, prefix.length, message.length);
        return Hash.sha3(result);
    }

    private Sign.SignatureData signPrefixedMessage(byte[] message, ECKeyPair keyPair) {
        return Sign.signMessage(getEthereumMessageHash(message), keyPair, false);
    }
    // !!!!!!!!!!! End block web3j 4.0-beta !!!!!!!!!!!

}
