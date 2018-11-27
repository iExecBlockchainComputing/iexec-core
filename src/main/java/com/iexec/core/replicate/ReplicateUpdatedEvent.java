package com.iexec.core.replicate;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ReplicateUpdatedEvent {

    private Replicate replicate;
}
