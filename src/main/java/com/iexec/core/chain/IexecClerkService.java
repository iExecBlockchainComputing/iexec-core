package com.iexec.core.chain;

import com.iexec.common.chain.ChainUtils;
import com.iexec.common.chain.Contribution;
import com.iexec.common.chain.ContributionStatus;
import com.iexec.common.contract.generated.Dapp;
import com.iexec.common.contract.generated.IexecClerkABILegacy;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.common.utils.BytesUtils;
import com.iexec.core.task.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tuples.generated.Tuple6;

import java.math.BigInteger;
import java.util.ArrayList;

import static com.iexec.common.chain.ChainUtils.getWeb3j;

@Slf4j
@Service
public class IexecClerkService {

    // outside services
    // TODO: this should be replaced by DealService ?
    private final TaskService taskService;

    // internal variables
    private final IexecClerkABILegacy iexecClerk;
    private final IexecHubABILegacy iexecHub;
    private final Credentials credentials;
    private final Web3j web3j;

    @Autowired
    public IexecClerkService(CredentialsService credentialsService,
                             ChainConfig chainConfig,
                             TaskService taskService) {
        this.taskService = taskService;

        this.credentials = credentialsService.getCredentials();
        this.web3j = getWeb3j(chainConfig.getPrivateChainAddress());
        this.iexecClerk = ChainUtils.loadClerkContract(credentials, web3j, chainConfig.getHubAddress());
        this.iexecHub = ChainUtils.loadHubContract(credentials, web3j, chainConfig.getHubAddress());

        startWatchers();
    }


    private void startWatchers() {
        iexecClerk.ordersMatchedEventObservable(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST)
                .subscribe(this::onOrderMatchedEvents);
    }

    private void onOrderMatchedEvents(IexecClerkABILegacy.OrdersMatchedEventResponse ordersMatchedEvent) {
        try {
            ChainDeal chainDeal = ChainHelpers.getChainDeal(iexecClerk, ordersMatchedEvent.dealid);

            Dapp dapp = ChainUtils.loadDappContract(credentials, web3j, chainDeal.dappPointer);
            log.info("Received an order match, trigger a computation [dappName:{}]", ChainHelpers.getDappName(dapp));

            String dockerImage = ChainHelpers.getDockerImage(dapp);
            ArrayList<String> dealParams = ChainHelpers.getChainDealParams(chainDeal);

            // get range of tasks in the deal
            int start = chainDeal.botFirst.intValue();
            int end = chainDeal.botFirst.intValue() + chainDeal.botSize.intValue();

            // initialize all tasks in the deal
            for (int iter = start; iter < end; iter++) {
                initializeTask(ordersMatchedEvent.dealid, iter, dealParams.get(iter), dockerImage);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initializeTask(byte[] dealId, int numTask, String param, String dockerImage) throws Exception {
        TransactionReceipt res = iexecHub.initialize(dealId, BigInteger.valueOf(numTask)).send();
        if (!iexecHub.getTaskInitializeEvents(res).isEmpty()) {
            String chainTaskId = BytesUtils.bytesToString(iexecHub.getTaskInitializeEvents(res).get(0).taskid);
            // TODO: contribution is hard coded for now
            taskService.addTask(dockerImage, param, 1, chainTaskId);
        }
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


}
