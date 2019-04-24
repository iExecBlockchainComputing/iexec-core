package com.iexec.core.result;

import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.configuration.ResultRepositoryConfiguration;
import com.iexec.core.result.ipfs.IPFSService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Service
@Slf4j
/**
 * Service class to manage all the results. If the result is public, it will be stored on IPFS. If there is a dedicated
 * beneficiary, the result will be pushed to mongo.
 */
public class ResultService {

    private static final String RESULT_FILENAME_PREFIX = "iexec-result-";
    private static final String IPFS_ADDRESS_PREFIX = "/ipfs/";

    private IexecHubService iexecHubService;
    private IPFSService ipfsService;
    private GridFsOperations gridOperations;
    private ResultRepositoryConfiguration resultRepositoryConfig;

    public ResultService(IexecHubService iexecHubService,
                         IPFSService ipfsService,
                         GridFsOperations gridOperations,
                         ResultRepositoryConfiguration resultRepositoryConfig) {
        this.iexecHubService = iexecHubService;
        this.ipfsService = ipfsService;
        this.gridOperations = gridOperations;
        this.resultRepositoryConfig = resultRepositoryConfig;
    }

    static String getResultFilename(String chainTaskId) {
        return RESULT_FILENAME_PREFIX + chainTaskId;
    }

    boolean canUploadResult(String chainTaskId, String walletAddress, byte[] zip) {
        // check if result has been already uploaded
        if (isResultInDatabase(chainTaskId)) {
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

    boolean isResultInDatabase(String chainTaskId) {
        if(isPublicResult(chainTaskId)){
            return isResultInIpfs(chainTaskId);
        }
        return isResultInMongo(chainTaskId);
    }

    boolean isResultInIpfs (String chainTaskId) {
        return false;
    }

    boolean isResultInMongo(String chainTaskId) {

        Query query = Query.query(Criteria.where("filename").is(getResultFilename(chainTaskId)));
        return gridOperations.findOne(query) != null;
    }

    String addResult(Result result, byte[] data) {
        if (result == null || result.getChainTaskId() == null) {
            return "";
        }

        if (iexecHubService.isPublicResult(result.getChainTaskId(), 0)) {
            return addResultToIPFS(result, data);
        } else {
            return addResultToMongo(result, data);
        }
    }

    private String addResultToMongo(Result result, byte[] data) {
        InputStream inputStream = new ByteArrayInputStream(data);
        String resultFileName = getResultFilename(result.getChainTaskId());
        gridOperations.store(inputStream, resultFileName, result);
        return resultRepositoryConfig.getResultRepositoryURL() + "/results/" + result.getChainTaskId();
    }

    private String addResultToIPFS(Result result, byte[] data) {
        return IPFS_ADDRESS_PREFIX + ipfsService.putContent(result.getChainTaskId(), data);
    }

    public boolean isPublicResult(String chainTaskId) {
        return iexecHubService.isPublicResult(chainTaskId, 0);
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

    public void removeResult(String chainTaskId){
        if (isResultInDatabase(chainTaskId)) {
            gridOperations.delete(new Query(Criteria.where("filename").is(RESULT_FILENAME_PREFIX + chainTaskId)));
        }
    }
}
