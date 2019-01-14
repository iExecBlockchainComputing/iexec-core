package com.iexec.core.result.eip712;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Eip712Challenge {

    public Domain domain;
    public String primaryType;
    public Message message;

}
