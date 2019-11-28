package com.iexec.core.metric;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class PlatformMetric {

    private int aliveWorkers;
    private int aliveTotalCpu;
    private int aliveAvailableCpu;
    private int aliveTotalGpu;
    private int aliveAvailableGpu;
    private int completedTasks;

}
