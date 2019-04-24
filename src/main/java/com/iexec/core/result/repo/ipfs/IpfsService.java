package com.iexec.core.result.repo.ipfs;

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
public class IpfsService {

    private IPFS ipfs;

    public IpfsService(IpfsConfig ipfsConfig) {
        String ipfsHost = ipfsConfig.getHost();
        String ipfsNodeIp = NetworkUtils.isIPAddress(ipfsHost) ? ipfsHost : NetworkUtils.convertHostToIp(ipfsHost);
        String multiAddress = "/ip4/" + ipfsNodeIp + "/tcp/" + ipfsConfig.getPort();
        try {
            ipfs = new IPFS(multiAddress);
        } catch (Exception e) {
            log.error("Exception when inializing IPFS [exception:{}]", e.getMessage());
            log.warn("Shutting down the service since IPFS is necessary");
            System.exit(1);
        }
    }

    public Optional<byte[]> get(String ipfsHash) {
        if (!isIpfsHash(ipfsHash)){
            return Optional.empty();
        }
        Multihash filePointer = Multihash.fromBase58(ipfsHash);
        try {
            return Optional.of(ipfs.cat(filePointer));
        } catch (IOException e) {
            log.error("Error when trying to retrieve ipfs object [hash:{}]", ipfsHash);
        }
        return Optional.empty();
    }

    public String add(String resultFileName, byte[] fileContent) {
        NamedStreamable.ByteArrayWrapper file = new NamedStreamable.ByteArrayWrapper(resultFileName, fileContent);
        try {
            MerkleNode pushedContent = ipfs.add(file, false).get(0);
            return pushedContent.hash.toString();
        } catch (IOException e) {
            log.error("Error when trying to push ipfs object [fileName:{}]");
        }
        return "";
    }

    public static boolean isIpfsHash(String hash) {
        try {
            return Multihash.fromBase58(hash).toBase58() != null;
        } catch (Exception e) {
            return false;
        }
    }

}
