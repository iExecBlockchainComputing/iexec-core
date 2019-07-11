package com.iexec.core.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import com.iexec.common.replicate.ReplicateStatusCause;
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
    private ReplicateStatus newReplicateStatus;
    private ReplicateStatusCause newReplicateStatusCause;
}
