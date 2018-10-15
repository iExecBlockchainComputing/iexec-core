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
    private String os;
    private String cpu;
    private int cpuNb;
    private List<String> taskIds;

    private Date lastAliveDate;

    public Worker() {
        taskIds = new ArrayList<>();
    }

    void addTaskId(String taskId) {
        taskIds.add(taskId);
    }

    void removeTaskId(String taskId) {
        taskIds.remove(taskId);
    }
}
