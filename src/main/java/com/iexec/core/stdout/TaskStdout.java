package com.iexec.core.stdout;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStdout {

    @Id
    @JsonIgnore
    private String id;

    @Version
    @JsonIgnore
    private Long version;

    @Indexed(unique = true)
    private String chainTaskId;

    private List<ReplicateStdout> replicateStdoutList;

    public TaskStdout(String chainTaskId) {
        this.chainTaskId = chainTaskId;
        this.replicateStdoutList = new ArrayList<>();
    }

    public TaskStdout(String chainTaskId, List<ReplicateStdout> replicateStdoutList) {
        this.chainTaskId = chainTaskId;
        this.replicateStdoutList = replicateStdoutList;
    }

    public boolean containsWalletAddress(String walletAddress) {
        return replicateStdoutList.stream().anyMatch(
            replicateStdout -> replicateStdout.getWalletAddress().equals(walletAddress)
        );
    }
}
