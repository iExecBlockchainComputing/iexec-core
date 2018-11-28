package com.iexec.core.chain;

import com.iexec.common.chain.ChainUtils;
import com.iexec.common.contract.generated.App;
import com.iexec.common.contract.generated.IexecClerkABILegacy;
import com.iexec.common.utils.BytesUtils;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskCreatedEvent;
import com.iexec.core.task.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
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
    private final IexecHubService iexecHubService;
    private ApplicationEventPublisher applicationEventPublisher;
    // internal variables
    private final IexecClerkABILegacy iexecClerk;
    private final Credentials credentials;
    private final Web3j web3j;

    @Autowired
    public IexecWatcherService(CredentialsService credentialsService,
                               ChainConfig chainConfig,
                               TaskService taskService,
                               IexecHubService iexecHubService,
                               ApplicationEventPublisher applicationEventPublisher) {
        this.taskService = taskService;

        this.credentials = credentialsService.getCredentials();
        this.web3j = getWeb3j(chainConfig.getPrivateChainAddress());
        this.iexecClerk = ChainUtils.loadClerkContract(credentials, web3j, chainConfig.getHubAddress());
        this.iexecHubService = iexecHubService;
        this.applicationEventPublisher = applicationEventPublisher;

        startWatchers();
    }


    private void startWatchers() {
        iexecClerk.ordersMatchedEventObservable(DefaultBlockParameterName.EARLIEST, DefaultBlockParameterName.LATEST)
                .subscribe(this::onOrderMatchedEvents);
    }

    private void onOrderMatchedEvents(IexecClerkABILegacy.OrdersMatchedEventResponse ordersMatchedEvent) {
        try {
            ChainDeal chainDeal = ChainHelpers.getChainDeal(iexecClerk, ordersMatchedEvent.dealid);
            App chainApp = ChainUtils.loadDappContract(credentials, web3j, chainDeal.dappPointer);
            log.info("Received an order match, trigger a computation [m_dappParams:{}]", chainApp.m_appParams().send());

            String dockerImage = ChainHelpers.getDockerImage(chainApp);
            ArrayList<String> dealParams = ChainHelpers.getChainDealParams(chainDeal);

            int startBag = chainDeal.botFirst.intValue();
            int endBag = chainDeal.botFirst.intValue() + chainDeal.botSize.intValue();

            for (int taskIndex = startBag; taskIndex < endBag; taskIndex++) {
                Task task = taskService.addTask(BytesUtils.bytesToString(ordersMatchedEvent.dealid), taskIndex,
                        dockerImage, dealParams.get(taskIndex), chainDeal.trust.intValue());
                applicationEventPublisher.publishEvent(new TaskCreatedEvent(task));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
