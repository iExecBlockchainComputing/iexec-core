package com.iexec.core.result;

import com.iexec.common.chain.ChainContributionStatus;
import com.iexec.common.chain.ChainDeal;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.utils.BytesUtils;
import com.iexec.core.chain.IexecHubService;

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
public class ResultService {

    private static final String RESULT_FILENAME_PREFIX = "iexec-result-";

    private GridFsOperations gridOperations;
    private IexecHubService iexecHubService;


    public ResultService(GridFsOperations gridOperations,
                         IexecHubService iexecHubService) {
        this.gridOperations = gridOperations;
        this.iexecHubService = iexecHubService;
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
        boolean isChainContributionStatusSetToRevealed = iexecHubService.checkContributionStatus(chainTaskId,
                walletAddress, ChainContributionStatus.REVEALED);
        if (!isChainContributionStatusSetToRevealed) {
            log.error("Trying to upload result even though ChainContributionStatus is not REVEALED [chainTaskId:{}, uploadRequester:{}]",
                    chainTaskId, walletAddress);
            return false;
        }

        return true;
    }

    boolean isResultInDatabase(String chainTaskId) {
        Query query = Query.query(Criteria.where("filename").is(getResultFilename(chainTaskId)));
        return gridOperations.findOne(query) != null;
    }

    String addResult(Result result, byte[] data) {
        if (result == null || result.getChainTaskId() == null) {
            return "";
        }

        InputStream inputStream = new ByteArrayInputStream(data);
        String resultFileName = getResultFilename(result.getChainTaskId());
        gridOperations.store(inputStream, resultFileName, result);
        return resultFileName;
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

    boolean canGetResult(Integer chainId, String chainTaskId, String walletAddress) {
        /*
         * TODO 1:  Use an iexecHubService loaded with ResultRepo credentials
         * TODO 2:  Make possible to call this iexecHubService with a 'chainId' at runtime
         */
        //THREE: check if requester (or beneficiary if set) equals address provided
        Optional<ChainTask> chainTask = iexecHubService.getChainTask(chainTaskId);

        if (!chainTask.isPresent()) {
            log.error("Failed to get ChainTask [chainTaskId:{}, downloadRequester:{}]", chainTaskId, walletAddress);
            return false;
        }

        Optional<ChainDeal> chainDeal = iexecHubService.getChainDeal(chainTask.get().getDealid());
        if (!chainDeal.isPresent()) {
            log.error("Failed to get ChainDeal [chainTaskId:{}, downloadRequester:{}]", chainTaskId, walletAddress);
            return false;
        }

        String requester = chainDeal.get().getRequester();
        String beneficiary = chainDeal.get().getBeneficiary();

        if (!beneficiary.equals(BytesUtils.EMPTY_ADDRESS) && !walletAddress.equalsIgnoreCase(beneficiary)) {
            log.error("Set beneficiary doesn't match downloadRequester [chainTaskId:{}, downloadRequester:{}," +
                            "requester:{}, beneficiary:{}]",
                    chainTaskId, walletAddress, requester, beneficiary);
            return false;
        }

        if (!walletAddress.equalsIgnoreCase(requester)) {
            log.error("Set requester doesn't match downloadRequester [chainTaskId:{}, downloadRequester:{}," +
                            "requester:{}, beneficiary:{}]",
                    chainTaskId, walletAddress, requester, beneficiary);
            return false;
        }

        return true;
    }

}
