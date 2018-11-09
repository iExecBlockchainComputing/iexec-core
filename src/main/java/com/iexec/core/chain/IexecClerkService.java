package com.iexec.core.chain;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;

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
            byte[] dealId = ordersMatchedEvent.dealid;
            ChainDeal chainDeal = ChainHelpers.getChainDeal(iexecClerk, ordersMatchedEvent.dealid);

            Dapp dapp = ChainUtils.loadDappContract(credentials, web3j, chainDeal.dappPointer);
            String dappName = dapp.m_dappName().send();
            String jsonDappParams = dapp.m_dappParams().send();

            log.info("Received an order match, trigger a computation [dappName:{}]", dappName);
            // deserialize the dapp params json into POJO
            ChainDappParams dappParams = new ObjectMapper().readValue(jsonDappParams, ChainDappParams.class);
            String dockerImage = dappParams.getUri();

            // get params for all tasks
            LinkedHashMap<String,String> tasksParamsMap = new ObjectMapper().readValue(chainDeal.getParams(), LinkedHashMap.class);
            ArrayList<String> taskParams = new ArrayList<>(tasksParamsMap.values());

            for(int iter = chainDeal.botFirst.intValue(); iter < chainDeal.botFirst.intValue() + chainDeal.botSize.intValue(); iter++){

                TransactionReceipt res = iexecHub.initialize(dealId, BigInteger.valueOf(iter)).send();
                if(!iexecHub.getTaskInitializeEvents(res).isEmpty()){
                    byte[] chainTaskId = iexecHub.getTaskInitializeEvents(res).get(0).taskid;
                    // TODO: contribution is hard coded for now
                    taskService.addTask(chainTaskId, dockerImage, taskParams.get(iter), 1);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
