package com.iexec.core.chain;

import com.iexec.common.chain.ChainUtils;
import com.iexec.common.contract.generated.Dapp;
import com.iexec.common.contract.generated.IexecClerkABILegacy;
import com.iexec.common.contract.generated.IexecHubABILegacy;
import com.iexec.core.task.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;

import static com.iexec.common.chain.ChainUtils.getWeb3j;

@Slf4j
@Service
public class IexecHubService {

    // outside services
    // TODO: this should be replaced by DealService ?
    private final TaskService taskService;

    // internal variables
    private final IexecHubABILegacy iexecHub;
    private final IexecClerkABILegacy iexecClerk;
    private final Credentials credentials;
    private final Web3j web3j;

    @Autowired
    public IexecHubService(CredentialsService credentialsService,
                           ChainConfig chainConfig,
                           TaskService taskService) {
        this.taskService = taskService;

        this.credentials = credentialsService.getCredentials();
        this.web3j = getWeb3j(chainConfig.getPrivateChainAddress());
        this.iexecHub = ChainUtils.loadHubContract(credentials, web3j, chainConfig.getHubAddress());
        this.iexecClerk = ChainUtils.loadClerkContract(credentials, web3j, chainConfig.getHubAddress());

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
            String dappName = dapp.m_dappName().send();

            log.info("Received an order match, trigger a computation [dappName:{}]", dappName);

            // TODO: hard coded values for now
            taskService.addTask("iexechub/vanityeth:latest", "ace", 1);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
