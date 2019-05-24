package com.iexec.core.task.event;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ConsensusReachedEvent {

    private String chainTaskId;
    private String consensus;
    private long blockNumber;
}
