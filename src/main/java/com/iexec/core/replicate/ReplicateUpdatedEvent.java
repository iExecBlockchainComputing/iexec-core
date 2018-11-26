package com.iexec.core.replicate;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@NoArgsConstructor
@Getter
@Setter
public class ReplicateUpdatedEvent {

    private Replicate replicate;

    public ReplicateUpdatedEvent(Replicate replicate) {
        this.replicate = replicate;
    }

}
