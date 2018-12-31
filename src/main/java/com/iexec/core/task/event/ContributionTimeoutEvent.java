package com.iexec.core.task.event;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class ContributionTimeoutEvent {

    private String chainTaskId;
}
