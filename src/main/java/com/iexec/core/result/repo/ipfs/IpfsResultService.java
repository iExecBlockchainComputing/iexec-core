package com.iexec.core.result.repo.ipfs;

import com.iexec.common.utils.BytesUtils;
import com.iexec.core.chain.IexecHubService;
import com.iexec.core.result.repo.proxy.Result;
import com.iexec.core.result.repo.proxy.ResultRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
public class IpfsResultService extends ResultRepo {


    private static final String IPFS_ADDRESS_PREFIX = "/ipfs/";

    private IexecHubService iexecHubService;
    private IpfsService ipfsService;

    public IpfsResultService(IexecHubService iexecHubService,
                             IpfsService ipfsService) {
        this.iexecHubService = iexecHubService;
        this.ipfsService = ipfsService;
    }

    @Override
    public String addResult(Result result, byte[] data) {
        String resultFileName = getResultFilename(result.getChainTaskId());
        return IPFS_ADDRESS_PREFIX + ipfsService.add(resultFileName, data);
    }

    @Override
    public Optional<byte[]> getResult(String chainTaskId) {
        String ipfsHash = getIpfsHashFromChainTaskId(chainTaskId);
        if (!ipfsHash.isEmpty()) {
            return ipfsService.get(ipfsHash);
        }
        return Optional.empty();
    }

    String getIpfsHashFromChainTaskId(String chainTaskId) {
        String ipfsHash = "";
        String taskResultsHexString = iexecHubService.getTaskResults(chainTaskId, 0);
        if (!BytesUtils.isHexaString(taskResultsHexString)) {
            return "";
        }
        String resultLink = BytesUtils.hexStringToAscii(taskResultsHexString);
        if (resultLink.contains(IPFS_ADDRESS_PREFIX)) {
            String[] ipfsLinkParts = resultLink.split("/");
            if (ipfsLinkParts.length > 2) {
                ipfsHash = ipfsLinkParts[2];
            }
        }
        return IpfsService.isIpfsHash(ipfsHash)? ipfsHash: "";
    }
}
