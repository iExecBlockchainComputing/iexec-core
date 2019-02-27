package com.iexec.core.result;

import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multihash.Multihash;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

@Service
@Slf4j
public class IPFSService {

    @Value("${ipfs.host}")
    private String host;

    @Value("${ipfs.port}")
    private String port;

    private IPFS ipfs;

    public IPFSService() {
        String multiAddress = "/ip4/" + host + "/tcp/" + port;
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
