package com.iexec.core.task.event;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class PleaseUploadEvent {
    private String chainTaskId;
    private String workerWallet;
}
