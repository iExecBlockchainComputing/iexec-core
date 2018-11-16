package com.iexec.core.chain;

import com.iexec.common.chain.ChainUtils;
import com.iexec.common.contract.generated.IexecClerkABILegacy;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.common.utils.BytesUtils;
import com.iexec.core.task.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple6;

import java.math.BigInteger;

import static com.iexec.common.chain.ChainUtils.getWeb3j;

@Slf4j
@Service
public class IexecClerkService {

    // internal variables
    private final IexecClerkABILegacy iexecClerk;
    private final IexecHubABILegacy iexecHub;
    private final Credentials credentials;
    private final Web3j web3j;

    @Autowired
    public IexecClerkService(CredentialsService credentialsService,
                             ChainConfig chainConfig) {
        this.credentials = credentialsService.getCredentials();
        this.web3j = getWeb3j(chainConfig.getPrivateChainAddress());
        this.iexecClerk = ChainUtils.loadClerkContract(credentials, web3j, chainConfig.getHubAddress());
        this.iexecHub = ChainUtils.loadHubContract(credentials, web3j, chainConfig.getHubAddress());
    }



    public Contribution getContribution(String chainTaskId, String workerWalletAddress){
        Tuple6<BigInteger, byte[], byte[], String, BigInteger, BigInteger> contributionTuple = null;
        try {
            contributionTuple = iexecHub.viewContributionABILegacy(BytesUtils.stringToBytes(chainTaskId), workerWalletAddress).send();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Contribution.tuple2Contribution(contributionTuple);
    }

    public String initializeTask(byte[] dealId, int numTask) throws Exception {
        TransactionReceipt res = iexecHub.initialize(dealId, BigInteger.valueOf(numTask)).send();
        if (!iexecHub.getTaskInitializeEvents(res).isEmpty()) {
            return BytesUtils.bytesToString(iexecHub.getTaskInitializeEvents(res).get(0).taskid);
        }
        return null;
    }

    public boolean consensus(String chainTaskId, String consensus) throws Exception {
        TransactionReceipt receipt = iexecHub.consensus(BytesUtils.stringToBytes(chainTaskId),
                BytesUtils.stringToBytes(consensus)).send();
        if (!iexecHub.getTaskConsensusEvents(receipt).isEmpty()){
            log.info("Set consensus on-chain succeeded [chainTaskId:{}, consensus:{}]", chainTaskId, consensus);
            //return BytesUtils.bytesToString(iexecHub.getTaskConsensusEvents(receipt).get(0).taskid);
            return true;
        }
        return false;
    }



}
