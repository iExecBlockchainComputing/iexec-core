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
    private List<String> chainTaskIds;

    private Date lastAliveDate;

    public Worker() {
        chainTaskIds = new ArrayList<>();
    }

    void addChainTaskId(String chainTaskId) {
        chainTaskIds.add(chainTaskId);
    }

    void removeChainTaskId(String chainTaskId) {
        chainTaskIds.remove(chainTaskId);
    }
}
