package com.iexec.core.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Result {

    private String taskId;
    private String image;
    private String cmd;
    private String stdout;
    private byte[] zip;

}
