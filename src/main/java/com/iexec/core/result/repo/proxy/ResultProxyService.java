package com.iexec.core.result.repo.proxy;

import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.chain.ChainTaskStatus;
import com.iexec.common.task.TaskDescription;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.result.repo.ipfs.IpfsResultService;
import com.iexec.core.result.repo.mongo.MongoResultService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

@Service
@Slf4j
/**
 * Service class to manage all the results. If the result is public, it will be stored on IPFS. If there is a dedicated
 * beneficiary, the result will be pushed to mongo.
 */
public class ResultProxyService {

    private final IexecHubService iexecHubService;
    private final MongoResultService mongoResultService;
    private final IpfsResultService ipfsResultService;

    public ResultProxyService(IexecHubService iexecHubService,
                              MongoResultService mongoResultService,
                              IpfsResultService ipfsResultService) {
        this.iexecHubService = iexecHubService;
        this.mongoResultService = mongoResultService;
        this.ipfsResultService = ipfsResultService;
    }


    boolean canUploadResult(String chainTaskId, String tokenWalletAddress, byte[] zip) {
        if (iexecHubService.isTeeTask(chainTaskId)){
            Optional<ChainTask> chainTask = iexecHubService.getChainTask(chainTaskId);//TODO Add requester field to getChainTask
            if (chainTask.isEmpty()){
                log.error("Trying to upload result for TEE but getChainTask failed [chainTaskId:{}, uploader:{}]",
                        chainTaskId, tokenWalletAddress);
                return false;
            }
            boolean isActive = chainTask.get().getStatus().equals(ChainTaskStatus.ACTIVE);

            Optional<TaskDescription> taskDescription = iexecHubService.getTaskDescriptionFromChain(chainTaskId);
            if (taskDescription.isEmpty()){
                log.error("Trying to upload result for TEE but getTaskDescription failed [chainTaskId:{}, uploader:{}]",
                        chainTaskId, tokenWalletAddress);
                return false;
            }
            boolean isRequesterCredentials = taskDescription.get().getRequester().equalsIgnoreCase(tokenWalletAddress);

            return isActive && isRequesterCredentials;
        } else {
            // check if result has been already uploaded
            if (doesResultExist(chainTaskId)) {
                log.error("Trying to upload result that has been already uploaded [chainTaskId:{}, uploadRequester:{}]",
                        chainTaskId, tokenWalletAddress);
                return false;
            }

            // ContributionStatus of chainTask should be REVEALED
            boolean isChainContributionStatusSetToRevealed = iexecHubService.isStatusTrueOnChain(chainTaskId,
                    tokenWalletAddress, ChainContributionStatus.REVEALED);
            if (!isChainContributionStatusSetToRevealed) {
                log.error("Trying to upload result even though ChainContributionStatus is not REVEALED [chainTaskId:{}, uploadRequester:{}]",
                        chainTaskId, tokenWalletAddress);
                return false;
            }

            return true;
        }
    }

    boolean doesResultExist(String chainTaskId) {
        if (isPublicResult(chainTaskId)) {
            return false; //return ipfsResultService.doesResultExist(chainTaskId);
            /*
             * We should probably not check if result exists when IPFS (timeout issues)(?)
             * */
        }
        return mongoResultService.doesResultExist(chainTaskId);
    }


    String addResult(Result result, byte[] data) {
        if (result == null || result.getChainTaskId() == null) {
            return "";
        }

        if (iexecHubService.isTeeTask(result.getChainTaskId())){
            return ipfsResultService.addResult(result, data);
        }

        if (iexecHubService.isPublicResult(result.getChainTaskId(), 0)) {
            return ipfsResultService.addResult(result, data);
        } else {
            return mongoResultService.addResult(result, data);
        }
    }

    public boolean isPublicResult(String chainTaskId) {
        return iexecHubService.isPublicResult(chainTaskId, 0);
    }

    Optional<byte[]> getResult(String chainTaskId) throws IOException {
        if (iexecHubService.isTeeTask(chainTaskId)){
            return ipfsResultService.getResult(chainTaskId);
        }

        if (!isPublicResult(chainTaskId)) {
            return mongoResultService.getResult(chainTaskId);
        }
        return ipfsResultService.getResult(chainTaskId);
    }

    /*
     * TODO 1:  Use an iexecHubService loaded with ResultRepo credentials
     * TODO 2:  Make possible to call this iexecHubService with a 'chainId' at runtime
     */
    boolean isOwnerOfResult(Integer chainId, String chainTaskId, String downloaderAddress) {
        Optional<TaskDescription> oTask = iexecHubService.getTaskDescriptionFromChain(chainTaskId);

        if (oTask.isEmpty()){
            log.error("Failed to getTaskDescriptionFromChain for isOwnerOfResult() method [chainTaskId:{}, downloaderAddress:{}]",
                    chainTaskId, downloaderAddress);
            return false;
        }

        TaskDescription task = oTask.get();

        downloaderAddress = downloaderAddress.toLowerCase();

        if (task.isTeeTask()){//Push TEE result with beneficiary credentials not implemented yet, so we check the requester
            if (downloaderAddress.equalsIgnoreCase(task.getRequester())) {
                return true;
            }
            log.error("Set requester doesn't match downloaderAddress [chainTaskId:{}, downloaderAddress:{}, " +
                    "requester:{}]", chainTaskId, downloaderAddress, task.getRequester());
            return false;
        }

        if (downloaderAddress.equalsIgnoreCase(task.getBeneficiary())) {
            return true;
        }

        log.error("Set beneficiary doesn't match downloaderAddress [chainTaskId:{}, downloaderAddress:{}, " +
                "beneficiary:{}]", chainTaskId, downloaderAddress, task.getBeneficiary());
        return false;
    }
}
