package com.iexec.core.replicate;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
@NoArgsConstructor
public class ReplicatesList {

    @Id
    private String id;

    @Version
    private Long version;

    private String chainTaskId;
    private List<Replicate> replicates;

    public ReplicatesList(String chainTaskId) {
        this.chainTaskId = chainTaskId;
        this.replicates = new ArrayList<>();
    }

    public ReplicatesList(String chainTaskId, List<Replicate> replicates) {
        this.chainTaskId = chainTaskId;
        this.replicates = replicates;
    }

    public Optional<Replicate> getReplicateOfWorker(String workerWalletAddress) {
        for (Replicate replicate: replicates){
            if (replicate.getWalletAddress().equals(workerWalletAddress)){
                return Optional.of(replicate);
            }
        }
        return Optional.empty();
    }
}
