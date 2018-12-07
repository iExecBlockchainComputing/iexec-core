package com.iexec.core.chain;

import com.iexec.common.chain.ChainDeal;
import com.iexec.common.chain.ChainUtils;
import com.iexec.common.contract.generated.App;
import com.iexec.common.contract.generated.IexecClerkABILegacy;
import com.iexec.common.utils.BytesUtils;
import com.iexec.core.configuration.ConfigurationService;
import com.iexec.core.task.Task;
import com.iexec.core.task.TaskService;
import com.iexec.core.task.event.TaskCreatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.DefaultBlockParameterName;
import rx.Subscription;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Optional;

import static com.iexec.common.chain.ChainUtils.getWeb3j;

@Slf4j
@Service
public class IexecWatcherService {

    // outside services
    // TODO: this should be replaced by DealService ?
    private final TaskService taskService;
    // internal variables
    private final IexecClerkABILegacy iexecClerk;
    private final Credentials credentials;
    private final Web3j web3j;
    private Subscription dealEventSubscriptionReplay;
    private ChainConfig chainConfig;
    private ApplicationEventPublisher applicationEventPublisher;
    private ConfigurationService configurationService;

    @Autowired
    public IexecWatcherService(CredentialsService credentialsService,
                               ChainConfig chainConfig,
                               TaskService taskService,
                               ApplicationEventPublisher applicationEventPublisher,
                               ConfigurationService configurationService) {
        this.chainConfig = chainConfig;
        this.taskService = taskService;
        this.credentials = credentialsService.getCredentials();
        this.web3j = getWeb3j(chainConfig.getPrivateChainAddress());
        this.iexecClerk = ChainUtils.loadClerkContract(credentials, web3j, chainConfig.getHubAddress());
        this.applicationEventPublisher = applicationEventPublisher;
        this.configurationService = configurationService;
        subscribeToDealEventFromGivenToLatest(configurationService.getLastSeenBlockWithDeal());
    }

    private Subscription subscribeToDealEventFromGivenToLatest(BigInteger from) {
        return subscribeToDealEventInRange(from, null);
    }

    private Subscription subscribeToDealEventInRange(BigInteger from, BigInteger to) {
        DefaultBlockParameter fromBlock = DefaultBlockParameter.valueOf(from);
        DefaultBlockParameter toBlock = DefaultBlockParameterName.LATEST;
        if (to != null) {
            toBlock = DefaultBlockParameter.valueOf(to);
        }

        log.info("Watcher DealEvent started [from:{}, to:{}]", from, (to == null) ? "latest" : to);
        return iexecClerk.schedulerNoticeEventObservable(fromBlock, toBlock)
                .subscribe(dealEvent -> {
                    if (dealEvent.workerpool.equals(chainConfig.getPoolAddress())) {
                        log.info("Received deal [dealId:{}, block:{}]",
                                BytesUtils.bytesToString(dealEvent.dealid),
                                dealEvent.log.getBlockNumber());
                        this.onDealEvent(BytesUtils.bytesToString(dealEvent.dealid));
                        if (configurationService.getLastSeenBlockWithDeal().intValue() < dealEvent.log.getBlockNumber().intValue()) {
                            configurationService.setLastSeenBlockWithDeal(dealEvent.log.getBlockNumber());
                        }
                    }
                });
    }

    private void onDealEvent(String chainDealId) {
        try {
            Optional<ChainDeal> optionalChainDeal = ChainUtils.getChainDeal(iexecClerk, chainDealId);
            if (!optionalChainDeal.isPresent()) {
                return;
            }
            ChainDeal chainDeal = optionalChainDeal.get();
            App chainApp = ChainUtils.loadDappContract(credentials, web3j, chainDeal.getDappPointer());
            String dockerImage = ChainHelpers.getDockerImage(chainApp);
            ArrayList<String> dealParams = ChainHelpers.getChainDealParams(chainDeal);

            int startBag = chainDeal.getBotFirst().intValue();
            int endBag = chainDeal.getBotFirst().intValue() + chainDeal.getBotSize().intValue();

            for (int taskIndex = startBag; taskIndex < endBag; taskIndex++) {
                Optional<Task> optional = taskService.addTask(chainDealId, taskIndex,
                        dockerImage, dealParams.get(taskIndex), chainDeal.getTrust().intValue());
                optional.ifPresent(task -> applicationEventPublisher.publishEvent(new TaskCreatedEvent(task)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * Some deal events are sometimes missed by #schedulerNoticeEventObservable method
     * so we decide to replay events from times to times (already saved events will be ignored)
     * */
    @Scheduled(fixedRateString = "60000")
    public void replayDealEvent() {
        if (configurationService.getFromReplay().intValue() < configurationService.getLastSeenBlockWithDeal().intValue()) {
            if (dealEventSubscriptionReplay != null) {
                this.dealEventSubscriptionReplay.unsubscribe();
            }
            this.dealEventSubscriptionReplay = subscribeToDealEventInRange(configurationService.getFromReplay(), configurationService.getLastSeenBlockWithDeal());
            configurationService.setFromReplay(configurationService.getLastSeenBlockWithDeal());
        }
    }

}
