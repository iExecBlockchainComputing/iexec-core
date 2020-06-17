package com.iexec.core.task.stdout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplicateStdout {
    
    private String walletAddress;
    private String stdout;
}