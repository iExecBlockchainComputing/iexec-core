package com.iexec.core.result.repo.proxy;

import com.iexec.common.chain.ChainContributionStatus;
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


    boolean canUploadResult(String chainTaskId, String walletAddress, byte[] zip) {
        // check if result has been already uploaded
        if (doesResultExist(chainTaskId)) {
            log.error("Trying to upload result that has been already uploaded [chainTaskId:{}, uploadRequester:{}]",
                    chainTaskId, walletAddress);
            return false;
        }

        // ContributionStatus of chainTask should be REVEALED
        boolean isChainContributionStatusSetToRevealed = iexecHubService.doesWishedStatusMatchesOnChainStatus(chainTaskId,
                walletAddress, ChainContributionStatus.REVEALED);
        if (!isChainContributionStatusSetToRevealed) {
            log.error("Trying to upload result even though ChainContributionStatus is not REVEALED [chainTaskId:{}, uploadRequester:{}]",
                    chainTaskId, walletAddress);
            return false;
        }

        return true;
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
        Optional<String> beneficiary = iexecHubService.getTaskBeneficiary(chainTaskId, chainId);
        if (!beneficiary.isPresent()) {
            log.error("Failed to get beneficiary for isOwnerOfResult() method [chainTaskId:{}, downloaderAddress:{}]",
                    chainTaskId, downloaderAddress);
            return false;
        }
        downloaderAddress = downloaderAddress.toLowerCase();
        if (!downloaderAddress.equals(beneficiary.get())) {
            log.error("Set beneficiary doesn't match downloaderAddress [chainTaskId:{}, downloaderAddress:{}, " +
                    "beneficiary:{}]", chainTaskId, downloaderAddress, beneficiary.get());
            return false;
        }
        return true;
    }
}
