package com.iexec.core.result.ipfs;

import com.iexec.core.utils.NetworkUtils;
import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multihash.Multihash;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

@Service
@Slf4j
public class IPFSService {

    private IPFS ipfs;

    public IPFSService(IPFSConfig ipfsConfig) {
        String ipfsHost = ipfsConfig.getHost();
        String ipfsNodeIp = NetworkUtils.isIPAddress(ipfsHost) ? ipfsHost : NetworkUtils.convertHostToIp(ipfsHost);
        String multiAddress = "/ip4/" + ipfsNodeIp + "/tcp/" + ipfsConfig.getPort();
        try{
            ipfs = new IPFS(multiAddress);
        } catch (Exception e) {
            log.error("Exception when inializing IPFS [exception:{}]", e.getMessage());
            log.warn("Shutting down the service since IPFS is necessary");
            System.exit(1);
        }
    }

    public Optional<byte[]> getContent(String ipfsHash) {
        Multihash filePointer = Multihash.fromBase58(ipfsHash);
        try {
            return Optional.of(ipfs.cat(filePointer));
        } catch (IOException e) {
            log.error("Error when trying to retrieve ipfs object [hash:{}]", ipfsHash);
        }
        return Optional.empty();
    }

    public boolean doesContentExist(String ipfsHash) {
        return getContent(ipfsHash).isPresent();
    }

    public String putContent(String chainTaskId, byte[] fileContent) {
        NamedStreamable.ByteArrayWrapper file = new NamedStreamable.ByteArrayWrapper(fileContent);
        try {
            MerkleNode pushedContent = ipfs.add(file, false).get(0);
            return pushedContent.hash.toString();
        } catch (IOException e) {
            log.error("Error when trying to push ipfs object [fileName:{}]");
        }

        return "";
    }
}
