package com.iexec.core.result;

import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.chain.ChainDeal;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.utils.BytesUtils;
import com.iexec.core.chain.IexecHubService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Service
@Slf4j
public class ResultService {

    private static final String RESULT_FILENAME_PREFIX = "iexec-result-";

    private IexecHubService iexecHubService;
    private IPFSService ipfsService;

    public ResultService(IexecHubService iexecHubService,
                         IPFSService ipfsService) {
        this.iexecHubService = iexecHubService;
        this.ipfsService = ipfsService;
    }

    static String getResultFilename(String chainTaskId) {
        return RESULT_FILENAME_PREFIX + chainTaskId;
    }

    boolean canUploadResult(String chainTaskId, String walletAddress, byte[] zip) {
        // check if result has been already uploaded
        //if (isResultInDatabase(chainTaskId)) {
        //    log.error("Trying to upload result that has been already uploaded [chainTaskId:{}, uploadRequester:{}]",
        //            chainTaskId, walletAddress);
        //    return false;
        //}

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

    public boolean isResultInDatabase(String chainTaskId) {
        ipfsService.getContent();

        Query query = Query.query(Criteria.where("filename").is(getResultFilename(chainTaskId)));
        return gridOperations.findOne(query) != null;
    }

    String addResult(Result result, byte[] data) {
        return result == null || result.getChainTaskId() == null ? "" :
                ipfsService.putContent(getResultFilename(result.getChainTaskId()), data);
    }

    byte[] getResultByChainTaskId(String chainTaskId) throws IOException {
        String resultFileName = getResultFilename(chainTaskId);
        GridFsResource[] resources = gridOperations.getResources(resultFileName);
        if (resources.length == 0) {
            return new byte[0];
        }
        InputStream result = resources[0].getInputStream();
        return org.apache.commons.io.IOUtils.toByteArray(result);
    }

    /*
     * TODO 1:  Use an iexecHubService loaded with ResultRepo credentials
     * TODO 2:  Make possible to call this iexecHubService with a 'chainId' at runtime
     */
    boolean isOwnerOfResult(Integer chainId, String chainTaskId, String downloaderAddress) {
        Optional<String> beneficiary = getBeneficiary(chainTaskId, chainId);
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

    boolean isPublicResult(String chainTaskId, Integer chainId) {
        Optional<String> beneficiary = getBeneficiary(chainTaskId, chainId);
        if (!beneficiary.isPresent()) {
            log.error("Failed to get beneficiary for isPublicResult() method [chainTaskId:{}]", chainTaskId);
            return false;
        }
        return beneficiary.get().equals(BytesUtils.EMPTY_ADDRESS);
    }

    private Optional<String> getBeneficiary(String chainTaskId, Integer chainId) {
        Optional<ChainTask> chainTask = iexecHubService.getChainTask(chainTaskId);
        if (!chainTask.isPresent()) {
            return Optional.empty();
        }
        Optional<ChainDeal> optionalChainDeal = iexecHubService.getChainDeal(chainTask.get().getDealid());
        return optionalChainDeal.map(chainDeal -> chainDeal.getBeneficiary().toLowerCase());
    }

}
