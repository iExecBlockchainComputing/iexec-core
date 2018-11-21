package com.iexec.core.chain;

import com.iexec.common.chain.ChainUtils;
import com.iexec.common.contract.generated.Dapp;
import com.iexec.common.contract.generated.IexecClerkABILegacy;
import com.iexec.core.task.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;

import java.util.ArrayList;

import static com.iexec.common.chain.ChainUtils.getWeb3j;

@Slf4j
@Service
public class IexecWatcherService {

    // outside services
    // TODO: this should be replaced by DealService ?
    private final TaskService taskService;
    private final IexecClerkService iexecClerkService;

    // internal variables
    private final IexecClerkABILegacy iexecClerk;
    private final Credentials credentials;
    private final Web3j web3j;

    @Autowired
    public IexecWatcherService(CredentialsService credentialsService,
                               ChainConfig chainConfig,
                               TaskService taskService,
                               IexecClerkService iexecClerkService) {
        this.taskService = taskService;

        this.credentials = credentialsService.getCredentials();
        this.web3j = getWeb3j(chainConfig.getPrivateChainAddress());
        this.iexecClerk = ChainUtils.loadClerkContract(credentials, web3j, chainConfig.getHubAddress());
        this.iexecClerkService = iexecClerkService;

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
                String chainTaskId = iexecClerkService.initializeTask(ordersMatchedEvent.dealid, iter);
                if (chainTaskId != null && !chainTaskId.isEmpty()){
                    // TODO: contribution  is hard coded for now
                    // TODO: hardcoded trust
                    taskService.addTask(dockerImage, dealParams.get(iter), 2, chainTaskId);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
