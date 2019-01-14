package com.iexec.core.result.eip712;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Domain {

    public String name;
    public String version;
    public Long chainId;

}
