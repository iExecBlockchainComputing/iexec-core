package com.iexec.core.worker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@Builder
public class Worker {

    @Id
    private String id;
    private String name;
    private String walletAddress;
    private String os;
    private String cpu;
    private int cpuNb;
    private int memorySize;
    private List<String> participatingChainTaskIds;
    private List<String> computingChainTaskIds;

    private Date lastAliveDate;

    public Worker() {
        participatingChainTaskIds = new ArrayList<>();
        computingChainTaskIds = new ArrayList<>();
    }

    void addChainTaskId(String chainTaskId) {
        participatingChainTaskIds.add(chainTaskId);
        computingChainTaskIds.add(chainTaskId);
    }

    void removeChainTaskId(String chainTaskId) {
        participatingChainTaskIds.remove(chainTaskId);
        computingChainTaskIds.remove(chainTaskId);
    }

    void removeComputedChainTaskId(String chainTaskId) {
        computingChainTaskIds.remove(chainTaskId);
    }
}
