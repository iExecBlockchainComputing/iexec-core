package com.iexec.core.result.eip712;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Domain {

    private String name;
    private String version;
    private long chainId;

}
