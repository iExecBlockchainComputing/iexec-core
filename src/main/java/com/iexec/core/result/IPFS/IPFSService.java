package com.iexec.core.result.IPFS;

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
        String multiAddress = "/ip4/" + ipfsConfig.getHost() + "/tcp/" + ipfsConfig.getPort();
        ipfs = new IPFS(multiAddress);
    }

    public Optional<byte[]> getContent(String ipfsHash) {
        Multihash filePointer = Multihash.fromBase58(ipfsHash);
        try {
            return Optional.of(ipfs.get(filePointer));
        } catch (IOException e) {
            log.error("Error when trying to retrieve ipfs object [hash:{}]", ipfsHash);
        }
        return Optional.empty();
    }

    public boolean doesContentExists(String ipfsHash) {
        return getContent(ipfsHash).isPresent();
    }

    public String putContent(String fileName, byte[] fileContent) {
        NamedStreamable.ByteArrayWrapper file = new NamedStreamable.ByteArrayWrapper(fileName, fileContent);
        try {
            MerkleNode pushedContent = ipfs.add(file).get(0);
            return pushedContent.hash.toString();
        } catch (IOException e) {
            log.error("Error when trying to push ipfs object [fileName:{}]", fileName);
        }

        return "";
    }
}
