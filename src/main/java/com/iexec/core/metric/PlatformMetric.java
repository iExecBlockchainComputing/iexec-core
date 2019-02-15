package com.iexec.core.metric;

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
public class PlatformMetric {

    private int aliveWorkers;
    private int aliveTotalCpu;
    private int aliveAvailableCpu;
    private int completedTasks;

}
