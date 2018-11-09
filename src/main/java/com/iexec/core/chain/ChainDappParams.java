package com.iexec.core.chain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChainDappParams {

    private String type;
    private String provider;
    private String uri;
    private String minmemory;
}
