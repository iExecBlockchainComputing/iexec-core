package com.iexec.core.replicate;

import com.iexec.common.replicate.ReplicateStatusUpdate;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ReplicateUpdatedEvent {

    private String chainTaskId;
    private String walletAddress;
    private ReplicateStatusUpdate replicateStatusUpdate;
}
