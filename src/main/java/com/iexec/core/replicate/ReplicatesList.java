package com.iexec.core.replicate;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;

import java.util.List;

@Data
@NoArgsConstructor
public class ReplicatesList {

    @Id
    private String id;

    @Version
    private Long version;

    private String chainTaskId;
    private List<Replicate> replicates;

    public ReplicatesList(String chainTaskId, List<Replicate> replicates) {
        this.chainTaskId = chainTaskId;
        this.replicates = replicates;
    }
}
