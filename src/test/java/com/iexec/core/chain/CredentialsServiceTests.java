package com.iexec.core.chain;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.web3j.crypto.CipherException;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.WalletUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.assertj.core.api.Java6Assertions.assertThat;

public class CredentialsServiceTests {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldGetCorrectCredentials() throws Exception {
        String walletPass = "random-pass";
        String walletPath = createTempWallet(walletPass);
        WalletDetails walletDetails = new WalletDetails(walletPath, walletPass);

        CredentialsService credentialsService = new CredentialsService(walletDetails);
        Credentials claimedCredentials = credentialsService.getCredentials();
        Credentials correctCredentials = WalletUtils.loadCredentials(walletPass, walletPath);

        assertThat(claimedCredentials).isEqualTo(correctCredentials);
    }

    @Test(expected=IOException.class)
    public void shouldThrowIOExceptionSinceWalletFileNotFind() throws Exception {
        String walletPass = "random-pass";
        String walletPath = createTempWallet(walletPass);
        WalletDetails walletDetails = new WalletDetails(walletPath, walletPass);

        (new File(walletPath)).delete();

        new CredentialsService(walletDetails);
    }

    @Test(expected=IOException.class)
    public void shouldThrowIOExceptionSinceCorruptedWalletFile() throws Exception {
        String walletPass = "random-pass";
        String walletPath = createTempWallet(walletPass);
        WalletDetails walletDetails = new WalletDetails(walletPath, walletPass);

        FileWriter fw = new FileWriter(walletPath);
        fw.write("{new: devilish corrupted content}");
        fw.close();

        new CredentialsService(walletDetails);
    }

    @Test(expected=CipherException.class)
    public void shouldThrowCipherExceptionSinceWrongPassword() throws Exception {
        String walletPass = "random-pass";
        String wrongPass = "wrong-pass";
        String walletPath = createTempWallet(walletPass);
        WalletDetails walletDetails = new WalletDetails(walletPath, wrongPass);
 
        new CredentialsService(walletDetails);
    }

    String createTempWallet(String password) throws Exception {
        File tempDir = temporaryFolder.newFolder("temp-dir");
        String walletFilename = WalletUtils.generateFullNewWalletFile(password, tempDir);
        return tempDir + "/" + walletFilename;
    }
}