package com.iexec.core.result;

import com.iexec.common.chain.ChainDeal;
import com.iexec.common.chain.ChainTask;
import com.iexec.common.security.Signature;
import com.iexec.common.utils.BytesUtils;
import com.iexec.common.utils.SignatureUtils;
import com.iexec.core.chain.IexecHubService;
import lombok.extern.slf4j.Slf4j;
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
    private Eip712ChallengeService eip712ChallengeService;
    private IexecHubService iexecHubService;


    public ResultService(GridFsOperations gridOperations,
                         Eip712ChallengeService eip712ChallengeService,
                         IexecHubService iexecHubService) {
        this.gridOperations = gridOperations;
        this.eip712ChallengeService = eip712ChallengeService;
        this.iexecHubService = iexecHubService;
    }

    static String getResultFilename(String chainTaskId) {
        return RESULT_FILENAME_PREFIX + chainTaskId;
    }

    String addResult(Result result, byte[] data) {
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

    boolean isAuthorizedToGetResult(String chainTaskId, String eip712ChallengeString, Signature signature) {
        //ONE: check if eip712Challenge is in eip712Challenge map
        if (!eip712ChallengeService.containsEip712ChallengeString(eip712ChallengeString)){
            log.error("Eip712ChallengeString provided doesn't match any challenge [chainTaskId:{}, downloadRequester:{}]", chainTaskId, signature.getWorkerWallet());
            return false;
        }

        //TWO: check if ecrecover on eip712Challenge & signature match address
        if (!SignatureUtils.doesSignatureMatchesAddress(signature.getSignR(), signature.getSignS(),
                eip712ChallengeString, signature.getWorkerWallet())) {
            log.error("Signature provided doesn't match walletAddress [chainTaskId:{}, " +
                            "downloadRequester:{}, sign.r:{}, sign.s:{}, eip712ChallengeString:{}]",
                    chainTaskId, signature.getWorkerWallet(), signature.getSignR(), signature.getSignS(), eip712ChallengeString);
            return false;
        }

        //THREE: check if requester (or beneficiary if set) equals address provided
        //TODO: We're just reading data but we shouldn't use iExecHub with scheduler credentials (+ its a scheduler service)
        Optional<ChainTask> chainTask = iexecHubService.getChainTask(chainTaskId);

        if (!chainTask.isPresent()){
            log.error("Failed to get ChainTask [chainTaskId:{}, downloadRequester:{}]", chainTaskId, signature.getWorkerWallet());
            return false;
        }

        Optional<ChainDeal> chainDeal = iexecHubService.getChainDeal(chainTask.get().getDealid());
        if (!chainDeal.isPresent()){
            log.error("Failed to get ChainDeal [chainTaskId:{}, downloadRequester:{}]", chainTaskId, signature.getWorkerWallet());
            return false;
        }

        String requester = chainDeal.get().getRequester();
        String beneficiary = chainDeal.get().getBeneficiary();

        if (!beneficiary.equals(BytesUtils.EMPTY_ADDRESS) && !signature.getWorkerWallet().equalsIgnoreCase(beneficiary)) {
            log.error("Set beneficiary doesn't match downloadRequester [chainTaskId:{}, downloadRequester:{}," +
                            "requester:{}, beneficiary:{}]",
                    chainTaskId, signature.getWorkerWallet(), requester, beneficiary);
            return false;
        }

        if (!signature.getWorkerWallet().equalsIgnoreCase(requester)) {
            log.error("Set requester doesn't match downloadRequester [chainTaskId:{}, downloadRequester:{}," +
                            "requester:{}, beneficiary:{}]",
                    chainTaskId, signature.getWorkerWallet(), requester, beneficiary);
            return false;
        }

        return true;
    }




}
