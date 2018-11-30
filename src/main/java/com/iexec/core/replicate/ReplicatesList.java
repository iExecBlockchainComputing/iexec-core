package com.iexec.core.replicate;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;

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

    @Indexed(unique = true)
    private String chainTaskId;

    private List<Replicate> replicates;

    ReplicatesList(String chainTaskId) {
        this.chainTaskId = chainTaskId;
        this.replicates = new ArrayList<>();
    }

    ReplicatesList(String chainTaskId, List<Replicate> replicates) {
        this.chainTaskId = chainTaskId;
        this.replicates = replicates;
    }

    Optional<Replicate> getReplicateOfWorker(String workerWalletAddress) {
        for (Replicate replicate: replicates){
            if (replicate.getWalletAddress().equals(workerWalletAddress)){
                return Optional.of(replicate);
            }
        }
        return Optional.empty();
    }
}
