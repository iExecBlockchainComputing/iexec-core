package com.iexec.core.replicate;

import com.iexec.common.replicate.ReplicateStatus;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ReplicateUpdatedEvent {

    private String chainTaskId;
    private ReplicateStatus newReplicateStatus;
}
