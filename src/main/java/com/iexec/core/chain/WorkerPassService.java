package com.iexec.core.chain;

import com.iexec.common.chain.IexecHubAbstractService;
import com.iexec.core.contract.generated.IExecTokenABI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.ens.EnsResolutionException;
import org.web3j.tx.ChainIdLong;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.gas.ContractGasProvider;
import org.web3j.tx.gas.DefaultGasProvider;

import java.math.BigInteger;
import java.util.Optional;

import static org.web3j.protocol.core.JsonRpc2_0Web3j.DEFAULT_BLOCK_TIME;
import static org.web3j.tx.TransactionManager.DEFAULT_POLLING_ATTEMPTS_PER_TX_HASH;

@Slf4j
@Service
public class WorkerPassService extends IexecHubAbstractService {

    private final String workerPassAddress;
    private final Web3jService web3jService;
    private final CredentialsService credentialsService;

    @Autowired
    public WorkerPassService(CredentialsService credentialsService,
                           Web3jService web3jService,
                           ChainConfig chainConfig) {
        super(
                credentialsService.getCredentials(),
                web3jService,
                chainConfig.getHubAddress());
        this.credentialsService = credentialsService;
        this.web3jService = web3jService;
        this.workerPassAddress = chainConfig.getWorkerPassAddress();
        if (!hasEnoughGas(credentialsService.getCredentials().getAddress())) {
            System.exit(0);
        }
    }

    public IExecTokenABI getWorkerPassContract(ContractGasProvider contractGasProvider,
                                        long chainId,
                                        int watchFrequency,
                                        int watchAttempts) {
        ExceptionInInitializerError exceptionInInitializerError =
                new ExceptionInInitializerError("Failed to load WorkerPass " +
                        "contract from address " + workerPassAddress);

        if (workerPassAddress != null && !workerPassAddress.isEmpty()) {
            try {
                return IExecTokenABI.load(workerPassAddress,
                        web3jService.getWeb3j(),
                        new RawTransactionManager(web3jService.getWeb3j(),
                                credentialsService.getCredentials(),
                                chainId,
                                watchAttempts,
                                watchFrequency),
                        contractGasProvider);
            } catch (EnsResolutionException e) {
                throw exceptionInInitializerError;
            }
        } else {
            throw exceptionInInitializerError;
        }
    }

    /*
     * We wan't a fresh new instance of IexecHubContract on each call in order to get
     * the last ContractGasProvider which depends on the gas price of the network
     */
    public IExecTokenABI getWorkerPassContract(ContractGasProvider contractGasProvider) {
        return getWorkerPassContract(contractGasProvider,
                ChainIdLong.NONE,
                DEFAULT_BLOCK_TIME,
                DEFAULT_POLLING_ATTEMPTS_PER_TX_HASH);
    }

    /*
     * This method should only be used for reading
     */
    public IExecTokenABI getWorkerPassContract() {
        return getWorkerPassContract(new DefaultGasProvider());
    }

    public Optional<Boolean> hasWorkerPass(String address) {
        if (address != null && !address.isEmpty()) {
            try {
                BigInteger balance = getWorkerPassContract().balanceOf(address).send();
                return Optional.of(balance.compareTo(BigInteger.ZERO) > 0);
            } catch (Exception e) {
                log.error("Failed to getWorkerScore [address:{}]", address, e);
            }
        }
        return Optional.empty();
    }
}
